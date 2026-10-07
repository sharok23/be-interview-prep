package com.project.api.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final ShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;

    public ShortUrlService(ShortUrlRepository repository, ShortCodeGenerator codeGenerator) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
    }

    public record ShortenResult(ShortUrlResponse response, boolean created) {
    }

    public ShortenResult shorten(ShortenRequest request, String baseUrl) {
        String dedupeKey = ShortUrl.dedupeKey(request.url(), request.expiresAt());
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Optional<ShortUrl> existing = repository.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                return new ShortenResult(ShortUrlResponse.from(existing.get(), baseUrl), false);
            }
            try {
                ShortUrl saved = repository.saveAndFlush(
                        new ShortUrl(codeGenerator.next(), request.url(), request.expiresAt()));
                return new ShortenResult(ShortUrlResponse.from(saved, baseUrl), true);
            } catch (DataIntegrityViolationException duplicateKeyOrCode) {
                log.debug("Shorten attempt {} hit a unique constraint, retrying", attempt + 1);
            }
        }
        throw new IllegalStateException("Could not store short URL after " + MAX_ATTEMPTS + " attempts");
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
}
