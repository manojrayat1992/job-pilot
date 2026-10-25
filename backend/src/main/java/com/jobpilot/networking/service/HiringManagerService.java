package com.jobpilot.networking.service;

import com.jobpilot.common.exception.FeatureUnavailableException;
import com.jobpilot.networking.entity.HiringManager;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/** Contact discovery stays unavailable until records have verified provenance. */
@Service
public class HiringManagerService {
    public List<HiringManager> search(String domain, String function, String level, String country) {
        throw unavailable();
    }

    public HiringManager unlockContact(UUID contactId) {
        throw unavailable();
    }

    private FeatureUnavailableException unavailable() {
        return new FeatureUnavailableException(
                "Contact discovery is not available yet. No credits have been charged.");
    }
}
