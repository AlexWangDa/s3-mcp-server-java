package org.springframework.ai.mcp.sample.server.security;

import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.ACCESS_DENIED;
import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.READ_ONLY;

import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class S3AccessPolicy {

	private final S3Properties properties;

	public S3AccessPolicy(S3Properties properties) {
		this.properties = properties;
	}

	public boolean allowsBucket(String bucket) {
		return this.properties.allowedBuckets().isEmpty() || this.properties.allowedBuckets().contains(bucket);
	}

	public void requireBucket(String bucket) {
		if (!StringUtils.hasText(bucket) || !allowsBucket(bucket)) {
			throw new S3ToolException(ACCESS_DENIED, "Bucket is not allowed: " + bucket);
		}
	}

	public void requireObject(String bucket, String key) {
		requireBucket(bucket);
		if (!StringUtils.hasText(key) || !this.properties.allowedPrefixes().isEmpty()
				&& this.properties.allowedPrefixes().stream()
					.filter(StringUtils::hasText)
					.noneMatch(key::startsWith)) {
			throw new S3ToolException(ACCESS_DENIED, "Object key is not allowed");
		}
	}

	public void requireMutation(String operation) {
		if (this.properties.readOnly()) {
			throw new S3ToolException(READ_ONLY, operation + " is disabled by s3.read-only=true");
		}
	}

}
