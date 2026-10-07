package com.project.api.controller;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.api.contract.PageResponse;
import com.project.api.contract.ProductRequest;
import com.project.api.contract.ProductResponse;
import com.project.api.service.ProductService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minPrice must be 0 or more")
            BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxPrice must be 0 or more")
            BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(name = "q", required = false) String nameQuery,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or more") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be 1 or more") int size,
            @RequestParam(required = false) String sort) {
        return service.search(category, minPrice, maxPrice, inStock, nameQuery, page, size, sort);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
