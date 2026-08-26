# Task 1 Report

Status: DONE

Commit: `build: restore reproducible Maven wrapper` (final hash reported in handoff)

## Implementation

- Regenerated wrapper scripts and restored executable mode on `mvnw`.
- Pinned Maven Wrapper 3.3.4 to Maven 3.9.11 with the required SHA-256 checksum.
- Added Java 17 compiler configuration, Spring Boot validation starter, and test starter.
- Left existing application dependency versions unchanged.

## Tests

- `./mvnw --version` — Apache Maven 3.9.11; passed.
- `./mvnw -B verify` — `BUILD SUCCESS`; passed.
- `git ls-files --stage mvnw` — mode `100755` after staging; passed.

## Concerns

- The worktree contained an untracked `target/` directory from prior activity; it was not included in the commit.
