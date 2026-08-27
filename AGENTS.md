# Repository Guidelines

## Project Structure & Module Organization

Production code lives under `src/main/java/org/springframework/ai/mcp/sample/server`. Keep AWS client setup in `config`, access and path checks in `security`, tool behavior in `service`, and AWS-independent response records in `entity`. Runtime settings and stderr logging are in `src/main/resources`. Tests mirror those packages under `src/test/java`; MinIO and packaged-STDIO coverage belongs in `integration`. Design decisions and implementation plans are recorded in `docs/superpowers`.

## Build, Test, and Development Commands

Use the checked-in Maven Wrapper so local and CI builds use the same Maven version.

- `./mvnw clean package` builds the executable JAR in `target/`.
- `./mvnw -B test` runs the fast unit-test suite.
- `./mvnw -B clean verify` runs all checks, including Failsafe integration tests; Docker is required for Testcontainers.
- `./mvnw -B -Dtest=S3ServiceReadTest test` runs one test class while iterating.

Run the packaged server with `java -jar target/s3-mcp-server-0.2.0.jar --s3.region=us-east-1`. Keep stdout reserved for MCP traffic.

## Coding Style & Naming Conventions

Target Java 17 and indent Java with four spaces. Prefer constructor injection, immutable records, focused methods, and existing Spring conventions. Use `UpperCamelCase` for types, `lowerCamelCase` for methods and fields, and `UPPER_SNAKE_CASE` for constants. Name unit tests `*Test` and integration tests `*IT`. No formatter or linter is enforced, so match surrounding imports and whitespace.

## Testing Guidelines

Use JUnit 5, AssertJ, and Mockito for isolated behavior. Use Testcontainers with the shared MinIO fixture for S3 compatibility tests. Cover success, policy rejection, error translation, and side effects; security fixes need a regression test. Run `clean verify` before submitting changes.

## Commit & Pull Request Guidelines

Follow the repository's Conventional Commit style, for example `feat: add bucket filter` or `docs: clarify MinIO setup`. Keep commits scoped. Pull requests should explain behavior and security impact, link related issues, list verification commands and results, and update README/config examples when interfaces change. Attach screenshots only when a visual change exists.

## Security & Configuration Tips

Never commit credentials, tokens, presigned URLs, or real bucket data. Prefer AWS profiles or the default credential chain. Preserve `s3.read-only=true` as the default, and keep all local file operations within `s3.local-root`, including after symlink resolution.
