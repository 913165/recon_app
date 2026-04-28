package com.recon.processing.writer;

import com.recon.storage.entity.ReconResult;
import com.recon.storage.repository.ReconResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReconResultWriter {

    private final ReconResultRepository reconResultRepository;

    public void write(List<? extends ReconResult> records) {
        reconResultRepository.saveAll(records);
    }
}
