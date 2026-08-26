package org.springframework.ai.mcp.sample.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class McpStdioIT {

	@Container
	static final GenericContainer<?> MINIO = MinioTestFixture.createContainer();

	@Test
	void packagedJarServesAllS3ToolsOverStdio() {
		Path jar = Path.of("target", "s3-mcp-server-0.2.0-SNAPSHOT.jar");
		assertThat(jar).isRegularFile();

		String minioEndpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
		ServerParameters parameters = ServerParameters.builder("java")
			.args("-jar", jar.toString(), "--s3.endpoint=" + minioEndpoint, "--s3.region=us-east-1",
					"--s3.access-key=" + MinioTestFixture.ACCESS_KEY,
					"--s3.secret-key=" + MinioTestFixture.SECRET_KEY, "--s3.path-style-access=true")
			.build();

		var transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
		try (var client = McpClient.sync(transport).build()) {
			client.initialize();
			assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
				.containsExactlyInAnyOrder("getBucketList", "getBucketInfo", "listObjects", "getObjectMetadata",
						"generatePresignedUrl", "downloadObject", "uploadObject", "createDirectory");
			CallToolRequest request = CallToolRequest.builder("getBucketList").arguments(Map.of()).build();
			assertThat(client.callTool(request).isError()).isFalse();
		}
	}

}
