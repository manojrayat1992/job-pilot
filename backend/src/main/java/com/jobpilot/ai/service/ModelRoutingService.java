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
        
        long totalWeight = 0;
        for (ModelVersion version : activeVersions) {
            if (version.getTrafficWeight() == null || version.getTrafficWeight() < 0
                    || version.getModelPath() == null || version.getModelPath().isBlank()) {
                throw new com.jobpilot.common.exception.FeatureUnavailableException("AI model routing is misconfigured");
            }
            totalWeight += version.getTrafficWeight();
        }
        if (totalWeight == 0) {
            throw new com.jobpilot.common.exception.FeatureUnavailableException("No AI model is enabled for traffic");
        }
        long roll = random.nextLong(totalWeight);
        long currentSum = 0;
        for (ModelVersion version : activeVersions) {
            currentSum += version.getTrafficWeight();
            if (roll < currentSum) return version;
        }
        throw new IllegalStateException("AI routing weights changed during selection");
    }
}
