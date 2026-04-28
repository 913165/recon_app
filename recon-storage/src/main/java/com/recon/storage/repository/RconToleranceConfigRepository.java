package com.recon.storage.repository;

import com.recon.storage.entity.RconToleranceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RconToleranceConfigRepository extends JpaRepository<RconToleranceConfig, Long> {
    Optional<RconToleranceConfig> findByRconCode(String rconCode);
}

