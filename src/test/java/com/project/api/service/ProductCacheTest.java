package com.project.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.github.benmanes.caffeine.cache.Cache;
import com.project.api.config.CacheConfig;
import com.project.api.contract.ProductRequest;
import com.project.api.exception.NotFoundException;
import com.project.api.model.Product;
import com.project.api.repository.ProductRepository;

@SpringBootTest
class ProductCacheTest {

    @Autowired
    private ProductService service;

    @MockitoSpyBean
    private ProductRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void repeatedLookupsHitTheDatabaseOnce() {
        long id = newProduct();
        clearInvocations(repository);
        long hitsBefore = nativeCache().stats().hitCount();

        for (int i = 0; i < 5; i++) {
            assertThat(service.get(id).id()).isEqualTo(id);
        }

        verify(repository, times(1)).findById(id);
        assertThat(nativeCache().stats().hitCount() - hitsBefore).isEqualTo(4);
    }

    @Test
    void updateEvictsTheCacheSoTheNextLookupReloadsFreshData() {
        long id = newProduct();
        service.get(id);

        service.update(id, new ProductRequest("Renamed", "Home", new BigDecimal("15.00"), 3, new BigDecimal("4.0")));
        clearInvocations(repository);

        assertThat(service.get(id).name()).isEqualTo("Renamed");
        assertThat(service.get(id).price()).isEqualByComparingTo("15.00");
        verify(repository, times(1)).findById(id);
    }

    @Test
    void deleteEvictsTheCacheSoLookupsReturn404() {
        long id = newProduct();
        service.get(id);

        service.delete(id);

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void slowLookupRacingAnUpdateNeverLeavesStaleDataInTheCache() throws Exception {
        long id = newProduct();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        Answer<?> real = mockingDetails(repository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object result = real.answer(invocation);
            if (firstLookup.compareAndSet(true, false)) {
                loaded.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return result;
        }).when(repository).findById(id);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> slowRead = pool.submit(() -> service.get(id));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();

            Thread updater = new Thread(() -> service.update(id,
                    new ProductRequest("Updated", "Home", new BigDecimal("12.00"), 1, new BigDecimal("4.5"))));
            updater.start();
            awaitCommittedName(id, "Updated");
            awaitBlockedOrDone(updater);
            release.countDown();
            slowRead.get(5, TimeUnit.SECONDS);
            updater.join(5_000);
        }

        assertThat(service.get(id).name()).isEqualTo("Updated");
    }

    @Test
    void notFoundIsNotCached() {
        assertThatThrownBy(() -> service.get(888_888L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(888_888L)).isInstanceOf(NotFoundException.class);

        verify(repository, times(2)).findById(888_888L);
    }

    private long newProduct() {
        String name = "cached-" + UUID.randomUUID();
        return repository.save(new Product(name, "Home", new BigDecimal("10.00"), 5, new BigDecimal("3.5"))).getId();
    }

    private static void awaitBlockedOrDone(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.BLOCKED || state == Thread.State.TERMINATED) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("update thread neither finished nor blocked on the cache");
    }

    private void awaitCommittedName(long id, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (repository.findAll().stream().anyMatch(p -> p.getId() == id && expected.equals(p.getName()))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("update was not committed in time");
    }

    @SuppressWarnings("unchecked")
    private Cache<Object, Object> nativeCache() {
        return (Cache<Object, Object>) cacheManager.getCache(CacheConfig.PRODUCTS).getNativeCache();
    }
}
