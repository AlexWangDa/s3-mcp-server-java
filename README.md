# S3-MCP-Server-Java [![Java CI](https://github.com/yourusername/s3-mcp-server-java/actions/workflows/maven.yml/badge.svg)](https://github.com/yourusername/s3-mcp-server-java/actions)

A Spring AI-based STDIO server implementing S3 protocol operations for Amazon S3 and S3-compatible object storage services.

## Features

- 🚀 S3 protocol compatible operations
- 🔄 Spring AI-powered processing
- ☁️ Supports Amazon S3 and all S3-compatible services

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+

### Installation

```bash
git clone https://github.com/AlexWangDa/s3-mcp-server-java
cd s3-mcp-server-java
mvn clean install
```


## MCP Integration

Add to your MCP configuration file:

```json
{
  "mcpServers": {
    "s3-mcp-server": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/your/s3-mcp-server-0.0.1-SNAPSHOT.jar",
        "--s3.endpoint=your_endpoint",
        "--s3s.accessKey=your_access_key",
        "--s3.secretKey=your_secret_key"
      ]
    }
  }
}
```

## Key Parameters


| Parameter      | Description                    | Example                           |
| -------------- | ------------------------------ | --------------------------------- |
| `s3.endpoint`  | S3-compatible service endpoint | oss.cn-north-3.inspurcloudoss.com |
| `s3.accessKey` | Access key for authentication  | NTQtNDQxYy00NTgyL                 |
| `s3.secretKey` | Secret key for authentication  | ZEtM2Y1YS00MjIzL                  |


## Features

### Core Operations

- 📦 **Bucket Management**
  - List all buckets with metadata (`getBucketList`)
  - Retrieve detailed bucket info including location and creation date (`getBucketInfo`)

### Object Operations

- 📥 **File Transfer**
  - Upload local files with auto-generated presigned URLs (`uploadObject`)
  - Download objects to specified local paths (`downloadObject`)

### Advanced Features

- 🔍 **Object Discovery**
  - Paginated object listing with prefix filtering and NextMarker support (`listObjects`)
  - Virtual directory creation via empty object markers (`createDirectory`)

### Security & Access

- 🔑 **Presigned URLs**
  - Generate 15-minute valid URLs for private object access (`generatePresignedUrl`)
  - Automatic URL generation on upload operations

### Metadata Management

- 📄 **Object Inspection**
  - Retrieve technical metadata including ETag, storage class, and size (`getObjectMetadata`)

### Technical Implementation

- 🌐 **S3 Protocol Implementation**
  - AWS SDK-based client with HTTP protocol configuration
  - Path-style access and global bucket access enabled
  - UTF-8 encoding support for object listings
  - Automatic directory marker normalization (appends trailing '/' if missing)

### Compatibility

- ☁️ **Multi-Provider Support**
  - Works with Amazon S3 and any S3-compatible storage
  - Tested with Inspur OSS (official implementation example)

### Spring AI Integration

- 🤖 **Tool Annotations**
  - @Tool-annotated service methods for AI integration
  - Parameter validation through @ToolParam descriptors



## Test Instructions

Please query the list of buckets in my object storage, then retrieve information about the first bucket and present it in a readable format. Next, list the first 200 files in the bucket. Query the details of the 200th file, download it to the current working directory, and generate a shareable link for the file.

Afterward, create a folder named "mcp" within the bucket. Generate a 1000-word essay on the theme "S3 Java MCP," save it as a local TXT file, upload it to the "mcp" folder, and finally generate a downloadable URL for this file.


## Notes

1. Always keep credentials secure - never commit them to version control
2. Test with different S3-compatible providers (AWS, MinIO, InspurOSS, etc.)


## License

Apache 2.0
