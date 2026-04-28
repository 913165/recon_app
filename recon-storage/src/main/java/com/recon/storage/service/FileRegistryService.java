package com.recon.storage.service;

import com.recon.common.dto.FileArrivedEvent;
import com.recon.common.dto.FileRegistryDto;
import com.recon.common.dto.FileStatusDto;
import com.recon.common.enums.FileStatus;
import com.recon.common.enums.SourceSystem;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.util.List;

public interface FileRegistryService {
    void register(@NonNull FileArrivedEvent event);

    List<FileRegistryDto> findByDateAndSource(@NonNull LocalDate reportDate, @NonNull SourceSystem sourceSystem);

    FileStatusDto status(@NonNull String fileId);

    void markForReprocess(@NonNull String fileId);

    void updateStatus(@NonNull String fileId, @NonNull FileStatus status);
}

