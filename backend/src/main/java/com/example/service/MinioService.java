package com.example.service;

import io.minio.SetBucketPolicyArgs;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private static final org.slf4j.Logger logger =
        org.slf4j.LoggerFactory.getLogger(MinioService.class);
    @Value("${minio.bucket:profiles}")
    private String bucket;  // Fixed: 'bucket', not 'buckets'

    @Value("${minio.url}")  // Internal: http://minio:9000 in Docker
    private String minioEndpoint;

    @Value("${minio.public-host:http://localhost:9000}")  // New: Env for browser-facing URL
    private String publicHost;

    public MinioService(@Value("${minio.url}") String url,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            setPublicReadPolicy();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing MinIO bucket", e);
        }
    }

    private void setPublicReadPolicy() {
        try {
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": "*",
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                                       .bucket(bucket)
                                       .config(policy)
                                       .build());
            logger.info("✅ Bucket {} is now public-read", bucket);
        } catch (Exception e) {
            logger.warn("Could not set public-read policy (maybe already set): {}", e.getMessage());
        }
    }

    public String uploadImage(String fileName, byte[] imageBytes) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fileName)
                        .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                        .contentType("image/jpeg")
                        .build());
        return fileName;
    }

    public byte[] getObject(String bucketName, String objectName) throws Exception {
        try (var in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectName).build())) {
            return in.readAllBytes();
        }
    }

    public String getPublicUrl(String objectName) {
        return publicHost.endsWith("/")
                ? publicHost + bucket + "/" + objectName
                : publicHost + "/" + bucket + "/" + objectName;
    }
}