package com.recon.storage.entity;

import com.recon.common.enums.DrCrIndicator;
import com.recon.common.enums.SourceSystem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recon_staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, length = 50)
    private String fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "record_id", nullable = false, length = 30)
    private String recordId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "entity_id", nullable = false, length = 10)
    private String entityId;

    @Column(name = "rcon_code", nullable = false, length = 10)
    private String rconCode;

    @Column(name = "balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "dr_cr_ind", nullable = false, length = 2)
    private DrCrIndicator drCrInd;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;
}
