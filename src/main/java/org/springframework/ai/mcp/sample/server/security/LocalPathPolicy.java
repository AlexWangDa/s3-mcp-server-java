package org.springframework.ai.mcp.sample.server.security;

import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.LOCAL_FILE_EXISTS;
import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.NOT_FOUND;
import static org.springframework.ai.mcp.sample.server.exception.S3ErrorCode.PATH_OUTSIDE_ROOT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class LocalPathPolicy {

	private final Path root;

	private final boolean allowOverwrite;

	public LocalPathPolicy(S3Properties properties) throws IOException {
		this.root = properties.localRoot().toAbsolutePath().normalize().toRealPath();
		this.allowOverwrite = properties.allowLocalOverwrite();
	}

	public Path resolveUpload(String input) {
		Path candidate = contained(input, true);
		if (!Files.isRegularFile(candidate)) {
			throw new S3ToolException(NOT_FOUND, "Upload source is not a regular file");
		}
		return candidate;
	}

	public Path resolveDownload(String input) {
		Path candidate = contained(input, false);
		if (Files.exists(candidate) && !this.allowOverwrite) {
			throw new S3ToolException(LOCAL_FILE_EXISTS, "Download destination already exists");
		}
		return candidate;
	}

	private Path contained(String input, boolean mustExist) {
		if (!StringUtils.hasText(input)) {
			throw new S3ToolException(PATH_OUTSIDE_ROOT, "Local path is empty");
		}
		try {
			Path supplied = Path.of(input);
			Path candidate = (supplied.isAbsolute() ? supplied : this.root.resolve(supplied))
				.toAbsolutePath()
				.normalize();
			if (!candidate.startsWith(this.root)) {
				throw new S3ToolException(PATH_OUTSIDE_ROOT, "Local path is outside s3.local-root");
			}
			Path checked;
			if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
				checked = candidate.toRealPath();
			}
			else if (mustExist) {
				return candidate;
			}
			else {
				Path parent = candidate.getParent();
				if (parent == null) {
					throw new S3ToolException(PATH_OUTSIDE_ROOT, "Download path has no parent");
				}
				checked = parent.toRealPath().resolve(candidate.getFileName()).normalize();
			}
			if (!checked.startsWith(this.root)) {
				throw new S3ToolException(PATH_OUTSIDE_ROOT, "Symlink escapes s3.local-root");
			}
			return checked;
		}
		catch (InvalidPathException | IOException ex) {
			throw new S3ToolException(PATH_OUTSIDE_ROOT, "Local path cannot be resolved", ex);
		}
	}

}
