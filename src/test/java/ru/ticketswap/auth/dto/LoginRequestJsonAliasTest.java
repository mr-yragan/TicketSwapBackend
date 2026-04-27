package ru.ticketswap.auth.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRequestJsonAliasTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesLegacyUsernameFieldIntoIdentifier() throws Exception {
        LoginRequest request = objectMapper.readValue(
                """
                {
                  "username": "legacy-user",
                  "password": "password123"
                }
                """,
                LoginRequest.class
        );

        assertEquals("legacy-user", request.identifier());
        assertEquals("password123", request.password());
    }

    @Test
    void deserializesEmailFieldIntoIdentifier() throws Exception {
        LoginRequest request = objectMapper.readValue(
                """
                {
                  "email": "user@example.com",
                  "password": "password123"
                }
                """,
                LoginRequest.class
        );

        assertEquals("user@example.com", request.identifier());
        assertEquals("password123", request.password());
    }
}
