package org.springframework.ai.mcp.sample.server.entity;

import java.time.Instant;
import java.util.Map;

public record S3ObjectMetadata(long contentLength, String eTag, String contentType, Instant lastModified,
		Map<String, String> userMetadata) {

	public S3ObjectMetadata {
		userMetadata = Map.copyOf(userMetadata);
	}

}
