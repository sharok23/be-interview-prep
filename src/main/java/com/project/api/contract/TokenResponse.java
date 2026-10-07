package com.project.api.contract;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt) {
}
