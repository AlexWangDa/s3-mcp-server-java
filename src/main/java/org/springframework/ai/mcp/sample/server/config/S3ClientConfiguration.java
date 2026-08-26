package org.springframework.ai.mcp.sample.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3ClientConfiguration {

	@Bean
	public AwsCredentialsProvider credentialsProvider(S3Properties properties) {
		if (!StringUtils.hasText(properties.accessKey())) {
			return DefaultCredentialsProvider.create();
		}
		AwsCredentials credentials = StringUtils.hasText(properties.sessionToken())
				? AwsSessionCredentials.create(properties.accessKey(), properties.secretKey(), properties.sessionToken())
				: AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());
		return StaticCredentialsProvider.create(credentials);
	}

	@Bean
	public Region s3Region(S3Properties properties) {
		return StringUtils.hasText(properties.region()) ? Region.of(properties.region())
				: DefaultAwsRegionProviderChain.builder().build().getRegion();
	}

	@Bean(destroyMethod = "close")
	public S3Client s3Client(S3Properties properties, Region s3Region, AwsCredentialsProvider credentialsProvider) {
		S3ClientBuilder builder = S3Client.builder()
			.region(s3Region)
			.credentialsProvider(credentialsProvider)
			.forcePathStyle(properties.pathStyleAccess());
		if (properties.endpoint() != null) {
			builder.endpointOverride(properties.endpoint());
		}
		return builder.build();
	}

	@Bean(destroyMethod = "close")
	public S3Presigner s3Presigner(S3Properties properties, Region s3Region,
			AwsCredentialsProvider credentialsProvider) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(s3Region)
			.credentialsProvider(credentialsProvider)
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build());
		if (properties.endpoint() != null) {
			builder.endpointOverride(properties.endpoint());
		}
		return builder.build();
	}
}
