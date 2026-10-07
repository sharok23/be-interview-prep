package com.project.api.contract;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be between {min} and {max} characters")
        @Pattern(regexp = "[A-Za-z0-9._-]*", message = "username may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between {min} and {max} characters")
        @MaxUtf8Bytes(value = 72, message = "password must be at most {value} bytes")
        String password) {

    public RegisterRequest {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
