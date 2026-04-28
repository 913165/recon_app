package com.recon.storage.repository;

import com.recon.common.enums.SourceSystem;
import com.recon.storage.entity.ReconFileRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReconFileRegistryRepository extends JpaRepository<ReconFileRegistry, Long> {
    Optional<ReconFileRegistry> findByFileId(String fileId);

    List<ReconFileRegistry> findByReportDateAndSourceSystem(LocalDate reportDate, SourceSystem sourceSystem);
}

