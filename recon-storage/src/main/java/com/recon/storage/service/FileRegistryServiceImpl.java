package com.recon.storage.service;

import com.recon.common.dto.FileArrivedEvent;
import com.recon.common.dto.FileRegistryDto;
import com.recon.common.dto.FileStatusDto;
import com.recon.common.enums.FileStatus;
import com.recon.common.enums.SourceSystem;
import com.recon.common.exception.DuplicateFileException;
import com.recon.storage.entity.ReconFileRegistry;
import com.recon.storage.repository.ReconFileRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileRegistryServiceImpl implements FileRegistryService {

    private final ReconFileRegistryRepository fileRegistryRepository;

    @Override
    @Transactional
    public void register(@NonNull FileArrivedEvent event) {
        if (fileRegistryRepository.findByFileId(event.fileId()).isPresent()) {
            throw new DuplicateFileException("File already registered: " + event.fileId());
        }
        fileRegistryRepository.save(ReconFileRegistry.builder()
                .fileId(event.fileId())
                .fileName(event.fileName())
                .sourceSystem(event.sourceSystem())
                .filePath(event.filePath())
                .reportDate(event.reportDate())
                .fileStatus(FileStatus.RECEIVED)
                .receivedAt(event.arrivedAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileRegistryDto> findByDateAndSource(@NonNull LocalDate reportDate, @NonNull SourceSystem sourceSystem) {
        return fileRegistryRepository.findByReportDateAndSourceSystem(reportDate, sourceSystem)
                .stream()
                .map(entry -> new FileRegistryDto(
                        entry.getFileId(),
                        entry.getFileName(),
                        entry.getSourceSystem(),
                        entry.getReportDate(),
                        entry.getFileStatus(),
                        entry.getTotalRecords() == null ? 0L : entry.getTotalRecords(),
                        entry.getFailedRecords() == null ? 0L : entry.getFailedRecords(),
                        entry.getReceivedAt() == null ? OffsetDateTime.now() : entry.getReceivedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FileStatusDto status(@NonNull String fileId) {
        ReconFileRegistry file = fileRegistryRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));
        return new FileStatusDto(file.getFileId(), file.getFileStatus());
    }

    @Override
    @Transactional
    public void markForReprocess(@NonNull String fileId) {
        updateStatus(fileId, FileStatus.PARSING);
    }

    @Override
    @Transactional
    public void updateStatus(@NonNull String fileId, @NonNull FileStatus status) {
        ReconFileRegistry file = fileRegistryRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fileId: " + fileId));
        file.setFileStatus(status);
        fileRegistryRepository.save(file);
    }
}

