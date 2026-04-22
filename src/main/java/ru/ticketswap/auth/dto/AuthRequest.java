package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 32, message = "Login must be between 3 and 32 characters")
        @Pattern(
                regexp = "^(?!.*@)(?![0-9-]{5,32}$)[A-Za-z0-9_.-]+$",
                message = "Login may contain only letters, digits, underscore, dot and dash, and must not look like an email or phone number"
        )
        String login,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
) {
}
