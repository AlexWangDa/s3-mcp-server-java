package org.springframework.ai.mcp.sample.server.exception;

public final class S3ToolException extends RuntimeException {

	private final S3ErrorCode code;

	public S3ToolException(S3ErrorCode code, String message) {
		super(code + ": " + message);
		this.code = code;
	}

	public S3ToolException(S3ErrorCode code, String message, Throwable cause) {
		super(code + ": " + message, cause);
		this.code = code;
	}

	public S3ErrorCode code() {
		return this.code;
	}

}
