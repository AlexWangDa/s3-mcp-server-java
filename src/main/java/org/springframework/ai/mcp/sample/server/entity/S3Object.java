package org.springframework.ai.mcp.sample.server.entity;

import java.time.Instant;

public record S3Object(String key, String storageClass, String eTag, Instant modifyTime, long size) {
}
