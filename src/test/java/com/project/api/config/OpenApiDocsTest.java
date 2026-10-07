package com.project.api.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OpenApiDocsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void apiDocsArePublicAndDescribeEveryFeatureWithBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("be-interview-prep API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/tasks")))
                .andExpect(jsonPath("$.paths", hasKey("/api/urls")))
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/products")))
                .andExpect(jsonPath("$.paths", hasKey("/api/orders")))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0]", hasKey("bearerAuth")))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/r/{code}'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/orders'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/tasks'].get.security").doesNotExist());
    }

    @Test
    void apiDocsShowTheRealSuccessAndErrorStatuses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/tasks'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/products'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/urls'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/urls'].post.responses", hasKey("200")))
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses", hasKey("200")))
                .andExpect(jsonPath("$.paths['/r/{code}'].get.responses", hasKey("302")))
                .andExpect(jsonPath("$.paths['/r/{code}'].get.responses", not(hasKey("200"))))
                .andExpect(jsonPath("$.paths['/api/tasks'].post.responses", not(hasKey("200"))))
                .andExpect(jsonPath("$.paths['/api/tasks/{id}'].get.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/api/tasks'].post.responses", hasKey("400")))
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['/api/tasks/{id}'].get.responses['404'].content['application/json'].schema.$ref")
                        .value(containsString("ApiError")))
                .andExpect(jsonPath("$.paths['/api/products'].post.description").value("Requires ADMIN role"));
    }

    @Test
    void swaggerUiIsServedAndTheRootRedirectsToIt() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui")));
    }
}
