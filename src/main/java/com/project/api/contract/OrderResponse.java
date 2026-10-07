package com.project.api.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.project.api.enums.OrderStatus;
import com.project.api.model.PurchaseOrder;

public record OrderResponse(
        Long id,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal total,
        Instant createdAt,
        String owner) {

    public static OrderResponse from(PurchaseOrder order) {
        List<OrderItemResponse> items = order.getItems().stream().map(OrderItemResponse::from).toList();
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(order.getId(), order.getStatus(), items, total, order.getCreatedAt(),
                order.getOwner().getUsername());
    }
}
