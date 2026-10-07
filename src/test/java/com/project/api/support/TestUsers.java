package com.project.api.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.project.api.enums.Role;
import com.project.api.model.CurrentUser;
import com.project.api.model.User;
import com.project.api.repository.UserRepository;
import com.project.api.service.TokenService;

@Component
public class TestUsers {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final WebApplicationContext context;

    public TestUsers(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
                     WebApplicationContext context) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.context = context;
    }

    public record Account(User user, String authorization) {

        public CurrentUser currentUser() {
            return new CurrentUser(user.getUsername(), user.getRole() == Role.ADMIN);
        }
    }

    public Account create(Role role) {
        String username = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(username, passwordEncoder.encode(UUID.randomUUID().toString()), role));
        return new Account(user, "Bearer " + tokenService.issue(user).accessToken());
    }

    public MockMvc mockMvcAs(Account account) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, account.authorization()))
                .build();
    }
}
