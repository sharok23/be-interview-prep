package com.project.api.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.project.api.enums.Role;
import com.project.api.model.User;
import com.project.api.repository.UserRepository;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                       @Value("${app.admin.username}") String adminUsername,
                       @Value("${app.admin.password}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            return;
        }
        String username = adminUsername.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._-]{3,50}")) {
            throw new IllegalStateException("ADMIN_USERNAME must be 3-50 letters, digits, '.', '_' or '-'");
        }
        int passwordBytes = adminPassword.getBytes(StandardCharsets.UTF_8).length;
        if (adminPassword.length() < 8 || passwordBytes > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least 8 characters and at most 72 bytes");
        }
        if (!users.existsByUsername(username)) {
            users.save(new User(username, passwordEncoder.encode(adminPassword), Role.ADMIN));
            log.info("Created admin user '{}'", username);
        }
    }
}
