package com.project.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.project.api.enums.Role;
import com.project.api.model.CurrentUser;
import com.project.api.repository.UserRepository;
import com.project.api.support.TestUsers;

@SpringBootTest
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registerCreatesUserRoleAndNeverReturnsThePassword() throws Exception {
        String username = uniqueUsername();

        register(username, "Str0ng-pass")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/me"))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String storedHash = userRepository.findByUsername(username).orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo("Str0ng-pass");
    }

    @Test
    void registerReturns409ForTakenUsernameIgnoringCase() throws Exception {
        String username = uniqueUsername();
        register(username, "Str0ng-pass").andExpect(status().isCreated());

        register(username.toUpperCase(), "Str0ng-pass")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registerReturns400WithFieldErrors() throws Exception {
        register("a", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be between 8 and 72 characters"));
    }

    @Test
    void loginReturnsABearerTokenThatExpiresIn15Minutes() throws Exception {
        String username = uniqueUsername();
        register(username, "Str0ng-pass");

        String body = login(username, "Str0ng-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginReturns401ForWrongPasswordOrUnknownUser() throws Exception {
        String username = uniqueUsername();
        register(username, "Str0ng-pass");

        login(username, "wrong-pass")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        login(uniqueUsername(), "Str0ng-pass")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void registerReturns400ForPasswordOver72BytesEvenIfUnder72Characters() throws Exception {
        register(uniqueUsername(), "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("password must be at most 72 bytes"));
    }

    @Test
    void loginReturns400ForMissingFieldsOrMalformedJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("username is required"))
                .andExpect(jsonPath("$.fieldErrors.password").value("password is required"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleTokenIsIgnoredOnPublicEndpoints() throws Exception {
        String username = uniqueUsername();
        register(username, "Str0ng-pass");

        mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"Str0ng-pass\"}".formatted(username)))
                .andExpect(status().isOk());

        mvc.perform(get("/r/{code}", "nope1234").header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-garbage"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTokenReturns401AsJson() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    void invalidOrExpiredTokenReturns401() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String username = testUsers.create(Role.USER).user().getUsername();
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(past)
                .expiresAt(past.plus(Duration.ofMinutes(15)))
                .claim(CurrentUser.ROLES_CLAIM, List.of("USER"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userCannotAccessTheAdminEndpoint() throws Exception {
        TestUsers.Account user = testUsers.create(Role.USER);

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, user.authorization()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @Test
    void adminCanListAllUsers() throws Exception {
        TestUsers.Account admin = testUsers.create(Role.ADMIN);
        TestUsers.Account user = testUsers.create(Role.USER);

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem(user.user().getUsername())))
                .andExpect(jsonPath("$[*].username", hasItem(admin.user().getUsername())));
    }

    @Test
    void adminEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(String username, String pass) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, pass)));
    }

    private ResultActions login(String username, String pass) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, pass)));
    }

    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
