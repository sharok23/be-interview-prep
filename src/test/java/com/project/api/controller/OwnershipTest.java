package com.project.api.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.project.api.enums.Role;
import com.project.api.support.TestUsers;

@SpringBootTest
class OwnershipTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestUsers testUsers;

    private MockMvc mvc;
    private TestUsers.Account alice;
    private TestUsers.Account bob;
    private TestUsers.Account admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = testUsers.create(Role.USER);
        bob = testUsers.create(Role.USER);
        admin = testUsers.create(Role.ADMIN);
    }

    @Test
    void taskAndUrlEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"https://a.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/urls/abc/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersOnlySeeTheirOwnTasksAndAdminSeesAll() throws Exception {
        long id = createTask(alice);

        mvc.perform(get("/api/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(alice.user().getUsername()));
        mvc.perform(get("/api/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));

        mvc.perform(get("/api/tasks/{id}", id).header(HttpHeaders.AUTHORIZATION, admin.authorization()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, admin.authorization()))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }

    @Test
    void urlStatsAreOwnerOnlyButRedirectIsPublic() throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();
        String body = mvc.perform(post("/api/urls").header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"%s\"}".formatted(url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        mvc.perform(get("/r/{code}", code)).andExpect(status().isFound());

        mvc.perform(get("/api/urls/{code}/stats", code).header(HttpHeaders.AUTHORIZATION, alice.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visits").value(1));
        mvc.perform(get("/api/urls/{code}/stats", code).header(HttpHeaders.AUTHORIZATION, bob.authorization()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/urls/{code}/stats", code).header(HttpHeaders.AUTHORIZATION, admin.authorization()))
                .andExpect(status().isOk());
    }

    @Test
    void sameUrlShortenedByTwoUsersGivesEachTheirOwnLink() throws Exception {
        String payload = "{\"url\": \"https://example.com/%s\"}".formatted(UUID.randomUUID());

        String aliceCode = JsonPath.read(mvc.perform(post("/api/urls")
                        .header(HttpHeaders.AUTHORIZATION, alice.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.code");

        mvc.perform(post("/api/urls").header(HttpHeaders.AUTHORIZATION, bob.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(not(aliceCode)));
    }

    private long createTask(TestUsers.Account account) throws Exception {
        String body = mvc.perform(post("/api/tasks").header(HttpHeaders.AUTHORIZATION, account.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"private task\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
