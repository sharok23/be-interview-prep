package com.project.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.api.model.Product;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity, p.version = p.version + 1 "
            + "where p.id = :id and p.stock >= :quantity")
    int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity, p.version = p.version + 1 where p.id = :id")
    int releaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
