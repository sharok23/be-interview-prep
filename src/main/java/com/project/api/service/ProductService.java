package com.project.api.service;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api.config.CacheConfig;
import com.project.api.contract.PageResponse;
import com.project.api.contract.ProductRequest;
import com.project.api.contract.ProductResponse;
import com.project.api.exception.BadRequestException;
import com.project.api.exception.NotFoundException;
import com.project.api.model.Product;
import com.project.api.repository.ProductRepository;
import com.project.api.repository.ProductSpecifications;

@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("id", "name", "category", "price", "stock", "rating", "createdAt");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String category, BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean inStock, String nameQuery, int page, int size, String sort) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice", "minPrice must not be greater than maxPrice");
        }
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), parseSort(sort));
        var spec = ProductSpecifications.withFilters(category, minPrice, maxPrice, inStock, nameQuery);
        return PageResponse.from(repository.findAll(spec, pageRequest), ProductResponse::from);
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.name(), request.category(), request.price(), request.stock(),
                request.rating());
        return ProductResponse.from(repository.save(product));
    }

    @CachePut(cacheNames = CacheConfig.PRODUCTS, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = find(id);
        product.update(request.name(), request.category(), request.price(), request.stock(), request.rating());
        return ProductResponse.from(repository.saveAndFlush(product));
    }

    @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#id")
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Product find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("id");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field) || parts.length > 2) {
            throw new BadRequestException("sort", "sort must be one of %s, optionally followed by ,asc or ,desc"
                    .formatted(String.join(", ", SORTABLE.stream().sorted().toList())));
        }
        String direction = parts.length == 2 ? parts[1].trim() : "asc";
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new BadRequestException("sort", "sort direction must be asc or desc");
        }
        Sort primary = Sort.by(Sort.Direction.fromString(direction), field);
        return field.equals("id") ? primary : primary.and(Sort.by("id"));
    }
}
