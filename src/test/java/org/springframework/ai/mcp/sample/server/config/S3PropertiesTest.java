package org.springframework.ai.mcp.sample.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class S3PropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class);

	@Test
	void bindsSafeDefaults() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(S3Properties.class);
			S3Properties properties = context.getBean(S3Properties.class);
			assertThat(properties.readOnly()).isTrue();
			assertThat(properties.presignDuration()).isEqualTo(Duration.ofMinutes(15));
			assertThat(properties.pathStyleAccess()).isFalse();
			assertThat(properties.allowLocalOverwrite()).isFalse();
			assertThat(properties.allowedPrefixes()).isEmpty();
		});
	}

	@Test
	void rejectsEndpointWithoutRegion() {
		runner.withPropertyValues("s3.endpoint=http://localhost:9000")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsIncompleteExplicitCredentials() {
		runner.withPropertyValues("s3.access-key=test")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsSessionTokenWithoutExplicitCredentials() {
		runner.withPropertyValues("s3.session-token=test-token")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void bindsExplicitSessionCredentialsAndLists() {
		runner.withPropertyValues(
				"s3.access-key=test-access", "s3.secret-key=test-secret",
				"s3.session-token=test-token", "s3.allowed-buckets[0]=docs",
				"s3.allowed-prefixes[0]=safe/", "s3.local-root=build/files")
			.run(context -> {
				S3Properties properties = context.getBean(S3Properties.class);
				assertThat(properties.sessionToken()).isEqualTo("test-token");
				assertThat(properties.allowedBuckets()).containsExactly("docs");
				assertThat(properties.allowedPrefixes()).containsExactly("safe/");
				assertThat(properties.localRoot()).isEqualTo(Path.of("build/files"));
			});
	}

	@Test
	void rejectsBlankIndexedAllowedPrefix() {
		runner.withPropertyValues("s3.allowed-prefixes[0]=safe/", "s3.allowed-prefixes[1]=")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsTrailingBlankCommaSeparatedAllowedPrefix() {
		runner.withPropertyValues("s3.allowed-prefixes=safe/,")
			.run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "PT59S", "P8D" })
	void rejectsPresignDurationOutsideBounds(String duration) {
		runner.withPropertyValues("s3.presign-duration=" + duration)
			.run(context -> assertThat(context).hasFailed());
	}

	@EnableConfigurationProperties(S3Properties.class)
	static class TestConfiguration {
	}
}
