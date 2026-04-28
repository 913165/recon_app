package com.recon.processing.writer;

import com.recon.common.dto.ValidatedRecord;
import com.recon.processing.step.ValidatedRecordBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ValidatedItemWriter {

    private final ValidatedRecordBuffer buffer;

    public void write(List<? extends ValidatedRecord> records) {
        for (ValidatedRecord record : records) {
            buffer.add(record);
        }
    }
}
