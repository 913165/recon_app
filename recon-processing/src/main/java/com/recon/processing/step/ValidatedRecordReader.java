package com.recon.processing.step;

import com.recon.common.dto.ValidatedRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidatedRecordReader {

    private final ValidatedRecordBuffer buffer;

    public ValidatedRecord read() {
        return buffer.poll();
    }
}
