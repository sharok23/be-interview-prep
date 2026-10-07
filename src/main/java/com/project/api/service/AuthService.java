package com.project.api.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.api.contract.LoginRequest;
import com.project.api.contract.RegisterRequest;
import com.project.api.contract.TokenResponse;
import com.project.api.contract.UserResponse;
import com.project.api.enums.Role;
import com.project.api.exception.ConflictException;
import com.project.api.exception.UnauthorizedException;
import com.project.api.model.User;
import com.project.api.repository.UserRepository;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw usernameTaken(request.username());
        }
        try {
            User saved = users.saveAndFlush(
                    new User(request.username(), passwordEncoder.encode(request.password()), Role.USER));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException raceLost) {
            throw usernameTaken(request.username());
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return tokenService.issue(user);
    }

    private static ConflictException usernameTaken(String username) {
        return new ConflictException("Username '%s' is already taken".formatted(username));
    }
}
