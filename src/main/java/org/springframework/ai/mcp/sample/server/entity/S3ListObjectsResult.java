package org.springframework.ai.mcp.sample.server.entity;

import java.util.List;

public record S3ListObjectsResult(List<S3Object> objects, List<String> folders, String nextMarker) {

	public S3ListObjectsResult {
		objects = List.copyOf(objects);
		folders = List.copyOf(folders);
	}

}
