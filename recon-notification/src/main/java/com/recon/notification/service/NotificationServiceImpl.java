package com.recon.notification.service;

import com.recon.common.enums.Severity;
import com.recon.storage.entity.ReconResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final RestClient restClient;

    @Value("${recon.notification.slack-webhook-url:}")
    private String slackWebhook;

    @Value("${recon.notification.email.to:ops@company.com}")
    private String emailTo;

    @Override
    @Async
    public void notifyBreak(ReconResult result) {
        if (result.getSeverity() == Severity.CRITICAL) {
            sendEmail(result);
        }
        if (result.getSeverity() == Severity.HIGH || result.getSeverity() == Severity.CRITICAL) {
            sendSlack(result);
        }
    }

    @Override
    @Async
    public void createAgedBreakTickets() {
        log.info("Scheduled JIRA ticket creation for unresolved breaks older than 24h");
    }

    private void sendEmail(ReconResult result) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(emailTo);
        msg.setSubject("[RECON] CRITICAL break detected: " + result.getReconId());
        msg.setText("Break reason: " + result.getBreakReason());
        mailSender.send(msg);
    }

    private void sendSlack(ReconResult result) {
        if (slackWebhook == null || slackWebhook.isBlank()) {
            return;
        }
        restClient.post()
                .uri(slackWebhook)
                .body(java.util.Map.of("text", "Recon break " + result.getReconId() + " severity=" + result.getSeverity()))
                .retrieve()
                .toBodilessEntity();
    }
}

