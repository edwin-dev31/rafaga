package com.rafaga.backend.media.infrastructure.storage;

import com.rafaga.backend.media.application.port.out.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
@Profile("!test")
public class R2ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public R2ObjectStorage(
        @Value("${media.r2.account-id:}") String accountId,
        @Value("${media.r2.access-key-id:}") String accessKeyId,
        @Value("${media.r2.secret-access-key:}") String secretAccessKey,
        @Value("${media.r2.bucket:rafaga-media}") String bucket) {

        this.bucket = bucket;

        // Fail-fast in production: R2 credentials are mandatory.
        // Without them the adapter would silently fall back to AWS default
        // credentials and could accidentally write to an AWS S3 bucket.
        boolean configured = !accountId.isBlank() && !accessKeyId.isBlank() && !secretAccessKey.isBlank();
        if (!configured) {
            throw new IllegalStateException(
                "R2 storage is not configured. Set media.r2.account-id, media.r2.access-key-id, and media.r2.secret-access-key.");
        }

        String endpoint = "https://" + accountId + ".r2.cloudflarestorage.com";
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));

        this.s3Client = S3Client.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(credentials)
            .serviceConfiguration(config -> config
                .checksumValidationEnabled(false)
            )
            .build();

        this.presigner = S3Presigner.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(credentials)
            .build();
    }

    @Override
    public ObjectStorage.PresignedUpload createUploadUrl(String uploadKey, String contentType, Duration expiry) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(uploadKey)
            .contentType(contentType)
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expiry)
            .putObjectRequest(putRequest)
            .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        URL url = presignedRequest.url();
        Instant expiresAt = Instant.now().plus(expiry);
        return new ObjectStorage.PresignedUpload(url.toString(), uploadKey, expiresAt);
    }

    @Override
    public void upload(String objectKey, byte[] data, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .build(),
            RequestBody.fromBytes(data));
    }

    @Override
    public InputStream download(String objectKey) {
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build());
        return response;
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build());
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (software.amazon.awssdk.core.exception.SdkServiceException e) {
            // HeadObject returns 404 Not Found for missing keys, not just NoSuchKeyException.
            // Catch the broader SdkServiceException to handle HTTP 404 responses.
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public ObjectStorage.Metadata getMetadata(String objectKey) {
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build());
        return new ObjectStorage.Metadata(response.contentType(), response.contentLength());
    }

    @Override
    public List<ObjectStorage.Key> listByTenantPrefix(String tenantId) {
        String prefix = "tenants/" + tenantId + "/";
        String continuationToken = null;
        List<ObjectStorage.Key> result = new ArrayList<>();

        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix);
            if (continuationToken != null) {
                builder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());

            response.contents().forEach(s3Object ->
                result.add(new ObjectStorage.Key(s3Object.key())));

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return result;
    }
}
