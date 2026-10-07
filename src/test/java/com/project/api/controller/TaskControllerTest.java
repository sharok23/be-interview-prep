package com.project.api.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.project.api.enums.Role;
import com.project.api.support.TestUsers;

@SpringBootTest
class TaskControllerTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired
    private TestUsers testUsers;

    private TestUsers.Account account;
    private MockMvc mvc;

    @BeforeEach
    void signIn() {
        account = testUsers.create(Role.USER);
        mvc = testUsers.mockMvcAs(account);
    }

    @Test
    void createReturns201WithDefaultsAndLocation() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Write report", null, TOMORROW)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Write report"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value(TOMORROW.toString()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createReturns400WithFieldErrorsForEveryInvalidField() throws Exception {
        String body = """
                {"title": "%s", "dueDate": "%s"}
                """.formatted("x".repeat(101), LocalDate.now().minusDays(1));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(jsonPath("$.fieldErrors.title").value("title must be at most 100 characters"))
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("dueDate cannot be in the past"));
    }

    @Test
    void createReturns400WhenTitleMissing() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("title is required"));
    }

    @Test
    void createReturns400WithFieldErrorForUnknownStatusOrBadDate() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"t\", \"status\": \"LATER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("status must be one of TODO, IN_PROGRESS, DONE"));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"t\", \"dueDate\": \"2030-13-45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("dueDate has an invalid LocalDate value"));
    }

    @Test
    void createReturns400ForMalformedJsonAndTooLongDescription() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("t", "d".repeat(1001), TOMORROW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").value("description must be at most 1000 characters"));
    }

    @Test
    void createTrimsTitleBeforeCheckingLength() throws Exception {
        String title = "a".repeat(100);

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  " + title + "  ", null, TOMORROW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void getReturnsTaskAndUnknownIdReturns404() throws Exception {
        long id = create(uniqueTitle(), "TODO");

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(get("/api/tasks/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task 999999 not found"));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        String doneTitle = uniqueTitle();
        String todoTitle = uniqueTitle();
        create(doneTitle, "DONE");
        create(todoTitle, "TODO");

        mvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("DONE"))))
                .andExpect(jsonPath("$[*].title", hasItem(doneTitle)))
                .andExpect(jsonPath("$[*].title", not(hasItem(todoTitle))));
    }

    @Test
    void listReturns400ForUnknownStatus() throws Exception {
        mvc.perform(get("/api/tasks").param("status", "LATER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status must be one of TODO, IN_PROGRESS, DONE"));
    }

    @Test
    void updateReplacesFieldsAndKeepsStatusWhenOmitted() throws Exception {
        long id = create(uniqueTitle(), "IN_PROGRESS");

        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Renamed", "new description", TOMORROW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.description").value("new description"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateReturns404ForUnknownIdAnd400ForInvalidBody() throws Exception {
        mvc.perform(put("/api/tasks/{id}", 999_999).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Valid", null, TOMORROW)))
                .andExpect(status().isNotFound());

        long id = create(uniqueTitle(), "TODO");
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Valid", null, LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueDate").value("dueDate cannot be in the past"));
    }

    @Test
    void deleteReturns204ThenTaskIsGone() throws Exception {
        long id = create(uniqueTitle(), "TODO");

        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void nonNumericIdAndUnknownUrlUseTheSameErrorFormat() throws Exception {
        mvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));

        mvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/nothing-here"));

        mvc.perform(patch("/api/tasks/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));

        mvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    private long create(String title, String status) throws Exception {
        String body = """
                {"title": "%s", "status": "%s", "dueDate": "%s"}
                """.formatted(title, status, TOMORROW);
        String response = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private static String json(String title, String description, LocalDate dueDate) {
        String desc = description == null ? "null" : "\"" + description + "\"";
        return """
                {"title": "%s", "description": %s, "dueDate": "%s"}
                """.formatted(title, desc, dueDate);
    }

    private static String uniqueTitle() {
        return "task-" + UUID.randomUUID();
    }
}
