package org.springframework.ai.mcp.sample.server.entity;

public record S3OperationResult(boolean success, String bucket, String key, String message, String presignedUrl) {
}
