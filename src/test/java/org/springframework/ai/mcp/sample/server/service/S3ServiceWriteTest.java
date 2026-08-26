package org.springframework.ai.mcp.sample.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.entity.S3OperationResult;
import org.springframework.ai.mcp.sample.server.exception.S3ErrorCode;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;
import org.springframework.ai.mcp.sample.server.security.LocalPathPolicy;
import org.springframework.ai.mcp.sample.server.security.S3AccessPolicy;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceWriteTest {

	@Mock
	private S3Client s3;

	@Mock
	private S3Presigner presigner;

	@TempDir
	Path root;

	@Test
	void rejectsRemoteMutationsByDefaultBeforeCallingAws() throws Exception {
		Files.writeString(this.root.resolve("upload.txt"), "content");
		S3Service service = service(true);

		assertThatThrownBy(() -> service.uploadObject("docs/upload.txt", "reports", "upload.txt"))
			.isInstanceOfSatisfying(S3ToolException.class,
					exception -> assertThat(exception.code()).isEqualTo(S3ErrorCode.READ_ONLY));
		assertThatThrownBy(() -> service.createDirectory("docs/archive", "reports"))
			.isInstanceOfSatisfying(S3ToolException.class,
					exception -> assertThat(exception.code()).isEqualTo(S3ErrorCode.READ_ONLY));
		verifyNoInteractions(this.s3, this.presigner);
	}

	@Test
	void downloadsToAContainedPath() throws Exception {
		Files.createDirectory(this.root.resolve("downloads"));
		when(this.s3.getObject(any(GetObjectRequest.class), any(Path.class)))
			.thenReturn(GetObjectResponse.builder().contentLength(7L).build());

		S3OperationResult result = service(true).downloadObject("docs/report.pdf", "reports", "downloads/report.pdf");

		assertThat(result).isEqualTo(new S3OperationResult(true, "reports", "docs/report.pdf",
				"Downloaded object", null));
		ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
		ArgumentCaptor<Path> destination = ArgumentCaptor.forClass(Path.class);
		verify(this.s3).getObject(request.capture(), destination.capture());
		assertThat(request.getValue().bucket()).isEqualTo("reports");
		assertThat(request.getValue().key()).isEqualTo("docs/report.pdf");
		assertThat(destination.getValue()).isEqualTo(this.root.resolve("downloads/report.pdf"));
	}

	@Test
	void uploadsAContainedFileAndReturnsItsPresignedUrl() throws Exception {
		Path source = Files.writeString(this.root.resolve("upload.txt"), "content");
		when(this.s3.putObject(any(PutObjectRequest.class), any(Path.class)))
			.thenReturn(PutObjectResponse.builder().eTag("etag").build());
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://example.test/reports/docs/upload.txt?signature=safe"));
		when(this.presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		S3OperationResult result = service(false).uploadObject("docs/upload.txt", "reports", "upload.txt");

		assertThat(result).isEqualTo(new S3OperationResult(true, "reports", "docs/upload.txt", "Uploaded object",
				"https://example.test/reports/docs/upload.txt?signature=safe"));
		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<Path> uploadedSource = ArgumentCaptor.forClass(Path.class);
		verify(this.s3).putObject(request.capture(), uploadedSource.capture());
		assertThat(request.getValue().bucket()).isEqualTo("reports");
		assertThat(request.getValue().key()).isEqualTo("docs/upload.txt");
		assertThat(uploadedSource.getValue()).isEqualTo(source);
	}

	@Test
	void checksUploadPoliciesAndPathBeforeCallingAws() throws Exception {
		S3AccessPolicy access = mock(S3AccessPolicy.class);
		LocalPathPolicy paths = mock(LocalPathPolicy.class);
		Path source = this.root.resolve("upload.txt");
		when(paths.resolveUpload("upload.txt")).thenReturn(source);
		when(this.s3.putObject(any(PutObjectRequest.class), any(Path.class)))
			.thenReturn(PutObjectResponse.builder().build());
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://example.test/object"));
		when(this.presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
		S3Service service = new S3Service(this.s3, this.presigner, properties(false), paths, access);

		service.uploadObject("docs/upload.txt", "reports", "upload.txt");

		InOrder order = inOrder(access, paths, this.s3);
		order.verify(access).requireMutation("uploadObject");
		order.verify(access).requireObject("reports", "docs/upload.txt");
		order.verify(paths).resolveUpload("upload.txt");
		order.verify(this.s3).putObject(any(PutObjectRequest.class), any(Path.class));
	}

	@Test
	void createsNormalizedZeroByteDirectoryMarker() throws Exception {
		when(this.s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		S3OperationResult result = service(false).createDirectory("docs/archive", "reports");

		assertThat(result).isEqualTo(new S3OperationResult(true, "reports", "docs/archive/",
				"Created directory marker", null));
		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
		verify(this.s3).putObject(request.capture(), body.capture());
		assertThat(request.getValue().bucket()).isEqualTo("reports");
		assertThat(request.getValue().key()).isEqualTo("docs/archive/");
		assertThat(body.getValue().optionalContentLength()).hasValue(0L);
	}

	@Test
	void doesNotAddASecondDirectoryMarkerSlash() throws Exception {
		when(this.s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		S3OperationResult result = service(false).createDirectory("docs/archive/", "reports");

		assertThat(result.key()).isEqualTo("docs/archive/");
	}

	@Test
	void translatesWriteSideAwsFailuresAndDoesNotPresignFailedUploads() throws Exception {
		Files.writeString(this.root.resolve("upload.txt"), "content");
		S3Exception.Builder failureBuilder = S3Exception.builder();
		failureBuilder.statusCode(403);
		failureBuilder.message("secret=must-not-leak");
		S3Exception failure = (S3Exception) failureBuilder.build();
		when(this.s3.putObject(any(PutObjectRequest.class), any(Path.class))).thenThrow(failure);

		assertThatThrownBy(() -> service(false).uploadObject("docs/upload.txt", "reports", "upload.txt"))
			.isInstanceOfSatisfying(S3ToolException.class, exception -> {
				assertThat(exception.code()).isEqualTo(S3ErrorCode.ACCESS_DENIED);
				assertThat(exception).hasCause(failure);
			})
			.hasMessageContaining("reports/docs/upload.txt")
			.hasMessageNotContaining("secret");
		verifyNoInteractions(this.presigner);
	}

	private S3Service service(boolean readOnly) throws Exception {
		S3Properties properties = properties(readOnly);
		return new S3Service(this.s3, this.presigner, properties, new LocalPathPolicy(properties),
				new S3AccessPolicy(properties));
	}

	private S3Properties properties(boolean readOnly) {
		return new S3Properties(URI.create("https://s3.example.test"), "us-west-2", null, null, null, false,
				Duration.ofMinutes(15), this.root, readOnly, List.of(), List.of(), false);
	}

}
