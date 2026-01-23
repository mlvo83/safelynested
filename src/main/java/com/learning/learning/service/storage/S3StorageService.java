package com.learning.learning.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * AWS S3 storage implementation.
 * Used for production deployments with cloud storage.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @PostConstruct
    public void init() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();

        this.s3Presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();

        logger.info("S3 Storage Service initialized for bucket: {} in region: {}", bucketName, region);
    }

    @Override
    public String store(MultipartFile file, String key) throws IOException {
        return store(file.getInputStream(), key, file.getContentType(), file.getSize());
    }

    @Override
    public String store(InputStream inputStream, String key, String contentType, long size) throws IOException {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256) // Enable server-side encryption
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, size));

            logger.info("File uploaded to S3: s3://{}/{}", bucketName, key);
            return key;

        } catch (S3Exception e) {
            logger.error("Failed to upload file to S3: {}", e.getMessage());
            throw new IOException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String key) throws IOException {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getRequest);

        } catch (NoSuchKeyException e) {
            throw new IOException("File not found in S3: " + key, e);
        } catch (S3Exception e) {
            logger.error("Failed to retrieve file from S3: {}", e.getMessage());
            throw new IOException("Failed to retrieve file from S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            logger.info("File deleted from S3: s3://{}/{}", bucketName, key);

        } catch (S3Exception e) {
            logger.error("Failed to delete file from S3: {}", e.getMessage());
            throw new IOException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            logger.error("Error checking if file exists in S3: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getDownloadUrl(String key, int expirationMinutes) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            logger.debug("Generated pre-signed URL for key: {} (expires in {} minutes)", key, expirationMinutes);
            return url;

        } catch (S3Exception e) {
            logger.error("Failed to generate pre-signed URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    @Override
    public String getStorageType() {
        return "s3";
    }

    /**
     * Get the S3 URI for a key
     */
    public String getS3Uri(String key) {
        return "s3://" + bucketName + "/" + key;
    }
}
