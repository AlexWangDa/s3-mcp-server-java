package org.springframework.ai.mcp.sample.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.sample.server.config.S3Properties;
import org.springframework.ai.mcp.sample.server.entity.S3Bucket;
import org.springframework.ai.mcp.sample.server.entity.S3ListObjectsResult;
import org.springframework.ai.mcp.sample.server.entity.S3Object;
import org.springframework.ai.mcp.sample.server.entity.S3ObjectMetadata;
import org.springframework.ai.mcp.sample.server.exception.S3ErrorCode;
import org.springframework.ai.mcp.sample.server.exception.S3ToolException;
import org.springframework.ai.mcp.sample.server.security.LocalPathPolicy;
import org.springframework.ai.mcp.sample.server.security.S3AccessPolicy;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectStorageClass;
import software.amazon.awssdk.services.s3.model.Owner;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceReadTest {

	@Mock
	private S3Client s3;

	@Mock
	private S3Presigner presigner;

	@TempDir
	Path root;

	@Test
	void filtersDeniedBucketsAndMapsAllowedBucketsToRepositoryRecords() throws Exception {
		Instant created = Instant.parse("2026-08-26T01:02:03Z");
		when(this.s3.listBuckets()).thenReturn(ListBucketsResponse.builder()
			.owner(Owner.builder().displayName("test-owner").id("owner-id").build())
			.buckets(Bucket.builder().name("allowed").creationDate(created).build(),
					Bucket.builder().name("denied").creationDate(created.plusSeconds(1)).build())
			.build());

		List<S3Bucket> result = service(true, List.of("allowed"), List.of()).getBucketList();

		assertThat(result).containsExactly(new S3Bucket("allowed", "test-owner", created, null));
	}

	@Test
	void returnsBucketInformationWithItsLocation() throws Exception {
		Instant created = Instant.parse("2026-08-26T01:02:03Z");
		when(this.s3.listBuckets()).thenReturn(ListBucketsResponse.builder()
			.owner(Owner.builder().displayName("test-owner").build())
			.buckets(Bucket.builder().name("reports").creationDate(created).build())
			.build());
		when(this.s3.getBucketLocation(anyLocationRequest())).thenReturn(GetBucketLocationResponse.builder()
			.locationConstraint("us-west-2")
			.build());

		S3Bucket result = service(true, List.of("reports"), List.of()).getBucketInfo("reports");

		assertThat(result).isEqualTo(new S3Bucket("reports", "test-owner", created, "us-west-2"));
	}

	@Test
	void rejectsDeniedBucketBeforeCallingAws() throws Exception {
		S3Service service = service(true, List.of("allowed"), List.of());

		assertThatThrownBy(() -> service.getBucketInfo("denied"))
			.isInstanceOfSatisfying(S3ToolException.class,
					exception -> assertThat(exception.code()).isEqualTo(S3ErrorCode.ACCESS_DENIED));
		verifyNoInteractions(this.s3, this.presigner);
	}

	@Test
	void reportsAllowedBucketAsNotFoundWithoutRequestingItsLocation() throws Exception {
		when(this.s3.listBuckets()).thenReturn(ListBucketsResponse.builder().build());

		assertThatThrownBy(() -> service(true, List.of("missing"), List.of()).getBucketInfo("missing"))
			.isInstanceOfSatisfying(S3ToolException.class,
					exception -> assertThat(exception.code()).isEqualTo(S3ErrorCode.NOT_FOUND))
			.hasMessageContaining("missing");
		verify(this.s3, never()).getBucketLocation(anyLocationRequest());
	}

	@Test
	void listsObjectsWithCompatibleDefaultsAndRepositoryOwnedResults() throws Exception {
		Instant modified = Instant.parse("2026-08-26T04:05:06Z");
		when(this.s3.listObjects(any(ListObjectsRequest.class))).thenReturn(ListObjectsResponse.builder()
			.contents(software.amazon.awssdk.services.s3.model.S3Object.builder()
				.key("docs/report.pdf")
				.storageClass(ObjectStorageClass.STANDARD)
				.eTag("etag-1")
				.lastModified(modified)
				.size(42L)
				.build())
			.commonPrefixes(CommonPrefix.builder().prefix("docs/archive/").build())
			.nextMarker("docs/report.pdf")
			.build());

		S3ListObjectsResult result = service(true, List.of(), List.of())
			.listObjects("reports", "start", "docs/", null, null);

		assertThat(result).isEqualTo(new S3ListObjectsResult(
				List.of(new S3Object("docs/report.pdf", "STANDARD", "etag-1", modified, 42L)),
				List.of("docs/archive/"), "docs/report.pdf"));
		ArgumentCaptor<ListObjectsRequest> request = ArgumentCaptor.forClass(ListObjectsRequest.class);
		verify(this.s3).listObjects(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("reports");
		assertThat(request.getValue().marker()).isEqualTo("start");
		assertThat(request.getValue().prefix()).isEqualTo("docs/");
		assertThat(request.getValue().delimiter()).isEqualTo("/");
		assertThat(request.getValue().maxKeys()).isEqualTo(100);
	}

	@Test
	void rejectsOutOfRangeListLimitsBeforeCallingAws() throws Exception {
		S3Service service = service(true, List.of(), List.of());

		assertThatThrownBy(() -> service.listObjects("reports", null, null, null, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("1 and 100");
		assertThatThrownBy(() -> service.listObjects("reports", null, null, null, 101))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("1 and 100");
		verifyNoInteractions(this.s3, this.presigner);
	}

	@Test
	void retrievesObjectMetadataWithHeadObject() throws Exception {
		Instant modified = Instant.parse("2026-08-26T07:08:09Z");
		when(this.s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
			.contentLength(123L)
			.eTag("etag-2")
			.contentType("application/pdf")
			.lastModified(modified)
			.metadata(Map.of("source", "test"))
			.build());

		S3ObjectMetadata result = service(true, List.of(), List.of("docs/"))
			.getObjectMetadata("docs/report.pdf", "reports");

		assertThat(result).isEqualTo(new S3ObjectMetadata(123L, "etag-2", "application/pdf", modified,
				Map.of("source", "test")));
		ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
		verify(this.s3).headObject(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("reports");
		assertThat(request.getValue().key()).isEqualTo("docs/report.pdf");
	}

	@Test
	void generatesGetUrlUsingConfiguredPresignDuration() throws Exception {
		PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://example.test/reports/docs/report.pdf?signature=safe"));
		when(this.presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		String result = service(true, List.of(), List.of("docs/"))
			.generatePresignedUrl("docs/report.pdf", "reports");

		assertThat(result).isEqualTo("https://example.test/reports/docs/report.pdf?signature=safe");
		ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(this.presigner).presignGetObject(request.capture());
		assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
		assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("reports");
		assertThat(request.getValue().getObjectRequest().key()).isEqualTo("docs/report.pdf");
		verifyNoInteractions(this.s3);
	}

	@Test
	void rejectsDeniedObjectBeforeCallingAwsOrPresigner() throws Exception {
		S3Service service = service(true, List.of(), List.of("public/"));

		assertThatThrownBy(() -> service.getObjectMetadata("private/report.pdf", "reports"))
			.isInstanceOfSatisfying(S3ToolException.class,
					exception -> assertThat(exception.code()).isEqualTo(S3ErrorCode.ACCESS_DENIED));
		verifyNoInteractions(this.s3, this.presigner);
	}

	@Test
	void translatesNoSuchKeyWithoutLeakingAwsMessage() throws Exception {
		NoSuchKeyException failure = NoSuchKeyException.builder()
			.statusCode(404)
			.message("secret-token=must-not-leak")
			.build();
		when(this.s3.headObject(any(HeadObjectRequest.class))).thenThrow(failure);

		assertThatThrownBy(() -> service(true, List.of(), List.of()).getObjectMetadata("missing.txt", "reports"))
			.isInstanceOfSatisfying(S3ToolException.class, exception -> {
				assertThat(exception.code()).isEqualTo(S3ErrorCode.NOT_FOUND);
				assertThat(exception).hasCause(failure);
			})
			.hasMessageContaining("reports/missing.txt")
			.hasMessageNotContaining("secret-token");
	}

	@Test
	void translatesForbiddenAndOtherAwsFailuresToStableCodes() throws Exception {
		S3Exception.Builder forbiddenBuilder = S3Exception.builder();
		forbiddenBuilder.statusCode(403);
		forbiddenBuilder.message("credential=must-not-leak");
		S3Exception forbidden = (S3Exception) forbiddenBuilder.build();
		when(this.s3.listObjects(any(ListObjectsRequest.class))).thenThrow(forbidden);
		S3Service service = service(true, List.of(), List.of());

		assertThatThrownBy(() -> service.listObjects("reports", null, null, null, 100))
			.isInstanceOfSatisfying(S3ToolException.class, exception -> {
				assertThat(exception.code()).isEqualTo(S3ErrorCode.ACCESS_DENIED);
				assertThat(exception).hasCause(forbidden);
			})
			.hasMessageContaining("reports")
			.hasMessageNotContaining("credential");

		S3Exception.Builder otherBuilder = S3Exception.builder();
		otherBuilder.statusCode(500);
		otherBuilder.message("session=must-not-leak");
		S3Exception other = (S3Exception) otherBuilder.build();
		when(this.s3.headObject(any(HeadObjectRequest.class))).thenThrow(other);
		assertThatThrownBy(() -> service.getObjectMetadata("report.pdf", "reports"))
			.isInstanceOfSatisfying(S3ToolException.class, exception -> {
				assertThat(exception.code()).isEqualTo(S3ErrorCode.S3_ERROR);
				assertThat(exception).hasCause(other);
			})
			.hasMessageNotContaining("session");
	}

	@Test
	void resultCollectionsAreDefensiveCopies() {
		List<S3Object> objects = new ArrayList<>();
		List<String> folders = new ArrayList<>();
		Map<String, String> metadata = new HashMap<>();
		S3ListObjectsResult listing = new S3ListObjectsResult(objects, folders, null);
		S3ObjectMetadata objectMetadata = new S3ObjectMetadata(0, null, null, null, metadata);

		objects.add(new S3Object("later", null, null, null, 0));
		folders.add("later/");
		metadata.put("later", "value");

		assertThat(listing.objects()).isEmpty();
		assertThat(listing.folders()).isEmpty();
		assertThat(objectMetadata.userMetadata()).isEmpty();
		assertThatThrownBy(() -> listing.folders().add("forbidden/")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> objectMetadata.userMetadata().put("forbidden", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	private S3Service service(boolean readOnly, List<String> allowedBuckets, List<String> allowedPrefixes)
			throws Exception {
		S3Properties properties = new S3Properties(URI.create("https://s3.example.test"), "us-west-2", null, null,
				null, false, Duration.ofMinutes(15), this.root, readOnly, allowedBuckets, allowedPrefixes, false);
		return new S3Service(this.s3, this.presigner, properties, new LocalPathPolicy(properties),
				new S3AccessPolicy(properties));
	}

	@SuppressWarnings("unchecked")
	private Consumer<GetBucketLocationRequest.Builder> anyLocationRequest() {
		return any(Consumer.class);
	}

}
