package org.springframework.ai.mcp.sample.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.mcp.sample.server.integration.MinioTestFixture.ACCESS_KEY;
import static org.springframework.ai.mcp.sample.server.integration.MinioTestFixture.SECRET_KEY;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.mcp.sample.server.config.S3ClientConfiguration;
import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.entity.S3Bucket;
import org.springframework.ai.mcp.sample.server.security.LocalPathPolicy;
import org.springframework.ai.mcp.sample.server.security.S3AccessPolicy;
import org.springframework.ai.mcp.sample.server.service.S3Service;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Testcontainers
class MinioS3ServiceIT {

	@Container
	static final GenericContainer<?> MINIO = MinioTestFixture.createContainer();

	@TempDir
	static Path localRoot;

	private static S3Client s3;

	private static S3Presigner presigner;

	private static S3Properties properties;

	private String bucket;

	private S3Service service;

	@BeforeAll
	static void createClients() {
		URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
		properties = new S3Properties(endpoint, "us-east-1", ACCESS_KEY, SECRET_KEY, null, true,
				Duration.ofMinutes(15), localRoot, false, List.of(), List.of(), false);
		S3ClientConfiguration configuration = new S3ClientConfiguration();
		AwsCredentialsProvider credentials = configuration.credentialsProvider(properties);
		Region region = configuration.s3Region(properties);
		s3 = configuration.s3Client(properties, region, credentials);
		presigner = configuration.s3Presigner(properties, region, credentials);
	}

	@BeforeEach
	void createBucketAndService() throws Exception {
		this.bucket = "s3-mcp-it-" + UUID.randomUUID();
		s3.createBucket(builder -> builder.bucket(this.bucket));
		this.service = new S3Service(s3, presigner, properties, new LocalPathPolicy(properties),
				new S3AccessPolicy(properties));
	}

	@AfterAll
	static void closeClients() {
		if (presigner != null) {
			presigner.close();
		}
		if (s3 != null) {
			s3.close();
		}
	}

	@Test
	void exercisesAllEightToolsAgainstS3CompatibleStorage() throws Exception {
		byte[] content = new byte[] { 0, 1, 2, 3, 10, 13, 42, 127 };
		Files.write(localRoot.resolve("input.txt"), content);

		assertThat(this.service.getBucketList()).extracting(S3Bucket::name).contains(this.bucket);
		assertThat(this.service.getBucketInfo(this.bucket).name()).isEqualTo(this.bucket);
		assertThat(this.service.uploadObject("docs/input.txt", this.bucket, "input.txt").success()).isTrue();
		assertThat(this.service.listObjects(this.bucket, null, "docs/", "/", 100).objects()).hasSize(1);
		assertThat(this.service.getObjectMetadata("docs/input.txt", this.bucket).contentLength())
			.isEqualTo(content.length);

		String presignedUrl = this.service.generatePresignedUrl("docs/input.txt", this.bucket);
		assertThat(presignedUrl).startsWith("http://");
		HttpResponse<byte[]> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create(presignedUrl)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).containsExactly(content);

		assertThat(this.service.downloadObject("docs/input.txt", this.bucket, "output.txt").success()).isTrue();
		assertThat(Files.readAllBytes(localRoot.resolve("output.txt"))).containsExactly(content);
		assertThat(this.service.createDirectory("docs/folder", this.bucket).key()).isEqualTo("docs/folder/");
	}

}
