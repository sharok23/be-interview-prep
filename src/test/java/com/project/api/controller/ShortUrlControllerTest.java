package com.project.api.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.project.api.model.ShortUrl;
import com.project.api.repository.ShortUrlRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void shortenReturns201WithUrlSafeCodeOfAtMost8Chars() throws Exception {
        String url = uniqueUrl();

        shorten(url, null)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value(matchesPattern("[A-Za-z0-9]{1,8}")))
                .andExpect(jsonPath("$.shortUrl").value(matchesPattern("http://localhost/r/[A-Za-z0-9]{1,8}")))
                .andExpect(jsonPath("$.originalUrl").value(url))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shorteningTheSameUrlTwiceReturnsTheExistingCode() throws Exception {
        String url = uniqueUrl();
        String first = codeOf(shorten(url, null).andExpect(status().isCreated()));

        shorten(url, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(first));
    }

    @Test
    void sameUrlWithADifferentExpiryGetsANewCode() throws Exception {
        String url = uniqueUrl();
        String withoutExpiry = codeOf(shorten(url, null));
        Instant expiry = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        shorten(url, expiry)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(not(withoutExpiry)))
                .andExpect(jsonPath("$.expiresAt").value(expiry.toString()));
    }

    @Test
    void shortenReturns400ForInvalidUrlOrPastExpiry() throws Exception {
        for (String bad : new String[] {"not a url", "ftp://example.com/file", "http://", "javascript:alert(1)"}) {
            shorten(bad, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.url").value("url must be a valid http or https URL"));
        }

        shorten(uniqueUrl(), Instant.now().minus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expiresAt").value("expiresAt must be in the future"));

        mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.url").value("url is required"));
    }

    @Test
    void redirectReturns302AndCountsEveryVisit() throws Exception {
        String url = uniqueUrl();
        String code = codeOf(shorten(url, null));

        mvc.perform(get("/r/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", url));
        mvc.perform(get("/r/{code}", code)).andExpect(status().isFound());

        mvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value(url))
                .andExpect(jsonPath("$.visits").value(2))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/r/{code}", "nope1234"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short URL nope1234 not found"));

        mvc.perform(get("/api/urls/{code}/stats", "nope1234"))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredCodeReturns410AndIsNotCounted() throws Exception {
        String code = "exp" + UUID.randomUUID().toString().substring(0, 5);
        repository.save(new ShortUrl(code, uniqueUrl(), Instant.now().minus(1, ChronoUnit.MINUTES)));

        mvc.perform(get("/r/{code}", code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("Short URL %s has expired".formatted(code)));

        mvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visits").value(0));
    }

    private ResultActions shorten(String url, Instant expiresAt) throws Exception {
        String expiry = expiresAt == null ? "null" : "\"" + expiresAt + "\"";
        String body = """
                {"url": "%s", "expiresAt": %s}
                """.formatted(url, expiry);
        return mvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String codeOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.code");
    }

    private static String uniqueUrl() {
        return "https://example.com/articles/" + UUID.randomUUID();
    }
}
