package ru.ticketswap.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "ticketswap")
public class TicketSwapProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Storage storage = new Storage();
    private final Mail mail = new Mail();

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public Storage getStorage() {
        return storage;
    public Mail getMail() {
        return mail;
    }

    public static class Security {

        private final Jwt jwt = new Jwt();
        private final TwoFactor twoFactor = new TwoFactor();
        private final PasswordReset passwordReset = new PasswordReset();

        public Jwt getJwt() {
            return jwt;
        }

        public TwoFactor getTwoFactor() {
            return twoFactor;
        }

        public PasswordReset getPasswordReset() {
            return passwordReset;
        }

        public static class Jwt {

            @NotBlank
            private String secret;

            @Min(60_000)
            private long expirationMs;

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }

            public long getExpirationMs() {
                return expirationMs;
            }

            public void setExpirationMs(long expirationMs) {
                this.expirationMs = expirationMs;
            }
        }

        public static class TwoFactor {

            @Min(60_000)
            private long codeExpirationMs;

            @Min(1)
            private int maxAttempts;

            public long getCodeExpirationMs() {
                return codeExpirationMs;
            }

            public void setCodeExpirationMs(long codeExpirationMs) {
                this.codeExpirationMs = codeExpirationMs;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }
        }

        public static class PasswordReset {

            @Min(60_000)
            private long tokenExpirationMs;

            public long getTokenExpirationMs() {
                return tokenExpirationMs;
            }

            public void setTokenExpirationMs(long tokenExpirationMs) {
                this.tokenExpirationMs = tokenExpirationMs;
            }
        }
    }

    public static class Cors {

        private List<String> allowedOrigins;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Storage {

        private final S3 s3 = new S3();

        public S3 getS3() {
            return s3;
        }

        public static class S3 {

            @NotBlank
            private String endpoint;

            @NotBlank
            private String accessKey;

            @NotBlank
            private String secretKey;

            @NotBlank
            private String bucket;

            @Min(1)
            private int presignedGetExpiryMinutes = 15;

            private boolean autoCreateBucket = true;

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public int getPresignedGetExpiryMinutes() {
                return presignedGetExpiryMinutes;
            }

            public void setPresignedGetExpiryMinutes(int presignedGetExpiryMinutes) {
                this.presignedGetExpiryMinutes = presignedGetExpiryMinutes;
            }

            public boolean isAutoCreateBucket() {
                return autoCreateBucket;
            }

            public void setAutoCreateBucket(boolean autoCreateBucket) {
                this.autoCreateBucket = autoCreateBucket;
            }
    public static class Mail {

        @NotBlank
        private String from;

        @NotBlank
        private String passwordResetUrlBase;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getPasswordResetUrlBase() {
            return passwordResetUrlBase;
        }

        public void setPasswordResetUrlBase(String passwordResetUrlBase) {
            this.passwordResetUrlBase = passwordResetUrlBase;
        }
    }
}
