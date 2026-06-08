package com.jobpilot.ai.service;

import com.jobpilot.ai.entity.ModelVersion;
import com.jobpilot.ai.repository.ModelVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRoutingService {

    private final ModelVersionRepository modelVersionRepository;
    private final Random random = new Random();

    /**
     * Determines which model version should handle a request based on traffic weights.
     * This enables A/B testing between base models and fine-tuned versions.
     */
    public ModelVersion getTargetModel() {
        List<ModelVersion> activeVersions = modelVersionRepository.findByIsActiveTrue();
        
        if (activeVersions.isEmpty()) {
            log.error("CRITICAL: No active model versions found in database.");
            throw new RuntimeException("AI Infrastructure Error: No active model.");
        }

        if (activeVersions.size() == 1) {
            return activeVersions.get(0);
        }

        // Weighted Random Selection
        int totalWeight = activeVersions.stream().mapToInt(ModelVersion::getTrafficWeight).sum();
        int roll = random.nextInt(totalWeight);
        
        int currentSum = 0;
        for (ModelVersion version : activeVersions) {
            currentSum += version.getTrafficWeight();
            if (roll < currentSum) {
                log.debug("Routing request to model: {} (weight: {})", version.getVersionName(), version.getTrafficWeight());
                return version;
            }
        }

        return activeVersions.get(0);
    }
}
