package com.recon.ingestion.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "recon.landing-zone.mode", havingValue = "s3")
public class S3PollingWatcherService implements FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(S3PollingWatcherService.class);

    @Override
    public void startWatching() {
        log.info("S3 polling watcher started");
    }

    @Scheduled(fixedDelayString = "${recon.landing-zone.poll-interval-ms:5000}")
    public void poll() {
        log.debug("Polling S3 landing bucket for new files");
    }
}
