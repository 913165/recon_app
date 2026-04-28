package com.recon.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "rcon_tolerance_config")
@Data
public class RconToleranceConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rcon_code", nullable = false, unique = true)
    private String rconCode;

    @Column(name = "tolerance", nullable = false, precision = 10, scale = 2)
    private BigDecimal tolerance;
}

