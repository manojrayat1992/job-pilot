package com.jobpilot.ai.service;

import com.jobpilot.ai.entity.AiLog;
import com.jobpilot.ai.entity.FineTuneDataset;
import com.jobpilot.ai.repository.AiLogRepository;
import com.jobpilot.ai.repository.FineTuneDatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataHarvestService {

    private final AiLogRepository aiLogRepository;
    private final FineTuneDatasetRepository fineTuneRepository;

    /**
     * Daily job to harvest high-performing AI interactions for the fine-tuning dataset.
     * Criteria: Rating = 5 AND Score Jump > 10%
     * Runs at 3 AM every day.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void harvestWinningPairs() {
        log.info("Starting daily AI data harvest...");
        
        List<AiLog> highPerformers = aiLogRepository.findHighPerformingLogs();
        int harvestedCount = 0;

        for (AiLog logEntry : highPerformers) {
            // Skip if already harvested
            if (fineTuneRepository.existsByAiLogId(logEntry.getId())) {
                continue;
            }

            // Create fine-tuning sample
            FineTuneDataset sample = FineTuneDataset.builder()
                    .aiLogId(logEntry.getId())
                    .promptTemplate(logEntry.getPromptTemplate())
                    .inputContent(logEntry.getRequestContent())
                    .outputContent(logEntry.getResponseContent())
                    .rating(logEntry.getRating())
                    .scoreJump(logEntry.getScoreJump())
                    .build();
            
            fineTuneRepository.save(sample);
            harvestedCount++;
        }

        log.info("AI harvest complete. Added {} new golden samples to the training dataset.", harvestedCount);
        
        checkTrainingThreshold();
    }

    private void checkTrainingThreshold() {
        long totalSamples = fineTuneRepository.count();
        if (totalSamples >= 1000) {
            log.info("THRESHOLD REACHED: Training dataset has {} samples. Triggering fine-tuning preparation.", totalSamples);
            // In a real scenario, this could trigger a webhook to a Python/RunPod script
        }
    }
}
