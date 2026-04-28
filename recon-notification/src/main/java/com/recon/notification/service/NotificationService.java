package com.recon.notification.service;

import com.recon.storage.entity.ReconResult;

public interface NotificationService {
    void notifyBreak(ReconResult result);

    void createAgedBreakTickets();
}

