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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;


import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.StringUtils;
import org.springframework.ai.mcp.sample.server.entity.S3Bucket;
import org.springframework.ai.mcp.sample.server.entity.S3ListObjectsResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import static org.springframework.ai.mcp.sample.server.McpServerApplication.*;


@Service
public class S3Service {


    AmazonS3 s3Client;

    public S3Service() {
        AWSCredentials credentials = new BasicAWSCredentials(ak
                , sk);
        ClientConfiguration configuration = new ClientConfiguration();
        configuration.setProtocol(Protocol.HTTP);
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withClientConfiguration(configuration)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "cn-north-3a"))
                .enableForceGlobalBucketAccess()
                .enablePathStyleAccess()
                .build();

    }

    @Tool(description = "Retrieve bucket information including owner, region, and creation date, returning null if the bucket does not exist.")
    public S3Bucket getBucketInfo(@ToolParam(description = "Bucket Name") String bucket) {
        S3Bucket S3Bucket = null;
        for (Bucket b : s3Client.listBuckets()) {
            if (b.getName().equals(bucket)) {
                S3Bucket = new S3Bucket(b);
            }
        }
        if (S3Bucket == null) {
            return S3Bucket;
        } else {
            S3Bucket.setLocation(s3Client.getBucketLocation(bucket));
            return S3Bucket;
        }
    }


    @Tool(description = "Download an object to a specified local file path.")
    public String downloadObject(@ToolParam(description = "The full path key of the object") String key,
                                 @ToolParam(description = "Bucket containing the object") String bucket,
                                 @ToolParam(description = "Absolute local file path with filename for downloading") String path) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucket, key);
        s3Client.getObject(getObjectRequest, new File(path));
        return "success";
    }


    @Tool(description = "List objects with pagination (max 100 results per call). Use NextMarker for subsequent requests. Returns: object list (each with key, modifyTime, storageClass, size, etag), folder list, NextMarker for continuation.")
    public S3ListObjectsResult listObjects(
            @ToolParam(description = "Target bucket for object listing") String bucket,
            @ToolParam(description = "Starting marker key for pagination (optional, begins from start if omitted)") String marker,
            @ToolParam(description = "Object key prefix filter (e.g. folder path, optional)") String prefix,
            @ToolParam(description = "Directory grouping delimiter character (optional)") String delimiter,
            @ToolParam(description = "Maximum objects to return (1-100, defaults to 100 if omitted)") Integer maxKeys) {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        listObjectsRequest.setBucketName(bucket);
        listObjectsRequest.setPrefix(prefix);
        listObjectsRequest.setMarker(marker);
        listObjectsRequest.setDelimiter(StringUtils.isNullOrEmpty(delimiter) ? "/" : delimiter);
        listObjectsRequest.setMaxKeys(maxKeys == null ? 100 : maxKeys);
        listObjectsRequest.setEncodingType("UTF-8");
        return new S3ListObjectsResult(s3Client.listObjects(listObjectsRequest));
    }


    @Tool(description = "Generate a time-limited presigned URL (15-minute validity) for accessing private objects")
    public String generatePresignedUrl(
            @ToolParam(description = "Complete object key path in S3 namespace") String key,
            @ToolParam(description = "Target bucket containing the object") String bucket) {
        return s3Client.generatePresignedUrl(bucket, key, null).toString();
    }

    @Tool(description = "Retrieve technical metadata for a specific object")
    public ObjectMetadata getObjectMetadata(
            @ToolParam(description = "Complete object key path in S3 namespace") String key,
            @ToolParam(description = "Target bucket containing the object") String bucket) {
        return s3Client.getObject(bucket, key).getObjectMetadata();
    }

    @Tool(description = "Create a virtual directory in the specified bucket (Note: Implemented by uploading an empty object with trailing '/')")
    public String createDirectory(
            @ToolParam(description = "Full path of the virtual directory ending with '/' (e.g. 'aaa/aab/folder/')") String folder,
            @ToolParam(description = "Target bucket for directory creation") String bucket) {
        if (!folder.endsWith("/")) {
            folder = folder + "/";
        }
        InputStream inputStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        s3Client.putObject(bucket, folder, inputStream, null);
        return "success";
    }


    @Tool(description = "List all S3 buckets with metadata including bucket name, creation timestamp, and owner's canonical user ID")
    public List<Bucket> getBucketList() {
        return s3Client.listBuckets();
    }


    @Tool(description = "Upload a local file to S3 bucket and return its presigned download URL with default 15-minute validity")
    public String uploadObject(
            @ToolParam(description = "Full object key path in S3 namespace (including any prefix directories)") String key,
            @ToolParam(description = "Target bucket for object storage") String bucket,
            @ToolParam(description = "Absolute local filesystem path of the source file") String filePath) throws FileNotFoundException {
        s3Client.putObject(bucket, key, new FileInputStream(filePath), null);
        return s3Client.generatePresignedUrl(bucket, key, new Date(System.currentTimeMillis() + 900000L)).toString();
    }


}