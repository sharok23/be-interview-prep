package com.project.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.api.model.PurchaseOrder;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Override
    @EntityGraph(attributePaths = {"owner", "items"})
    Optional<PurchaseOrder> findById(Long id);

    @EntityGraph(attributePaths = {"owner", "items"})
    Optional<PurchaseOrder> findByIdAndOwnerUsername(Long id, String username);

    @EntityGraph(attributePaths = {"owner", "items"})
    Optional<PurchaseOrder> findByOwnerUsernameAndIdempotencyKey(String username, String idempotencyKey);

    @Modifying(clearAutomatically = true)
    @Query("update PurchaseOrder o set o.status = com.project.api.enums.OrderStatus.CANCELLED "
            + "where o.id = :id and o.status = com.project.api.enums.OrderStatus.PLACED")
    int markCancelled(@Param("id") Long id);
}
