package com.octapulse.backend.repository;

import com.octapulse.backend.domain.ApiUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiUsageRepository extends JpaRepository<ApiUsage, UUID> {
    Optional<ApiUsage> findBySourceAndMonth(String source, String month);
}
