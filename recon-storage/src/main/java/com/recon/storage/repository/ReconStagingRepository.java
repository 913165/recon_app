package com.recon.storage.repository;

import com.recon.storage.entity.ReconStaging;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconStagingRepository extends JpaRepository<ReconStaging, Long> {
    List<ReconStaging> findByProcessedFalse();
}

