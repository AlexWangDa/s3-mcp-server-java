package org.springframework.ai.mcp.sample.server.entity;

import java.time.Instant;

public record S3Bucket(String name, String owner, Instant creationDate, String location) {
}
