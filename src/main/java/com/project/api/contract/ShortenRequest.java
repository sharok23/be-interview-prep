package com.project.api.contract;

import java.time.Instant;

import com.project.api.model.ShortUrl;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = ShortUrl.URL_MAX, message = "url must be at most {max} characters")
        @Pattern(regexp = "https?://[A-Za-z0-9.-]+(:\\d{1,5})?([/?#][A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?",
                message = "url must be a valid http or https URL")
        String url,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {

    public ShortenRequest {
        url = url == null ? null : url.trim();
    }
}
