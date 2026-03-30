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
    private final Mail mail = new Mail();

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

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
