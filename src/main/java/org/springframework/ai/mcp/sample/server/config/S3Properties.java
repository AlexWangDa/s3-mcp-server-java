package org.springframework.ai.mcp.sample.server.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("s3")
public record S3Properties(
		URI endpoint,
		String region,
		String accessKey,
		String secretKey,
		String sessionToken,
		@DefaultValue("false") boolean pathStyleAccess,
		@DefaultValue("PT15M") Duration presignDuration,
		@DefaultValue(".") Path localRoot,
		@DefaultValue("true") boolean readOnly,
		List<String> allowedBuckets,
		List<@NotBlank String> allowedPrefixes,
		@DefaultValue("false") boolean allowLocalOverwrite) {

	public S3Properties {
		allowedBuckets = allowedBuckets == null ? List.of() : List.copyOf(allowedBuckets);
		allowedPrefixes = allowedPrefixes == null ? List.of() : List.copyOf(allowedPrefixes);
	}

	@AssertTrue(message = "s3.endpoint requires s3.region")
	boolean isEndpointRegionValid() {
		return endpoint == null || StringUtils.hasText(region);
	}

	@AssertTrue(message = "s3.access-key and s3.secret-key must be configured together")
	boolean isCredentialPairValid() {
		return StringUtils.hasText(accessKey) == StringUtils.hasText(secretKey);
	}

	@AssertTrue(message = "s3.session-token requires explicit access and secret keys")
	boolean isSessionTokenValid() {
		return !StringUtils.hasText(sessionToken) || isCredentialPairValid() && StringUtils.hasText(accessKey);
	}

	@AssertTrue(message = "s3.presign-duration must be between PT1M and P7D")
	boolean isPresignDurationValid() {
		return presignDuration != null
				&& presignDuration.compareTo(Duration.ofMinutes(1)) >= 0
				&& presignDuration.compareTo(Duration.ofDays(7)) <= 0;
	}
}
