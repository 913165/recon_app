package com.recon.processing.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationStepListener {

    private static final Logger log = LoggerFactory.getLogger(ValidationStepListener.class);

    public void beforeStep(long jobExecutionId) {
        log.debug("Starting validation step for job {}", jobExecutionId);
    }

    public void afterStep(String status) {
        log.debug("Validation step finished with status {}", status);
    }
}
