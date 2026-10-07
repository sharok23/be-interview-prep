package com.project.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.api.model.ShortUrl;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    Optional<ShortUrl> findByDedupeKey(String dedupeKey);

    @Modifying
    @Query("update ShortUrl s set s.visits = s.visits + 1 where s.code = :code")
    int incrementVisits(@Param("code") String code);
}
