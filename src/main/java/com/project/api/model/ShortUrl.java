package com.project.api.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ShortUrl {

    public static final int CODE_LENGTH = 7;
    public static final int URL_MAX = 2048;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false, length = URL_MAX)
    private String originalUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant expiresAt;

    @Column(nullable = false, unique = true, length = 64)
    private String dedupeKey;

    @Column(nullable = false)
    private long visits;

    protected ShortUrl() {
    }

    public ShortUrl(String code, String originalUrl, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.dedupeKey = dedupeKey(originalUrl, expiresAt);
        this.createdAt = Instant.now();
    }

    public static String dedupeKey(String originalUrl, Instant expiresAt) {
        String source = originalUrl + "|" + (expiresAt == null ? "none" : expiresAt.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getVisits() {
        return visits;
    }
}
