package com.project.api.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.project.api.model.ShortUrl;

@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(ShortUrl.CODE_LENGTH);
        for (int i = 0; i < ShortUrl.CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
