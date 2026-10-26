package com.akanksha.url_shortener.repository;

import com.akanksha.url_shortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    // Atomic increment in the DB by a batch of clicks.
    // @Transactional is needed because the flush job runs outside any service transaction.
    @Modifying
    @Transactional
    @Query("update UrlMapping u set u.clickCount = u.clickCount + :delta where u.id = :id")
    void incrementClickCountBy(@Param("id") Long id, @Param("delta") long delta);
}