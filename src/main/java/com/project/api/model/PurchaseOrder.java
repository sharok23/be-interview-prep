package com.project.api.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.project.api.enums.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_owner_idempotency_key", columnNames = {"owner_id", "idempotency_key"}))
public class PurchaseOrder {

    public static final int IDEMPOTENCY_KEY_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = IDEMPOTENCY_KEY_MAX)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId")
    private List<OrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(User owner, String idempotencyKey, String requestHash) {
        this.owner = owner;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = OrderStatus.PLACED;
        this.createdAt = Instant.now();
    }

    public void addItem(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
