package com.project.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    void updateRefreshesTheCacheSoLookupsAreNeverStale() {
        long id = newProduct();
        service.get(id);

        service.update(id, new ProductRequest("Renamed", "Home", new BigDecimal("15.00"), 3, new BigDecimal("4.0")));
        clearInvocations(repository);

        assertThat(service.get(id).name()).isEqualTo("Renamed");
        assertThat(service.get(id).price()).isEqualByComparingTo("15.00");
        verify(repository, times(0)).findById(id);
    }

    @Test
    void deleteEvictsTheCacheSoLookupsReturn404() {
        long id = newProduct();
        service.get(id);

        service.delete(id);

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
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

    @SuppressWarnings("unchecked")
    private Cache<Object, Object> nativeCache() {
        return (Cache<Object, Object>) cacheManager.getCache(CacheConfig.PRODUCTS).getNativeCache();
    }
}
