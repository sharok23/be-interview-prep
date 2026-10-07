package com.project.api.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    protected ShortUrl() {
    }

    public ShortUrl(User owner, String code, String originalUrl, Instant expiresAt) {
        this.owner = owner;
        this.code = code;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.dedupeKey = dedupeKey(owner.getUsername(), originalUrl, expiresAt);
        this.createdAt = Instant.now();
    }

    public static String dedupeKey(String ownerUsername, String originalUrl, Instant expiresAt) {
        String source = ownerUsername + "|" + originalUrl + "|" + (expiresAt == null ? "none" : expiresAt.toString());
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

    public User getOwner() {
        return owner;
    }
}
