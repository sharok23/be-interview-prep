package com.project.api.contract;

import java.time.Instant;

import com.project.api.model.ShortUrl;

public record ShortUrlResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt) {

    public static ShortUrlResponse from(ShortUrl shortUrl, String baseUrl) {
        return new ShortUrlResponse(shortUrl.getCode(), baseUrl + "/r/" + shortUrl.getCode(),
                shortUrl.getOriginalUrl(), shortUrl.getCreatedAt(), shortUrl.getExpiresAt());
    }
}
