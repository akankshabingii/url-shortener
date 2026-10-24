package com.akanksha.url_shortener.repository;

import com.akanksha.url_shortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    // Atomic increment in the DB: no lost updates when many clicks arrive at once
    @Modifying
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1 where u.id = :id")
    void incrementClickCount(@Param("id") Long id);
}
