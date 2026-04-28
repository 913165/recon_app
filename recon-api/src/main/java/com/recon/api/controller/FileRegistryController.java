package com.recon.api.controller;

import com.recon.common.dto.FileRegistryDto;
import com.recon.common.dto.FileStatusDto;
import com.recon.common.enums.SourceSystem;
import com.recon.storage.service.FileRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileRegistryController {

    private final FileRegistryService fileRegistryService;

    @Operation(summary = "List files by report date and source system")
    @GetMapping
    public List<FileRegistryDto> files(@RequestParam LocalDate reportDate, @RequestParam SourceSystem sourceSystem) {
        return fileRegistryService.findByDateAndSource(reportDate, sourceSystem);
    }

    @Operation(summary = "Get one file processing status")
    @GetMapping("/{fileId}/status")
    public FileStatusDto status(@PathVariable String fileId) {
        return fileRegistryService.status(fileId);
    }

    @Operation(summary = "Reprocess failed file")
    @PostMapping("/{fileId}/reprocess")
    public void reprocess(@PathVariable String fileId) {
        fileRegistryService.markForReprocess(fileId);
    }
}

