package com.project.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.project.api.enums.Role;
import com.project.api.model.Product;
import com.project.api.repository.ProductRepository;
import com.project.api.service.OrderService;
import com.project.api.support.TestUsers;

@SpringBootTest
class OrderControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private ProductRepository productRepository;

    private MockMvc mvc;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = testUsers.create(Role.USER).authorization();
        bob = testUsers.create(Role.USER).authorization();
        admin = testUsers.create(Role.ADMIN).authorization();
    }

    @Test
    void placeOrderReservesStockAndReturns201() throws Exception {
        long lamp = product("10.00", 5);
        long book = product("2.50", 5);

        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 2}, {\"productId\": %d, \"quantity\": 4}]"
                .formatted(lamp, book))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(30.00));

        assertThat(stockOf(lamp)).isEqualTo(3);
        assertThat(stockOf(book)).isEqualTo(1);
    }

    @Test
    void productLookupShowsReservedStockImmediately() throws Exception {
        long lamp = product("10.00", 5);
        mvc.perform(get("/api/products/{id}", lamp).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.stock").value(5));

        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 2}]".formatted(lamp))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/products/{id}", lamp).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(jsonPath("$.stock").value(3));
    }

    @Test
    void retryWithSameKeyReturnsTheSameOrderWithoutReservingAgain() throws Exception {
        long lamp = product("10.00", 5);
        String key = newKey();
        String items = "[{\"productId\": %d, \"quantity\": 2}]".formatted(lamp);

        String first = place(alice, key, items).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        place(alice, key, items)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(((Number) JsonPath.read(first, "$.id")).intValue()));

        assertThat(stockOf(lamp)).isEqualTo(3);
    }

    @Test
    void sameKeyWithADifferentOrderReturns409() throws Exception {
        long lamp = product("10.00", 5);
        String key = newKey();
        place(alice, key, "[{\"productId\": %d, \"quantity\": 1}]".formatted(lamp)).andExpect(status().isCreated());

        place(alice, key, "[{\"productId\": %d, \"quantity\": 3}]".formatted(lamp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already used for a different order")));
    }

    @Test
    void idempotencyKeysAreScopedPerUser() throws Exception {
        long lamp = product("10.00", 5);
        String key = newKey();
        String items = "[{\"productId\": %d, \"quantity\": 1}]".formatted(lamp);

        place(alice, key, items).andExpect(status().isCreated());
        place(bob, key, items).andExpect(status().isCreated());

        assertThat(stockOf(lamp)).isEqualTo(3);
    }

    @Test
    void insufficientStockReturns409AndReservesNothing() throws Exception {
        long plenty = product("1.00", 10);
        long scarce = product("1.00", 1);

        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 3}, {\"productId\": %d, \"quantity\": 2}]"
                .formatted(plenty, scarce))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient stock for product " + scarce)))
                .andExpect(jsonPath("$.message").value(containsString("requested 2, available 1")));

        assertThat(stockOf(plenty)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void duplicateLinesForTheSameProductAreMerged() throws Exception {
        long lamp = product("10.00", 3);

        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 2}, {\"productId\": %d, \"quantity\": 2}]"
                .formatted(lamp, lamp))
                .andExpect(status().isConflict());
        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 1}, {\"productId\": %d, \"quantity\": 2}]"
                .formatted(lamp, lamp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void invalidRequestsReturn400Or404() throws Exception {
        long lamp = product("10.00", 5);

        mvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": %d, \"quantity\": 1}]}".formatted(lamp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").exists());
        place(alice, newKey(), "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("items must contain at least one item"));
        place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 0}]".formatted(lamp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("quantity must be at least 1"));
        place(alice, newKey(), "[{\"productId\": 999999, \"quantity\": 1}]")
                .andExpect(status().isNotFound());
        place(alice, "   ", "[{\"productId\": %d, \"quantity\": 1}]".formatted(lamp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").exists());
        place(alice, "k".repeat(101), "[{\"productId\": %d, \"quantity\": 1}]".formatted(lamp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['Idempotency-Key']")
                        .value("Idempotency-Key header must be 1 to 100 characters"));
        mvc.perform(get("/api/orders/abc").header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/abc/cancel").header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header(OrderService.IDEMPOTENCY_HEADER, newKey())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordersAreVisibleToTheirOwnerAndAdminOnly() throws Exception {
        long lamp = product("10.00", 5);
        long id = orderId(place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 1}]".formatted(lamp)));

        mvc.perform(get("/api/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isOk());
        mvc.perform(get("/api/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    void cancelReturnsStockOnceEvenWhenRepeated() throws Exception {
        long lamp = product("10.00", 5);
        long id = orderId(place(alice, newKey(), "[{\"productId\": %d, \"quantity\": 4}]".formatted(lamp)));
        assertThat(stockOf(lamp)).isEqualTo(1);

        mvc.perform(post("/api/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(lamp)).isEqualTo(5);
        mvc.perform(post("/api/orders/{id}/cancel", 999_999).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isNotFound());
    }

    private ResultActions place(String authorization, String key, String items) throws Exception {
        return mvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, authorization)
                .header(OrderService.IDEMPOTENCY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": %s}".formatted(items)));
    }

    private static long orderId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long product(String price, int stock) {
        String name = "order-test-" + UUID.randomUUID().toString().substring(0, 8);
        return productRepository.save(new Product(name, "Test", new BigDecimal(price), stock, new BigDecimal("4.0")))
                .getId();
    }

    private int stockOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
