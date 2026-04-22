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
    void deserializesLegacyPhoneFieldIntoIdentifier() throws Exception {
        LoginRequest request = objectMapper.readValue(
                """
                {
                  "phoneNumber": "+7 (999) 123-45-67",
                  "password": "password123"
                }
                """,
                LoginRequest.class
        );

        assertEquals("+7 (999) 123-45-67", request.identifier());
        assertEquals("password123", request.password());
    }
}
