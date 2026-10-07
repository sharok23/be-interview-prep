package com.project.api.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api.contract.ShortUrlResponse;
import com.project.api.contract.ShortenRequest;
import com.project.api.contract.UrlStatsResponse;
import com.project.api.exception.GoneException;
import com.project.api.exception.NotFoundException;
import com.project.api.model.ShortUrl;
import com.project.api.repository.ShortUrlRepository;

@Service
public class ShortUrlService {

    private static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;

    public ShortUrlService(ShortUrlRepository repository, ShortCodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    public record ShortenResult(ShortUrlResponse response, boolean created) {
    }

    @Transactional
    public ShortenResult shorten(ShortenRequest request, String baseUrl) {
        var existing = repository.findFirstByOriginalUrlAndExpiresAt(request.url(), request.expiresAt());
        if (existing.isPresent()) {
            return new ShortenResult(ShortUrlResponse.from(existing.get(), baseUrl), false);
        }
        ShortUrl saved = repository.save(new ShortUrl(uniqueCode(), request.url(), request.expiresAt()));
        return new ShortenResult(ShortUrlResponse.from(saved, baseUrl), true);
    }

    @Transactional
    public String resolve(String code) {
        ShortUrl shortUrl = find(code);
        if (shortUrl.isExpired(Instant.now())) {
            throw new GoneException("Short URL %s has expired".formatted(code));
        }
        repository.incrementVisits(code);
        return shortUrl.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse stats(String code) {
        return UrlStatsResponse.from(find(code));
    }

    private ShortUrl find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NotFoundException("Short URL", code));
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }
}
