/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.mcp.sample.server.service;

import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.ACCESS_DENIED;
import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.NOT_FOUND;
import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.S3_ERROR;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.entity.S3Bucket;
import org.springframework.ai.mcp.sample.server.entity.S3ListObjectsResult;
import org.springframework.ai.mcp.sample.server.entity.S3Object;
import org.springframework.ai.mcp.sample.server.entity.S3ObjectMetadata;
import org.springframework.ai.mcp.sample.server.entity.S3OperationResult;
import org.springframework.ai.mcp.sample.server.exception.S3ErrorCode;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;
import org.springframework.ai.mcp.sample.server.security.LocalPathPolicy;
import org.springframework.ai.mcp.sample.server.security.S3AccessPolicy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3Service {

	private final S3Client s3;

	private final S3Presigner presigner;

	private final S3Properties properties;

	private final LocalPathPolicy paths;

	private final S3AccessPolicy access;

	public S3Service(S3Client s3, S3Presigner presigner, S3Properties properties, LocalPathPolicy paths,
			S3AccessPolicy access) {
		this.s3 = s3;
		this.presigner = presigner;
		this.properties = properties;
		this.paths = paths;
		this.access = access;
	}

	@Tool(description = "Retrieve bucket information including owner, region, and creation date.")
	public S3Bucket getBucketInfo(@ToolParam(description = "Bucket Name") String bucket) {
		this.access.requireBucket(bucket);
		S3Bucket found = getBucketList().stream()
			.filter(value -> value.name().equals(bucket))
			.findFirst()
			.orElseThrow(() -> new S3ToolException(NOT_FOUND, "Bucket does not exist: " + bucket));
		String location = awsCall(bucket,
				() -> this.s3.getBucketLocation(builder -> builder.bucket(bucket))).locationConstraintAsString();
		return new S3Bucket(found.name(), found.owner(), found.creationDate(), location);
	}

	@Tool(description = "Download an object to a specified local file path.")
	public S3OperationResult downloadObject(
			@ToolParam(description = "The full path key of the object") String key,
			@ToolParam(description = "Bucket containing the object") String bucket,
			@ToolParam(description = "Absolute local file path with filename for downloading") String path) {
		this.access.requireObject(bucket, key);
		Path destination = this.paths.resolveDownload(path);
		awsCall(resource(bucket, key),
				() -> this.s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), destination));
		return new S3OperationResult(true, bucket, key, "Downloaded object", null);
	}

	@Tool(description = "List objects with pagination (max 100 results per call). Use NextMarker for subsequent requests. Returns: object list (each with key, modifyTime, storageClass, size, eTag), folder list, NextMarker for continuation.")
	public S3ListObjectsResult listObjects(
			@ToolParam(description = "Target bucket for object listing") String bucket,
			@ToolParam(description = "Starting marker key for pagination (optional, begins from start if omitted)") String marker,
			@ToolParam(description = "Object key prefix filter (e.g. folder path, optional)") String prefix,
			@ToolParam(description = "Directory grouping delimiter character (optional)") String delimiter,
			@ToolParam(description = "Maximum objects to return (1-100, defaults to 100 if omitted)") Integer maxKeys) {
		if (maxKeys != null && (maxKeys < 1 || maxKeys > 100)) {
			throw new IllegalArgumentException("maxKeys must be between 1 and 100");
		}
		this.access.requireBucket(bucket);
		if (StringUtils.hasText(prefix) || !this.properties.allowedPrefixes().isEmpty()) {
			this.access.requireObject(bucket, prefix);
		}
		ListObjectsRequest request = ListObjectsRequest.builder()
			.bucket(bucket)
			.marker(marker)
			.prefix(prefix)
			.delimiter(StringUtils.hasText(delimiter) ? delimiter : "/")
			.maxKeys(maxKeys == null ? 100 : maxKeys)
			.build();
		ListObjectsResponse response = awsCall(bucket, () -> this.s3.listObjects(request));
		List<S3Object> objects = response.contents()
			.stream()
			.map(object -> new S3Object(object.key(), object.storageClassAsString(), object.eTag(),
					object.lastModified(), object.size() == null ? 0L : object.size()))
			.toList();
		List<String> folders = response.commonPrefixes().stream().map(prefixValue -> prefixValue.prefix()).toList();
		return new S3ListObjectsResult(objects, folders, response.nextMarker());
	}

	@Tool(description = "Generate a time-limited presigned URL for accessing private objects")
	public String generatePresignedUrl(
			@ToolParam(description = "Complete object key path in S3 namespace") String key,
			@ToolParam(description = "Target bucket containing the object") String bucket) {
		this.access.requireObject(bucket, key);
		GetObjectPresignRequest request = GetObjectPresignRequest.builder()
			.signatureDuration(this.properties.presignDuration())
			.getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
			.build();
		return awsCall(resource(bucket, key), () -> this.presigner.presignGetObject(request)).url().toString();
	}

	@Tool(description = "Retrieve technical metadata for a specific object")
	public S3ObjectMetadata getObjectMetadata(
			@ToolParam(description = "Complete object key path in S3 namespace") String key,
			@ToolParam(description = "Target bucket containing the object") String bucket) {
		this.access.requireObject(bucket, key);
		HeadObjectResponse response = awsCall(resource(bucket, key),
				() -> this.s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()));
		return new S3ObjectMetadata(response.contentLength(), response.eTag(), response.contentType(),
				response.lastModified(), response.metadata());
	}

	@Tool(description = "Create a virtual directory in the specified bucket (Note: Implemented by uploading an empty object with trailing '/')")
	public S3OperationResult createDirectory(
			@ToolParam(description = "Full path of the virtual directory ending with '/' (e.g. 'aaa/aab/folder/')") String folder,
			@ToolParam(description = "Target bucket for directory creation") String bucket) {
		this.access.requireMutation("createDirectory");
		String key = folder.endsWith("/") ? folder : folder + "/";
		this.access.requireObject(bucket, key);
		awsCall(resource(bucket, key), () -> this.s3.putObject(
				PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.empty()));
		return new S3OperationResult(true, bucket, key, "Created directory marker", null);
	}

	@Tool(description = "List all S3 buckets with metadata including bucket name, creation timestamp, and owner")
	public List<S3Bucket> getBucketList() {
		ListBucketsResponse response = awsCall("bucket list", this.s3::listBuckets);
		String owner = response.owner() == null ? null : response.owner().displayName();
		return response.buckets()
			.stream()
			.filter(bucket -> this.access.allowsBucket(bucket.name()))
			.map(bucket -> new S3Bucket(bucket.name(), owner, bucket.creationDate(), null))
			.toList();
	}

	@Tool(description = "Upload a local file to S3 bucket and return its presigned download URL")
	public S3OperationResult uploadObject(
			@ToolParam(description = "Full object key path in S3 namespace (including any prefix directories)") String key,
			@ToolParam(description = "Target bucket for object storage") String bucket,
			@ToolParam(description = "Absolute local filesystem path of the source file") String filePath) {
		this.access.requireMutation("uploadObject");
		this.access.requireObject(bucket, key);
		Path source = this.paths.resolveUpload(filePath);
		awsCall(resource(bucket, key),
				() -> this.s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), source));
		return new S3OperationResult(true, bucket, key, "Uploaded object", generatePresignedUrl(key, bucket));
	}

	private <T> T awsCall(String resource, Supplier<T> request) {
		try {
			return request.get();
		}
		catch (S3Exception exception) {
			throw translate(exception, resource);
		}
	}

	private S3ToolException translate(S3Exception exception, String resource) {
		S3ErrorCode code;
		if (exception instanceof NoSuchKeyException) {
			code = NOT_FOUND;
		}
		else {
			code = switch (exception.statusCode()) {
				case 403 -> ACCESS_DENIED;
				case 404 -> NOT_FOUND;
				default -> S3_ERROR;
			};
		}
		return new S3ToolException(code, "S3 request failed for " + resource, exception);
	}

	private String resource(String bucket, String key) {
		return bucket + "/" + key;
	}

}
