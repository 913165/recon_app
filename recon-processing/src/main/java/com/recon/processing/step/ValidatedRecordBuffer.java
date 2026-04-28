package com.recon.processing.step;

import com.recon.common.dto.ValidatedRecord;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class ValidatedRecordBuffer {

    private final BlockingQueue<ValidatedRecord> queue = new LinkedBlockingQueue<>();

    public void add(ValidatedRecord record) {
        queue.add(record);
    }

    public ValidatedRecord poll() {
        return queue.poll();
    }
}

