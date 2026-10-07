package com.mock.api.task;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired
    private MockMvc mvc;

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
    void createReturns400ForUnknownStatusOrMalformedJson() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"t\", \"status\": \"LATER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.message").value("Invalid value 'LATER' for 'status'"));
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
