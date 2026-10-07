package com.project.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
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
class ProductControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestUsers testUsers;

    private MockMvc mvc;
    private String user;
    private String admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = testUsers.create(Role.USER).authorization();
        admin = testUsers.create(Role.ADMIN).authorization();
    }

    @Test
    void listsSeededProductsWithPagingMetadata() throws Exception {
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(5)));
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mvc.perform(get("/api/products").param("size", "500").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));
    }

    @Test
    void sortsByAnyFieldInEitherDirection() throws Exception {
        String body = mvc.perform(get("/api/products").param("sort", "price,desc").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> prices = JsonPath.read(body, "$.content[*].price");
        for (int i = 1; i < prices.size(); i++) {
            assertThat(prices.get(i).doubleValue()).isLessThanOrEqualTo(prices.get(i - 1).doubleValue());
        }

        for (String field : List.of("id", "name", "category", "price", "stock", "rating", "createdAt")) {
            mvc.perform(get("/api/products").param("sort", field + ",asc").header(HttpHeaders.AUTHORIZATION, user))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void combinedFiltersWorkInASingleRequest() throws Exception {
        String category = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        createProduct("Blue Lamp", category, "20.00", 5);
        createProduct("Blue Lamp XL", category, "80.00", 5);
        createProduct("Blue Lamp Mini", category, "25.00", 0);
        createProduct("Red Chair", category, "30.00", 3);
        createProduct("Blue Lamp", "other-" + category, "22.00", 9);

        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .param("category", category.toUpperCase())
                        .param("minPrice", "10").param("maxPrice", "50")
                        .param("inStock", "true")
                        .param("q", "blue lamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Blue Lamp"))
                .andExpect(jsonPath("$.content[0].price").value(20.00));

        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user).param("category", category))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .param("category", category).param("inStock", "true"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .param("category", category).param("minPrice", "26"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .param("category", category).param("maxPrice", "25"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void nameSearchTreatsWildcardsLiterally() throws Exception {
        String category = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        createProduct("Mug 50% off", category, "5.00", 1);
        createProduct("Mug full price", category, "9.00", 1);

        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .param("category", category).param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Mug 50% off"));
    }

    @Test
    void invalidQueryParametersReturn400WithFieldErrors() throws Exception {
        mvc.perform(get("/api/products").param("sort", "secret").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").exists());
        mvc.perform(get("/api/products").param("sort", "price,sideways").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sort").value("sort direction must be asc or desc"));
        mvc.perform(get("/api/products").param("size", "0").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").value("size must be 1 or more"));
        mvc.perform(get("/api/products").param("page", "-1").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.page").value("page must be 0 or more"));
        mvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10")
                        .header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minPrice").value("minPrice must not be greater than maxPrice"));
        mvc.perform(get("/api/products").param("minPrice", "cheap").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsProductAnd404WhenUnknown() throws Exception {
        long id = createProduct("Desk", "Home", "120.00", 4);

        mvc.perform(get("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Desk"))
                .andExpect(jsonPath("$.createdAt").exists());
        mvc.perform(get("/api/products/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminCanChangeProducts() throws Exception {
        long id = createProduct("Desk", "Home", "120.00", 4);
        String body = productJson("Desk", "Home", "99.00", 4);

        mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesUpdatesAndDeletesProducts() throws Exception {
        long id = createProduct("Desk", "Home", "120.00", 4);

        mvc.perform(put("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson("Desk v2", "Home", "99.50", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Desk v2"))
                .andExpect(jsonPath("$.stock").value(0));

        mvc.perform(delete("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns400ForInvalidProduct() throws Exception {
        mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"price\": -1, \"stock\": -2, \"rating\": 7.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.category").value("category is required"))
                .andExpect(jsonPath("$.fieldErrors.price").value("price must be 0 or more"))
                .andExpect(jsonPath("$.fieldErrors.stock").value("stock must be 0 or more"))
                .andExpect(jsonPath("$.fieldErrors.rating").value("rating must be between 0 and 5"));
    }

    private long createProduct(String name, String category, String price, int stock) throws Exception {
        String body = mvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson(name, category, price, stock)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String productJson(String name, String category, String price, int stock) {
        return """
                {"name": "%s", "category": "%s", "price": %s, "stock": %d, "rating": 4.5}
                """.formatted(name, category, new BigDecimal(price).toPlainString(), stock);
    }
}
