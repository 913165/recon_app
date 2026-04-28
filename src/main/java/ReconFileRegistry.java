package com.recon.storage.entity;

import com.recon.common.enums.FileStatus;
import com.recon.common.enums.SourceSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recon_file_registry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconFileRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, unique = true)
    private String fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "loaded_records")
    private Long loadedRecords;

    @Column(name = "failed_records")
    private Long failedRecords;

    @Column(name = "control_amount")
    private BigDecimal controlAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_status", nullable = false)
    private FileStatus fileStatus;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
