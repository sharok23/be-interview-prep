package com.project.api.contract;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotEmpty(message = "items must contain at least one item")
        @Size(max = 50, message = "items must contain at most {max} items")
        List<@Valid @NotNull(message = "item must not be null") OrderItemRequest> items) {
}
