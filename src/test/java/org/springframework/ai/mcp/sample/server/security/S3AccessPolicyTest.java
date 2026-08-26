package org.springframework.ai.mcp.sample.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;

class S3AccessPolicyTest {

	@Test
	void filtersBucketsAgainstConfiguredAllowList() {
		S3AccessPolicy access = access(false, List.of("allowed-bucket"), List.of());

		assertThat(access.allowsBucket("allowed-bucket")).isTrue();
		assertThat(access.allowsBucket("other-bucket")).isFalse();
		assertThatThrownBy(() -> access.requireBucket("other-bucket"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("ACCESS_DENIED");
	}

	@Test
	void filtersObjectKeysAgainstConfiguredPrefixes() {
		S3AccessPolicy access = access(false, List.of("allowed-bucket"), List.of("safe/", "public/"));

		assertThatCode(() -> access.requireObject("allowed-bucket", "safe/report.pdf"))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> access.requireObject("allowed-bucket", "private/report.pdf"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("ACCESS_DENIED");
	}

	@Test
	void rejectsMutationsWhenReadOnly() {
		S3AccessPolicy access = access(true, List.of(), List.of());

		assertThatThrownBy(() -> access.requireMutation("uploadObject"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("READ_ONLY");
	}

	@Test
	void permitsMutationsWhenReadOnlyIsDisabled() {
		S3AccessPolicy access = access(false, List.of(), List.of());

		assertThatCode(() -> access.requireMutation("uploadObject")).doesNotThrowAnyException();
	}

	private S3AccessPolicy access(boolean readOnly, List<String> buckets, List<String> prefixes) {
		return new S3AccessPolicy(new S3Properties(null, null, null, null, null, false,
				Duration.ofMinutes(15), Path.of("."), readOnly, buckets, prefixes, false));
	}
}
