package com.octapulse.backend.repository;

import com.octapulse.backend.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {
    List<DeviceToken> findByUserId(UUID userId);
}
