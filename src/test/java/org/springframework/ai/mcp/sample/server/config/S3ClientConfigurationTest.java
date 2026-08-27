package org.springframework.ai.mcp.sample.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

class S3ClientConfigurationTest {

	private final S3ClientConfiguration configuration = new S3ClientConfiguration();

	@Test
	void usesStaticBasicCredentialsWhenAccessAndSecretKeysAreConfigured() {
		AwsCredentials credentials = configuration.credentialsProvider(properties("test-access", "test-secret", null))
			.resolveCredentials();

		assertThat(credentials.accessKeyId()).isEqualTo("test-access");
		assertThat(credentials.secretAccessKey()).isEqualTo("test-secret");
	}

	@Test
	void usesStaticSessionCredentialsWhenSessionTokenIsConfigured() {
		AwsCredentials credentials = configuration.credentialsProvider(properties("test-access", "test-secret", "test-token"))
			.resolveCredentials();

		assertThat(credentials).isInstanceOf(AwsSessionCredentials.class);
		assertThat(credentials.accessKeyId()).isEqualTo("test-access");
		assertThat(credentials.secretAccessKey()).isEqualTo("test-secret");
		assertThat(((AwsSessionCredentials) credentials).sessionToken()).isEqualTo("test-token");
	}

	@Test
	void usesDefaultCredentialsWhenExplicitCredentialsAreNotConfigured() {
		assertThat(configuration.credentialsProvider(properties(null, null, null)))
			.isInstanceOf(DefaultCredentialsProvider.class);
	}

	@Test
	void usesConfiguredRegion() {
		assertThat(configuration.s3Region(properties(null, null, null))).isEqualTo(Region.US_WEST_2);
	}

	@Test
	void appliesEndpointOverrideAndPathStyleToClientAndPresigner() throws Exception {
		S3Properties properties = properties("test-access", "test-secret", null);
		Region region = configuration.s3Region(properties);
		StaticCredentialsProvider credentials = (StaticCredentialsProvider) configuration.credentialsProvider(properties);

		try (S3Client client = configuration.s3Client(properties, region, credentials);
				S3Presigner presigner = configuration.s3Presigner(properties, region, credentials)) {
			URI clientUrl = client.utilities().getUrl(GetUrlRequest.builder()
				.bucket("bucket").key("object").build()).toURI();
			URI presignedUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(Duration.ofMinutes(5))
				.getObjectRequest(GetObjectRequest.builder().bucket("bucket").key("object").build())
				.build()).url().toURI();

			assertThat(clientUrl).hasHost("localhost").hasPort(9000).hasPath("/bucket/object");
			assertThat(presignedUrl).hasHost("localhost").hasPort(9000).hasPath("/bucket/object");
		}
	}

	private S3Properties properties(String accessKey, String secretKey, String sessionToken) {
		return new S3Properties(URI.create("http://localhost:9000"), "us-west-2", accessKey, secretKey, sessionToken,
				true, Duration.ofMinutes(15), Path.of("."), true, List.of(), List.of(), false);
	}
}
