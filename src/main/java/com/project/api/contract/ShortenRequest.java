package com.project.api.contract;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;

import com.project.api.model.ShortUrl;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = ShortUrl.URL_MAX, message = "url must be at most {max} characters")
        @Pattern(regexp = URL_PATTERN, message = "url must be a valid http or https URL")
        String url,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {

    private static final String LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?";
    private static final String PORT = "(?::(?:6553[0-5]|655[0-2]\\d|65[0-4]\\d{2}|6[0-4]\\d{3}|[1-5]\\d{4}|\\d{1,4}))?";
    private static final String URL_PATTERN = "(?i:https?)://" + LABEL + "(?:\\." + LABEL + ")*" + PORT
            + "(?:[/?#][A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?";
    private static final java.util.regex.Pattern SCHEME_AND_HOST =
            java.util.regex.Pattern.compile("^([A-Za-z]+://[^/?#:]+)(.*)$", java.util.regex.Pattern.DOTALL);

    public ShortenRequest {
        url = url == null ? null : lowercaseSchemeAndHost(url.trim());
        expiresAt = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MILLIS);
    }

    private static String lowercaseSchemeAndHost(String url) {
        Matcher matcher = SCHEME_AND_HOST.matcher(url);
        return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) + matcher.group(2) : url;
    }
}
