package com.recon.storage.entity;

import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recon_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recon_id", nullable = false, length = 40)
    private String reconId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "rcon_code", nullable = false)
    private String rconCode;

    @Column(name = "source_system_a", nullable = false)
    private String sourceSystemA;

    @Column(name = "balance_a", precision = 20, scale = 2)
    private @Nullable BigDecimal balanceA;

    @Column(name = "source_system_b", nullable = false)
    private String sourceSystemB;

    @Column(name = "balance_b", precision = 20, scale = 2)
    private @Nullable BigDecimal balanceB;

    @Column(name = "tolerance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tolerance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private @Nullable Severity severity;

    @Column(name = "break_reason")
    private @Nullable String breakReason;

    @Column(name = "resolved")
    @Builder.Default
    private Boolean resolved = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
