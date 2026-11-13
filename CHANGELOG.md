# Changelog

All notable changes to this project are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-08-27

### Added

- Eight STDIO MCP tools for S3 bucket, object, metadata, presigning, upload,
  download, and directory-marker operations.
- Validated AWS and S3-compatible configuration, including MinIO endpoints,
  path-style access, temporary credentials, and the AWS default provider chain.
- Maven Wrapper, Java 17/21 CI, MinIO integration tests, and packaged STDIO
  protocol tests.

### Changed

- Migrated S3 integration to AWS SDK for Java v2.
- Upgraded to Spring Boot 4.1.1 and Spring AI 2.0.0.
- Made response models independent of AWS SDK implementation types.

### Security

- Enabled remote read-only mode by default with optional bucket and key-prefix
  allowlists.
- Contained local file access below `s3.local-root`, including symlink checks,
  atomic downloads, and no-clobber behavior by default.
- Kept MCP stdout protocol-only and prevented credentials, presigned queries,
  and unrestricted local paths from appearing in user-facing errors.

[0.2.0]: https://github.com/AlexWangDa/s3-mcp-server-java/releases/tag/v0.2.0
