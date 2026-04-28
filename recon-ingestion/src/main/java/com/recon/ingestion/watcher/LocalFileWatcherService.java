package com.recon.ingestion.watcher;

import com.recon.common.dto.FileArrivedEvent;
import com.recon.common.enums.SourceSystem;
import com.recon.ingestion.kafka.FileEventProducer;
import com.recon.storage.service.FileRegistryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recon.landing-zone.mode", havingValue = "local", matchIfMissing = true)
public class LocalFileWatcherService implements FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileWatcherService.class);

    private final FileEventProducer fileEventProducer;
    private final FileRegistryService fileRegistryService;

    @Value("${recon.landing-zone.path}")
    private String landingPath;

    @PostConstruct
    void init() {
        Thread.ofVirtual().name("recon-file-watcher").start(this::startWatching);
    }

    @Override
    public void startWatching() {
        Path dir = Path.of(landingPath);
        if (!Files.exists(dir)) {
            log.warn("Landing path {} not found, watcher idle", dir);
            return;
        }

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            log.info("Watching landing directory {}", dir);
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path created = dir.resolve((Path) event.context());
                    handleFile(created);
                }
                key.reset();
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("File watcher stopped", ex);
        }
    }

    private void handleFile(Path created) {
        String name = created.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".dat") || lower.endsWith(".csv") || lower.endsWith(".txt"))) {
            return;
        }

        SourceSystem sourceSystem = resolveSourceSystem(name);
        LocalDate reportDate = parseDate(name);
        FileArrivedEvent arrivedEvent = new FileArrivedEvent(
                UUID.randomUUID().toString(),
                name,
                sourceSystem,
                created.toAbsolutePath().toString(),
                reportDate,
                OffsetDateTime.now()
        );
        fileRegistryService.register(arrivedEvent);
        fileEventProducer.publishFileArrived(arrivedEvent);
    }

    private SourceSystem resolveSourceSystem(String name) {
        if (name.startsWith("SRCA_")) {
            return SourceSystem.CORE_BANKING;
        }
        if (name.startsWith("SRCB_")) {
            return SourceSystem.LOANS_SYS;
        }
        return SourceSystem.TRADING_GL;
    }

    private LocalDate parseDate(String fileName) {
        String digits = fileName.replaceAll("\\D", "");
        if (digits.length() >= 8) {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.now();
    }
}
