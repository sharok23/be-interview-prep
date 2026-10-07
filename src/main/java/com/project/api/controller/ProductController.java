package com.project.api.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import com.project.api.contract.PageResponse;
import com.project.api.contract.ProductRequest;
import com.project.api.contract.ProductResponse;
import com.project.api.service.ProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final int MAX_PAGE = Integer.MAX_VALUE / ProductService.MAX_PAGE_SIZE;

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Parameter(name = "sort", in = ParameterIn.QUERY,
            description = "field[,asc|desc]; repeat for several fields, e.g. sort=category&sort=price,desc",
            array = @ArraySchema(schema = @Schema(type = "string")))
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minPrice must be 0 or more")
            BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxPrice must be 0 or more")
            BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(name = "q", required = false) String nameQuery,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or more")
            @Max(value = MAX_PAGE, message = "page must be at most " + MAX_PAGE) int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be 1 or more") int size,
            HttpServletRequest request) {
        String[] sort = request.getParameterValues("sort");
        List<String> sortParams = sort == null ? List.of() : List.of(sort);
        return service.search(category, minPrice, maxPrice, inStock, nameQuery, page, size, sortParams);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(description = "Requires ADMIN role")
    @ApiResponse(responseCode = "201", description = "Product created")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(description = "Requires ADMIN role")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(description = "Requires ADMIN role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
