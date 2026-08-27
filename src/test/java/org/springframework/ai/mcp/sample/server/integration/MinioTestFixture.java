package org.springframework.ai.mcp.sample.server.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class MinioTestFixture {

	static final String ACCESS_KEY = "minio-access-key";

	static final String SECRET_KEY = "minio-secret-key";

	private MinioTestFixture() {
	}

	static GenericContainer<?> createContainer() {
		return new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
			.withEnv("MINIO_ROOT_USER", ACCESS_KEY)
			.withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
			.withCommand("server /data")
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
	}

}
