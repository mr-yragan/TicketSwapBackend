package ru.ticketswap.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final int STARTUP_ATTEMPTS = 20;
    private static final long STARTUP_DELAY_MS = 2_000;

    @Bean("internalMinioClient")
    public MinioClient minioClient(TicketSwapProperties properties) {
        TicketSwapProperties.Storage.S3 s3 = properties.getStorage().getS3();
        return MinioClient.builder()
                .endpoint(s3.getEndpoint())
                .credentials(s3.getAccessKey(), s3.getSecretKey())
                .region(s3.getRegion())
                .build();
    }

    @Bean("publicPresignMinioClient")
    public MinioClient publicPresignMinioClient(TicketSwapProperties properties) {
        TicketSwapProperties.Storage.S3 s3 = properties.getStorage().getS3();
        String endpoint = (s3.getPublicEndpoint() == null || s3.getPublicEndpoint().isBlank())
                ? s3.getEndpoint()
                : s3.getPublicEndpoint();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(s3.getAccessKey(), s3.getSecretKey())
                .region(s3.getRegion())
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(@Qualifier("internalMinioClient") MinioClient minioClient, TicketSwapProperties properties) {
        return args -> {
            TicketSwapProperties.Storage.S3 s3 = properties.getStorage().getS3();
            Exception lastException = null;

            for (int attempt = 1; attempt <= STARTUP_ATTEMPTS; attempt++) {
                try {
                    boolean exists = minioClient.bucketExists(
                            BucketExistsArgs.builder()
                                    .bucket(s3.getBucket())
                                    .build()
                    );

                    if (!exists && s3.isAutoCreateBucket()) {
                        minioClient.makeBucket(
                                MakeBucketArgs.builder()
                                        .bucket(s3.getBucket())
                                        .build()
                        );
                    }
                    return;
                } catch (Exception ex) {
                    lastException = ex;
                    try {
                        Thread.sleep(STARTUP_DELAY_MS);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Инициализация bucket MinIO была прервана", interruptedException);
                    }
                }
            }

            throw new IllegalStateException("Не удалось инициализировать bucket MinIO", lastException);
        };
    }
}