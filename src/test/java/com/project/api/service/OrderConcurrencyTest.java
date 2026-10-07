package com.project.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project.api.contract.OrderItemRequest;
import com.project.api.contract.OrderRequest;
import com.project.api.enums.Role;
import com.project.api.exception.ConflictException;
import com.project.api.model.CurrentUser;
import com.project.api.model.Product;
import com.project.api.repository.ProductRepository;
import com.project.api.service.OrderService.PlaceResult;
import com.project.api.support.TestUsers;

@SpringBootTest
class OrderConcurrencyTest {

    private static final int CUSTOMERS = 50;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestUsers testUsers;

    @Test
    void fiftySimultaneousOrdersForStockOfTenSucceedExactlyTenTimes() throws Exception {
        long productId = product(10);
        CurrentUser customer = testUsers.create(Role.USER).currentUser();
        OrderRequest oneUnit = orderOf(productId, 1);

        List<Future<PlaceResult>> results = runTogether(CUSTOMERS,
                () -> orderService.place(oneUnit, UUID.randomUUID().toString(), customer));

        int succeeded = 0;
        int rejected = 0;
        for (Future<PlaceResult> result : results) {
            try {
                assertThat(result.get().created()).isTrue();
                succeeded++;
            } catch (ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(ConflictException.class)
                        .hasMessageContaining("Insufficient stock");
                rejected++;
            }
        }

        assertThat(succeeded).isEqualTo(10);
        assertThat(rejected).isEqualTo(40);
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    void simultaneousRetriesWithTheSameKeyCreateOneOrder() throws Exception {
        long productId = product(10);
        CurrentUser customer = testUsers.create(Role.USER).currentUser();
        String key = UUID.randomUUID().toString();
        OrderRequest twoUnits = orderOf(productId, 2);

        List<Future<PlaceResult>> results = runTogether(20, () -> orderService.place(twoUnits, key, customer));

        Set<Long> orderIds = new HashSet<>();
        int created = 0;
        for (Future<PlaceResult> result : results) {
            orderIds.add(result.get().order().id());
            created += result.get().created() ? 1 : 0;
        }

        assertThat(orderIds).hasSize(1);
        assertThat(created).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    void simultaneousRetriesForTheLastUnitsAllGetTheOriginalOrder() throws Exception {
        long productId = product(2);
        CurrentUser customer = testUsers.create(Role.USER).currentUser();
        String key = UUID.randomUUID().toString();
        OrderRequest lastTwoUnits = orderOf(productId, 2);

        List<Future<PlaceResult>> results = runTogether(10, () -> orderService.place(lastTwoUnits, key, customer));

        Set<Long> orderIds = new HashSet<>();
        for (Future<PlaceResult> result : results) {
            orderIds.add(result.get().order().id());
        }
        assertThat(orderIds).hasSize(1);
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    void simultaneousCancelsReturnStockOnlyOnce() throws Exception {
        long productId = product(10);
        CurrentUser customer = testUsers.create(Role.USER).currentUser();
        long orderId = orderService.place(orderOf(productId, 4), UUID.randomUUID().toString(), customer)
                .order().id();

        List<Future<Object>> results = runTogether(20, () -> orderService.cancel(orderId, customer));
        for (Future<Object> result : results) {
            result.get();
        }

        assertThat(stockOf(productId)).isEqualTo(10);
    }

    private static <T> List<Future<T>> runTogether(int threads, Callable<? extends T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
        }
        return futures;
    }

    private static OrderRequest orderOf(long productId, int quantity) {
        return new OrderRequest(List.of(new OrderItemRequest(productId, quantity)));
    }

    private long product(int stock) {
        String name = "concurrency-" + UUID.randomUUID().toString().substring(0, 8);
        return productRepository.save(new Product(name, "Test", new BigDecimal("5.00"), stock, new BigDecimal("4.0")))
                .getId();
    }

    private int stockOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }
}
