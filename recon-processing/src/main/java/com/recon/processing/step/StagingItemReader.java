package com.recon.processing.step;

import com.recon.storage.entity.ReconStaging;
import com.recon.storage.repository.ReconStagingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StagingItemReader {

    private final ReconStagingRepository stagingRepository;
    private Iterator<ReconStaging> iterator;

    public ReconStaging read() {
        if (iterator == null) {
            List<ReconStaging> rows = stagingRepository.findByProcessedFalse();
            iterator = rows.iterator();
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        iterator = null;
        return null;
    }
}
