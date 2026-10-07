package com.project.api.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.project.api.contract.ShortUrlResponse;
import com.project.api.contract.ShortenRequest;
import com.project.api.contract.UrlStatsResponse;
import com.project.api.service.ShortUrlService;
import com.project.api.service.ShortUrlService.ShortenResult;

import jakarta.validation.Valid;

@RestController
public class ShortUrlController {

    private final ShortUrlService service;

    public ShortUrlController(ShortUrlService service) {
        this.service = service;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortUrlResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        ShortenResult result = service.shorten(request, baseUrl);
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create(result.response().shortUrl())).body(result.response());
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, service.resolve(code)).build();
    }

    @GetMapping("/api/urls/{code}/stats")
    public UrlStatsResponse stats(@PathVariable String code) {
        return service.stats(code);
    }
}
