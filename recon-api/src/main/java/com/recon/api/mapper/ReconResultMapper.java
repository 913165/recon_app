package com.recon.api.mapper;

import com.recon.common.dto.ReconResultDto;
import com.recon.storage.entity.ReconResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ReconResultMapper {

    public ReconResultDto toDto(ReconResult entity) {
        BigDecimal difference = entity.getBalanceA() == null || entity.getBalanceB() == null
                ? null
                : entity.getBalanceA().subtract(entity.getBalanceB());
        return new ReconResultDto(
                entity.getReconId(),
                entity.getReportDate(),
                entity.getEntityId(),
                entity.getRconCode(),
                entity.getBalanceA(),
                entity.getBalanceB(),
                difference,
                entity.getMatchStatus(),
                entity.getSeverity(),
                entity.getResolved()
        );
    }
}

