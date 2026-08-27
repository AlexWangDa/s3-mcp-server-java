# S3 MCP Server for Java

[![CI](https://github.com/AlexWangDa/s3-mcp-server-java/actions/workflows/ci.yml/badge.svg)](https://github.com/AlexWangDa/s3-mcp-server-java/actions/workflows/ci.yml)
![Java 17+](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
[![License](https://img.shields.io/github/license/AlexWangDa/s3-mcp-server-java)](LICENSE)

A secure, STDIO-based [Model Context Protocol](https://modelcontextprotocol.io/)
server for Amazon S3 and S3-compatible object storage. It is built with Spring
Boot 4.1.1, Spring AI 2.0.0, and AWS SDK for Java v2.

The server gives MCP clients a focused set of bucket, object, presigning, and
local transfer tools. Remote writes are disabled by default, and every local
file operation is contained below a configurable root directory.

## Features

- Eight stable MCP tools for buckets, objects, metadata, presigned URLs, and
  file transfers.
- Amazon S3, MinIO, and other S3-compatible services through AWS SDK v2.
- AWS default credential and region chains, explicit temporary credentials,
  custom endpoints, HTTPS, and path-style access.
- Read-only mode enabled by default, with optional bucket and key-prefix
  allowlists.
- Local path traversal and symlink escape protection, plus no-clobber downloads.
- Protocol-safe logging: MCP messages use stdout; diagnostics use stderr or an
  optional log file.
- Unit, MinIO, and packaged STDIO integration coverage on Java 17 and 21.

## Requirements

- Java 17 or newer
- Docker for the full integration-test suite
- AWS credentials, unless connecting to a local S3-compatible service with
  explicit test credentials

Maven does not need to be installed. The repository includes Maven Wrapper
3.9.11 for reproducible local and CI builds.

## Quick Start

Clone the repository and build the executable JAR:

```bash
git clone https://github.com/AlexWangDa/s3-mcp-server-java.git
cd s3-mcp-server-java
./mvnw clean package
```

Run it with an AWS profile inherited from your shell:

```bash
export AWS_PROFILE=my-profile
java -jar target/s3-mcp-server-0.2.0-SNAPSHOT.jar \
  --s3.region=us-east-1 \
  --s3.local-root=/absolute/path/to/safe/files
```

The process communicates with its MCP client over STDIO. Do not redirect
application diagnostics to stdout. To also retain diagnostics in a file, add:

```text
--logging.file.name=/absolute/path/s3-mcp.log
```

## MCP Client Configuration

Credentials are intentionally absent from this example. Configure an AWS
profile or standard AWS environment variables in the process that launches the
MCP client.

Use the following structure in Claude Desktop's MCP configuration or in
Cursor's `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "s3": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/s3-mcp-server-0.2.0-SNAPSHOT.jar",
        "--s3.region=us-east-1",
        "--s3.local-root=/absolute/path/to/safe/files"
      ]
    }
  }
}
```

Restart the client after changing its configuration. Desktop applications may
not inherit variables from an interactive shell; use AWS shared profile files
or configure the variables in the client's launch environment.

## Available Tools

| Tool | Side effect | Purpose |
| --- | --- | --- |
| `getBucketList` | None | List buckets visible to the configured credentials and allowlist. |
| `getBucketInfo` | None | Return a bucket's owner, region, and creation date. |
| `listObjects` | None | List objects and common prefixes with marker-based pagination. |
| `getObjectMetadata` | None | Read object size, type, timestamps, ETag, and user metadata. |
| `generatePresignedUrl` | None | Create a time-limited GET URL for a private object. |
| `downloadObject` | Local file write | Download an object below `s3.local-root` without modifying S3. |
| `uploadObject` | S3 write | Upload a contained local file; disabled in read-only mode. |
| `createDirectory` | S3 write | Create a zero-byte directory marker; disabled in read-only mode. |

`listObjects` returns at most 100 objects per request. Use its `nextMarker`
value to retrieve subsequent pages.

## Configuration

Spring Boot maps environment variables to the corresponding `s3.*` properties.
Explicit access and secret keys must be supplied together. If they are absent,
the AWS SDK default credential chain is used.

| Environment variable | Property | Default / purpose |
| --- | --- | --- |
| `S3_ENDPOINT` | `s3.endpoint` | AWS endpoint; custom endpoints require a region. |
| `S3_REGION` | `s3.region` | Uses the AWS default region chain when omitted. |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `s3.access-key` / `s3.secret-key` | Uses the AWS default credential chain when omitted. |
| `S3_SESSION_TOKEN` | `s3.session-token` | Optional token for explicit temporary credentials. |
| `S3_PATH_STYLE_ACCESS` | `s3.path-style-access` | `false`; commonly `true` for MinIO. |
| `S3_PRESIGN_DURATION` | `s3.presign-duration` | `PT15M`; valid range is 1 minute to 7 days. |
| `S3_LOCAL_ROOT` | `s3.local-root` | `.`; containment root for upload and download paths. |
| `S3_READ_ONLY` | `s3.read-only` | `true`; blocks `uploadObject` and `createDirectory`. |
| `S3_ALLOWED_BUCKETS` | `s3.allowed-buckets` | Empty; optional comma-separated bucket allowlist. |
| `S3_ALLOWED_PREFIXES` | `s3.allowed-prefixes` | Empty; optional comma-separated key-prefix allowlist. |
| `S3_ALLOW_LOCAL_OVERWRITE` | `s3.allow-local-overwrite` | `false`; protects existing download destinations. |

Durations use ISO-8601 syntax, for example `PT5M` for five minutes. Lists use
comma-separated values, for example `S3_ALLOWED_BUCKETS=reports,archives`.

## MinIO and S3-Compatible Services

For a local MinIO server listening on port 9000, start with:

```bash
export S3_ENDPOINT=http://localhost:9000
export S3_REGION=us-east-1
export S3_ACCESS_KEY=replace-me
export S3_SECRET_KEY=replace-me
export S3_PATH_STYLE_ACCESS=true
export S3_LOCAL_ROOT=/absolute/path/to/safe/files
export S3_READ_ONLY=true
```

Use credentials created for your local instance. Set `S3_READ_ONLY=false` only
when upload and directory-marker operations are intentional. The repository's
[`.env.example`](.env.example) contains the same safe placeholders; the
application does not load dotenv files automatically.

## Security Model

- **Remote writes are opt-in.** `s3.read-only=true` is the default and rejects
  `uploadObject` and `createDirectory` before contacting S3.
- **Local files stay contained.** Upload and download paths are normalized and
  resolved below `s3.local-root`; traversal and symlink escapes are rejected.
- **Existing files are protected.** Downloads do not overwrite a destination
  unless `s3.allow-local-overwrite=true` is explicitly configured.
- **Allowlists add defense in depth.** Bucket names must match exactly and keys
  must begin with an allowed prefix when the corresponding lists are set.
- **Provider errors are sanitized.** Tool errors do not expose credentials,
  session tokens, unrestricted local paths, or presigned query strings.

Application allowlists supplement, but do not replace, least-privilege IAM or
provider credentials. Treat generated presigned URLs as temporary secrets.

## Development

Run the fast unit-test suite:

```bash
./mvnw -B test
```

Run the complete build, including Testcontainers-backed MinIO and packaged
STDIO tests:

```bash
./mvnw -B clean verify
```

If the current login was added to the Docker group after it started, refresh
the session or run the build with:

```bash
sg docker -c './mvnw -B clean verify'
```

The integration suite uses the published
`minio/minio:RELEASE.2025-09-07T16-13-09Z` image. GitHub Actions runs the full
build on Linux with Java 17 and 21, plus a Windows Maven Wrapper smoke test.

## Contributing

Read [`AGENTS.md`](AGENTS.md) for repository layout, coding conventions, test
expectations, and pull-request guidance. Keep commits focused and use
Conventional Commit messages such as `feat: add bucket filter` or
`docs: clarify MinIO setup`.

## License

Licensed under the [Apache License 2.0](LICENSE).
