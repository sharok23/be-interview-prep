package com.project.api.contract;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password) {

    public LoginRequest {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
