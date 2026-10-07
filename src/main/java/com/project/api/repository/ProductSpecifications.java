package com.project.api.repository;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.project.api.model.Product;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(String category, BigDecimal minPrice, BigDecimal maxPrice,
                                                     Boolean inStock, String nameQuery) {
        return Specification.allOf(
                categoryIs(category),
                priceAtLeast(minPrice),
                priceAtMost(maxPrice),
                inStockOnly(inStock),
                nameContains(nameQuery));
    }

    static Specification<Product> categoryIs(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), value);
    }

    static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return minPrice == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    static Specification<Product> inStockOnly(Boolean inStock) {
        return Boolean.TRUE.equals(inStock) ? (root, query, cb) -> cb.greaterThan(root.get("stock"), 0) : null;
    }

    static Specification<Product> nameContains(String nameQuery) {
        if (nameQuery == null || nameQuery.isBlank()) {
            return null;
        }
        String escaped = nameQuery.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\');
    }
}
