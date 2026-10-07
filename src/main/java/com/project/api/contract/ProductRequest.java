package com.project.api.contract;

import java.math.BigDecimal;

import com.project.api.model.Product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = Product.NAME_MAX, message = "name must be at most {max} characters")
        String name,

        @NotBlank(message = "category is required")
        @Size(max = Product.CATEGORY_MAX, message = "category must be at most {max} characters")
        String category,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must be 0 or more")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 digits and 2 decimals")
        BigDecimal price,

        @NotNull(message = "stock is required")
        @PositiveOrZero(message = "stock must be 0 or more")
        Integer stock,

        @NotNull(message = "rating is required")
        @DecimalMin(value = "0.0", message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        @Digits(integer = 1, fraction = 1, message = "rating must have at most 1 decimal")
        BigDecimal rating) {

    public ProductRequest {
        name = name == null ? null : name.trim();
        category = category == null ? null : category.trim();
    }
}
