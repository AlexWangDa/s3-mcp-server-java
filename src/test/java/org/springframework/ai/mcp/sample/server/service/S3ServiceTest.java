package org.springframework.ai.mcp.sample.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.sample.server.config.S3Properties;

class S3ServiceTest {

	@Test
	void defersClientCreationWhenUsingDefaultConfiguration() {
		S3Properties properties = properties(null, null, null, null, null, false);

		assertThatCode(() -> new S3Service(properties)).doesNotThrowAnyException();
		S3Service service = new S3Service(properties);
		assertThat(service.credentialsProvider()).isInstanceOf(DefaultAWSCredentialsProviderChain.class);
	}

	@Test
	void configuresSessionCredentialsEndpointRegionAndPathStyleAccess() {
		S3Service service = new S3Service(properties(
				URI.create("http://localhost:9000"), "us-east-1", "access", "secret", "token", true));

		AmazonS3ClientBuilder builder = service.clientBuilder();
		assertThat(builder.getCredentials().getCredentials()).isInstanceOf(BasicSessionCredentials.class);
		assertThat(((BasicSessionCredentials) builder.getCredentials().getCredentials()).getSessionToken()).isEqualTo("token");
		assertThat(builder.getEndpoint().getServiceEndpoint()).isEqualTo("http://localhost:9000");
		assertThat(builder.getEndpoint().getSigningRegion()).isEqualTo("us-east-1");
		assertThat(builder.getClientConfiguration().getProtocol()).isEqualTo(Protocol.HTTPS);
		assertThat(builder.isPathStyleAccessEnabled()).isTrue();
	}

	@Test
	void configuresPathStyleAccessFromProperties() {
		S3Service service = new S3Service(properties(
				URI.create("https://s3.example.test"), "us-east-1", "access", "secret", null, false));

		assertThat(service.clientBuilder().isPathStyleAccessEnabled()).isFalse();
	}

	private S3Properties properties(URI endpoint, String region, String accessKey, String secretKey, String sessionToken,
			boolean pathStyleAccess) {
		return new S3Properties(endpoint, region, accessKey, secretKey, sessionToken, pathStyleAccess,
				Duration.ofMinutes(15), Path.of("."), true, List.of(), List.of(), false);
	}
}
