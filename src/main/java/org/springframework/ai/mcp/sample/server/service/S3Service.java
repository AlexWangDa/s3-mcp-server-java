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

    //todo 查询桶信息

    @Tool(description = "查询某个桶的信息，返回桶的拥有者（owner）、桶所在区域（location）、桶的创建日期（creationDate），如果桶不存在则返回null。")
    public S3Bucket getBucketInfo(@ToolParam(description = "桶名") String bucket) {
        S3Bucket S3Bucket=null;
        for (Bucket b:s3Client.listBuckets()){
            if (b.getName().equals(bucket)){
               S3Bucket=new S3Bucket(b);
            }
        }
        if (S3Bucket==null){
            return S3Bucket;
        }else{
            S3Bucket.setLocation(s3Client.getBucketLocation(bucket));
            return S3Bucket;
        }
    }



    @Tool(description = "下载某个对象到本地的某个路径。")
    public String downloadObject(@ToolParam(description = "对象的全路径名称") String key,
                                 @ToolParam(description = "对象所在桶") String bucket,
                                 @ToolParam(description = "需要下载到的本地的全路径，需要带上文件名") String path) {
        GetObjectRequest getObjectRequest=new GetObjectRequest(bucket,key);
        s3Client.getObject(getObjectRequest,new File(path));
        return "success";
    }


    @Tool(description = "查询文件列表,请注意每次最多返回100个结果，如果想要查询更多，需要多次调用本接口，将返回的NextMarker作为下一次的marker。返回值包含了文件列表（objects,其中的每一条含有文件名（key）、文件创建时间(modifyTime)、存储类型（storageClass）、文件大小（size）、文件etag）、文件夹列表(folders)、下一次遍历开始的marker（NextMarker）。")
    public S3ListObjectsResult listObjects(@ToolParam(description = "需要查询的桶") String bucket,
                                           @ToolParam(description = "从哪个文件开始往后查询，此参数可以不传，不传就意味着从最开头开始查询。") String marker,
                                           @ToolParam(description = "文件的前缀是什么，可以填文件夹全路径，就代表着查询该文件夹下的文件。此参数可以不传。") String prefix,
                                           @ToolParam(description = "对Object名字进行分组的字符。所有Object名字包含指定的前缀，第一次出现delimiter字符之间的Object作为一组元素（即folders）。此参数可以不传。") String delimiter,
                                           @ToolParam(description = "指定返回Object的最大数。如果因为max-keys的设定无法一次完成列举，返回结果会附加NextMarker元素作为下一次列举的marker。如不传此参数，默认为100。此参数最多传100，如果想查询超过100的列表，需要多次调用本接口。") Integer maxKeys) {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest();
        listObjectsRequest.setBucketName(bucket);
        listObjectsRequest.setPrefix(prefix);
        listObjectsRequest.setMarker(marker);
        listObjectsRequest.setDelimiter(StringUtils.isNullOrEmpty(delimiter)? "/" : delimiter);
        listObjectsRequest.setMaxKeys(maxKeys == null ? 100 : maxKeys);
        listObjectsRequest.setEncodingType("UTF-8");
        return new S3ListObjectsResult(s3Client.listObjects(listObjectsRequest));
    }


    @Tool(description = "获取某个对象的分享链接，通过该分享链接，可以在十五分钟内访问到私有权限的对象。")
    public String getObjectUrl(@ToolParam(description = "对象的全路径名称") String key,
                               @ToolParam(description = "对象所在桶") String bucket) {
        return s3Client.generatePresignedUrl(bucket, key, null).toString();
    }

    @Tool(description = "查询某个对象的信息。")
    public ObjectMetadata getObjectInfo(@ToolParam(description = "对象的全路径名称") String key,
                                        @ToolParam(description = "对象所在桶") String bucket) {
        return s3Client.getObject(bucket, key).getObjectMetadata();
    }

    @Tool(description = "在桶中创建一个文件夹。")
    public String createFolder(@ToolParam(description = "文件夹的全路径名称(最后需要带上一个/，如aaa/aab/folder/)") String folder,
                               @ToolParam(description = "文件夹所在的桶") String bucket) {
        if (!folder.endsWith("/")) {
            folder = folder + "/";
        }
        InputStream inputStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        s3Client.putObject(bucket, folder, inputStream, null);
        return "success";
    }


    @Tool(description = "查询桶列表,返回值包含了桶的名字，桶的创建时间,桶的创建人的uid")
    public List<Bucket> getBucketList() {
        return s3Client.listBuckets();
    }


    @Tool(description = "向指定的桶内上传文件,上传完成后返回下载链接。")
    public String putObject(@ToolParam(description = "要命名为的文件名") String key,
                            @ToolParam(description = "要传的桶") String bucket,
                            @ToolParam(description = "需要上传的文件的本地路径") String in) throws FileNotFoundException {
        s3Client.putObject(bucket, key, new FileInputStream(in), null);
        return s3Client.generatePresignedUrl(bucket, key, new Date(System.currentTimeMillis() + 900000L)).toString();
    }


}