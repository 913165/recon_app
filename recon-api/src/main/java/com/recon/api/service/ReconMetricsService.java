package com.recon.api.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReconMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong unresolvedBreaks = new AtomicLong();
    private final AtomicLong matchRate = new AtomicLong();

    public ReconMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("recon.breaks.total", unresolvedBreaks::get).register(meterRegistry);
        Gauge.builder("recon.match.rate", matchRate::get).register(meterRegistry);
    }

    public void incrementFilesReceived(String source) {
        Counter.builder("recon.files.received").tag("source", source).register(meterRegistry).increment();
    }

    public void incrementProcessed(String source, String status) {
        Counter.builder("recon.records.processed")
                .tag("source", source)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopProcessingTimer(Timer.Sample sample) {
        sample.stop(Timer.builder("recon.processing.duration").register(meterRegistry));
    }

    public void stopBulkLoadTimer(Timer.Sample sample) {
        sample.stop(Timer.builder("recon.db.bulk.load.duration").register(meterRegistry));
    }

    public void setUnresolvedBreaks(long value) {
        unresolvedBreaks.set(value);
    }

    public void setMatchRate(long value) {
        matchRate.set(value);
    }
}

