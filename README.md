# S3 MCP Server Java

A Spring Boot 4.1.1 and Spring AI 2.0.0 MCP server that exposes Amazon S3 and
S3-compatible storage through STDIO. It uses AWS SDK for Java v2 and requires
Java 17 or newer.

## Five-minute quick start

Build the executable JAR with the checked-in Maven Wrapper, then run it with an
AWS profile inherited from your shell:

```bash
./mvnw clean package
export AWS_PROFILE=my-profile
java -jar target/s3-mcp-server-0.2.0-SNAPSHOT.jar --s3.region=us-east-1
```

The server writes MCP protocol messages to stdout and diagnostics to stderr.
To also write diagnostics to a file, add
`--logging.file.name=/absolute/path/s3-mcp.log` to the Java command.

The server starts in remote read-only mode. Set `S3_READ_ONLY=false` only when
`uploadObject` and `createDirectory` should be enabled. File transfers are
contained below `S3_LOCAL_ROOT` (default: the current working directory), and
normalized paths and symlinks are rejected if they escape that root. Set an
explicit root such as `S3_LOCAL_ROOT=/absolute/safe/path` before enabling file
tools in an MCP host.

## MinIO and compatible services

For a local MinIO server already listening on port 9000, export the following
values before starting the JAR:

```bash
export S3_ENDPOINT=http://localhost:9000
export S3_REGION=us-east-1
export S3_ACCESS_KEY=replace-me
export S3_SECRET_KEY=replace-me
export S3_PATH_STYLE_ACCESS=true
export S3_LOCAL_ROOT=/absolute/safe/path
export S3_READ_ONLY=true
```

Use the credentials configured for your local MinIO instance. Change
`S3_READ_ONLY` to `false` only for intentional write tests. `.env.example`
contains the same safe starting values; load them using your shell or process
manager because the application does not read dotenv files automatically.

## Configuration

Spring Boot maps environment names to the corresponding command-line property.
Explicit access and secret keys must be provided together; otherwise the AWS
SDK default credential chain is used (profiles, standard AWS environment
variables, container credentials, or instance roles).

| Environment variable | Property | Default / purpose |
| --- | --- | --- |
| `S3_ENDPOINT` | `s3.endpoint` | AWS endpoint; custom endpoints require a region |
| `S3_REGION` | `s3.region` | AWS default region chain |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `s3.access-key` / `s3.secret-key` | AWS default credential chain |
| `S3_SESSION_TOKEN` | `s3.session-token` | Optional token for explicit temporary credentials |
| `S3_PATH_STYLE_ACCESS` | `s3.path-style-access` | `false`; commonly `true` for MinIO |
| `S3_PRESIGN_DURATION` | `s3.presign-duration` | `PT15M` (valid range: 1 minute to 7 days) |
| `S3_LOCAL_ROOT` | `s3.local-root` | `.`; containment root for upload/download paths |
| `S3_READ_ONLY` | `s3.read-only` | `true`; blocks remote mutations |
| `S3_ALLOWED_BUCKETS` | `s3.allowed-buckets` | Empty; optional comma-separated allowlist |
| `S3_ALLOWED_PREFIXES` | `s3.allowed-prefixes` | Empty; optional comma-separated key-prefix allowlist |
| `S3_ALLOW_LOCAL_OVERWRITE` | `s3.allow-local-overwrite` | `false`; protects existing download destinations |

## MCP host configuration

Credentials are deliberately absent from these examples. Configure an AWS
profile or AWS environment variables in the process that launches the MCP host.

Claude Desktop configuration:

```json
{
  "mcpServers": {
    "s3": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/s3-mcp-server-0.2.0-SNAPSHOT.jar",
        "--s3.region=us-east-1",
        "--s3.local-root=/absolute/safe/path"
      ]
    }
  }
}
```

Cursor `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "s3": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/s3-mcp-server-0.2.0-SNAPSHOT.jar",
        "--s3.region=us-east-1",
        "--s3.local-root=/absolute/safe/path"
      ]
    }
  }
}
```

Restart the host after changing its configuration. Desktop applications may not
inherit variables from an interactive shell, so set credentials in the host's
launch environment or use the AWS shared credentials/profile files.

## Available tools

- `getBucketList` and `getBucketInfo`
- `listObjects` and `getObjectMetadata`
- `generatePresignedUrl`
- `downloadObject` (local write below `s3.local-root`)
- `uploadObject` and `createDirectory` (disabled while read-only)

## Development and testing

```bash
./mvnw -B test
./mvnw -B clean verify
```

The first command runs unit tests. The second performs the full build, including
JUnit integration tests backed by Testcontainers and MinIO; it requires Docker.
CI verifies the project on Java 17 and 21.

## License

Apache License 2.0.
