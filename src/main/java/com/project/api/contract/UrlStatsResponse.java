package com.project.api.contract;

import java.time.Instant;

import com.project.api.model.ShortUrl;

public record UrlStatsResponse(
        String code,
        String originalUrl,
        long visits,
        Instant createdAt,
        Instant expiresAt) {

    public static UrlStatsResponse from(ShortUrl shortUrl) {
        return new UrlStatsResponse(shortUrl.getCode(), shortUrl.getOriginalUrl(), shortUrl.getVisits(),
                shortUrl.getCreatedAt(), shortUrl.getExpiresAt());
    }
}
