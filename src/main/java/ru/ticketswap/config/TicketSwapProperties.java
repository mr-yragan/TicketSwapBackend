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

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Security {

        private final Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
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
}
