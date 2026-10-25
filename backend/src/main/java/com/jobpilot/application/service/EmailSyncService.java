package com.jobpilot.application.service;

import com.jobpilot.common.exception.FeatureUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class EmailSyncService {
    public int syncInbox() {
        throw new FeatureUnavailableException(
                "Email sync is not available yet. Update application statuses manually.");
    }
}
