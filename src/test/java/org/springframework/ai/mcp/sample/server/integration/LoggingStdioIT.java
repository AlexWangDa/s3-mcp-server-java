package org.springframework.ai.mcp.sample.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingStdioIT {

	@TempDir
	Path tempDirectory;

	@Test
	void configuredFileLoggingWritesDiagnosticsWithoutUsingStdout() throws IOException, InterruptedException {
		Path jar = Path.of("target", "s3-mcp-server-0.2.0-SNAPSHOT.jar");
		Path logFile = this.tempDirectory.resolve("server.log");
		Path stdoutFile = this.tempDirectory.resolve("stdout.log");
		Path stderrFile = this.tempDirectory.resolve("stderr.log");
		assertThat(jar).isRegularFile();

		Process process = new ProcessBuilder(javaExecutable(), "-jar", jar.toString(),
				"--s3.region=us-east-1", "--logging.file.name=" + logFile)
			.redirectOutput(stdoutFile.toFile())
			.redirectError(stderrFile.toFile())
			.start();
		try {
			await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
				assertThat(logFile).isRegularFile();
				assertThat(Files.readString(logFile)).contains("Started McpServerApplication");
			});
			assertThat(Files.readString(stderrFile)).contains("Started McpServerApplication");
			assertThat(Files.readString(stdoutFile)).isEmpty();
		}
		finally {
			process.getOutputStream().close();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroy();
				if (!process.waitFor(5, TimeUnit.SECONDS)) {
					process.destroyForcibly();
				}
			}
		}
	}

	private String javaExecutable() {
		String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

}
