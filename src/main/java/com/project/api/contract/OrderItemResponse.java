package com.project.api.contract;

import java.math.BigDecimal;

import com.project.api.model.OrderItem;

public record OrderItemResponse(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice());
    }
}
