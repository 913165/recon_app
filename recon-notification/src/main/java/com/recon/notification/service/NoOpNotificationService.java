package com.recon.notification.service;

import com.recon.storage.entity.ReconResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * No-op fallback used when no JavaMailSender is configured (e.g. local dev).
 * All notification calls are logged at DEBUG level and silently dropped.
 */
@Service
@ConditionalOnMissingBean(NotificationServiceImpl.class)
@Slf4j
public class NoOpNotificationService implements NotificationService {

    @Override
    public void notifyBreak(ReconResult result) {
        log.debug("NoOp notification: break {} severity={}", result.getReconId(), result.getSeverity());
    }

    @Override
    public void createAgedBreakTickets() {
        log.debug("NoOp notification: aged-break ticket creation skipped (no mail configured)");
    }
}

