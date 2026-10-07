package com.project.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.project.api.contract.ShortenRequest;

@SpringBootTest
class ShortUrlConcurrencyTest {

    private static final int VISITORS = 50;

    @Autowired
    private ShortUrlService service;

    @Test
    void visitCountStaysAccurateWhenManyVisitorsOpenTheLinkAtOnce() throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();
        String code = service.shorten(new ShortenRequest(url, null), "http://localhost").response().code();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> visits = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(VISITORS)) {
            for (int i = 0; i < VISITORS; i++) {
                visits.add(pool.submit(() -> {
                    start.await();
                    return service.resolve(code);
                }));
            }
            start.countDown();
            for (Future<String> visit : visits) {
                assertThat(visit.get()).isEqualTo(url);
            }
        }

        assertThat(service.stats(code).visits()).isEqualTo(VISITORS);
    }

    @Test
    void concurrentShortensOfTheSameUrlCreateExactlyOneLink() throws Exception {
        String url = "https://example.com/" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<ShortUrlService.ShortenResult>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(VISITORS)) {
            for (int i = 0; i < VISITORS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.shorten(new ShortenRequest(url, null), "http://localhost");
                }));
            }
            start.countDown();
            Set<String> codes = new HashSet<>();
            int created = 0;
            for (Future<ShortUrlService.ShortenResult> result : results) {
                codes.add(result.get().response().code());
                created += result.get().created() ? 1 : 0;
            }
            assertThat(codes).hasSize(1);
            assertThat(created).isEqualTo(1);
        }
    }
}
