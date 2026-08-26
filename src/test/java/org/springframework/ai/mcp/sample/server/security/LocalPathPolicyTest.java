package org.springframework.ai.mcp.sample.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;

class LocalPathPolicyTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void rejectsTraversalOutsideLocalRoot() throws IOException {
		LocalPathPolicy paths = paths(false);

		assertThatThrownBy(() -> paths.resolveUpload("../secret.txt"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("PATH_OUTSIDE_ROOT");
	}

	@Test
	void rejectsAbsolutePathOutsideLocalRoot() throws IOException {
		LocalPathPolicy paths = paths(false);
		Path outsideRoot = temporaryDirectory.resolve("outside.txt");
		Files.writeString(outsideRoot, "secret");

		assertThatThrownBy(() -> paths.resolveUpload(outsideRoot.toString()))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("PATH_OUTSIDE_ROOT");
	}

	@Test
	void rejectsUploadThroughSymlinkOutsideLocalRoot() throws IOException {
		Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
		Path outsideRoot = temporaryDirectory.resolve("outside.txt");
		Files.writeString(outsideRoot, "secret");
		Files.createSymbolicLink(root.resolve("escape"), outsideRoot);
		LocalPathPolicy paths = paths(root, false);

		assertThatThrownBy(() -> paths.resolveUpload("escape"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("PATH_OUTSIDE_ROOT");
	}

	@Test
	void rejectsMissingUploadBelowSymlinkOutsideLocalRoot() throws IOException {
		Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
		Path outsideDirectory = Files.createDirectory(temporaryDirectory.resolve("outside"));
		Files.createSymbolicLink(root.resolve("escape"), outsideDirectory);
		LocalPathPolicy paths = paths(root, false);

		assertThatThrownBy(() -> paths.resolveUpload("escape/missing.txt"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("PATH_OUTSIDE_ROOT");
	}

	@Test
	void rejectsMissingUploadSource() throws IOException {
		LocalPathPolicy paths = paths(false);

		assertThatThrownBy(() -> paths.resolveUpload("missing.txt"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("NOT_FOUND");
	}

	@Test
	void rejectsExistingDownloadDestinationWhenOverwriteIsDisabled() throws IOException {
		Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
		Files.writeString(root.resolve("download.txt"), "existing");
		LocalPathPolicy paths = paths(root, false);

		assertThatThrownBy(() -> paths.resolveDownload("download.txt"))
			.isInstanceOf(S3ToolException.class)
			.hasMessageContaining("LOCAL_FILE_EXISTS");
	}

	@Test
	void resolvesNewDownloadDestinationInsideLocalRoot() throws IOException {
		Path root = Files.createDirectory(temporaryDirectory.resolve("root"));
		LocalPathPolicy paths = paths(root, false);

		assertThat(paths.resolveDownload("download.txt")).isEqualTo(root.resolve("download.txt"));
	}

	private LocalPathPolicy paths(boolean allowOverwrite) throws IOException {
		return paths(Files.createDirectory(temporaryDirectory.resolve("root")), allowOverwrite);
	}

	private LocalPathPolicy paths(Path root, boolean allowOverwrite) throws IOException {
		return new LocalPathPolicy(properties(root, allowOverwrite));
	}

	private S3Properties properties(Path root, boolean allowOverwrite) {
		return new S3Properties(null, null, null, null, null, false, Duration.ofMinutes(15), root,
				true, List.of(), List.of(), allowOverwrite);
	}
}
