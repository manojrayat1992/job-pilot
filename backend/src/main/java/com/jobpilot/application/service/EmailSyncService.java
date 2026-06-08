package com.jobpilot.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.application.entity.JobApplication;
import com.jobpilot.application.entity.JobApplication.ApplicationStatus;
import com.jobpilot.application.repository.ApplicationRepository;
import com.jobpilot.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSyncService {

    private final ApplicationRepository applicationRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final Random random = new Random();

    @Transactional
    public int syncInbox() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<JobApplication> apps = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        
        if (apps.isEmpty()) {
            return 0;
        }

        ChatClient chatClient = chatClientBuilder.build();
        int updatedCount = 0;

        // Iterate through all pending applications and check for updates
        for (JobApplication app : apps) {
            if (app.getStatus() == ApplicationStatus.OFFER || 
                app.getStatus() == ApplicationStatus.REJECTED) {
                continue;
            }

            // Only perform sync check for some applications at a time to keep it realistic
            if (random.nextInt(10) > 4) { // 50% chance to check this application this turn
                continue;
            }

            try {
                log.info("Scanning inbox for application: company={}, position={}", app.getCompany(), app.getPosition());

                // 1. Generate a simulated recruiter email using AI
                String emailPrompt = String.format("""
                        Generate a realistic job application email update for:
                        Company: %s
                        Position: %s
                        Current Status: %s
                        
                        The email can be an invitation to phone screen, interview request, rejection, offer, or just a generic thank you.
                        Choose one status transition randomly but realistically.
                        Return a plain text email body (from recruiter to candidate) under 120 words.
                        """, 
                        app.getCompany(), app.getPosition(), app.getStatus().name());

                String emailBody = chatClient.prompt().user(emailPrompt).call().content();
                log.debug("Simulated Email received:\n{}", emailBody);

                // 2. Parse the simulated email using AI to extract the new status
                String parsePrompt = String.format("""
                        Analyze this email received from a recruiter and determine the new application status.
                        
                        Email body:
                        \"\"\"
                        %s
                        \"\"\"
                        
                        Current Status: %s
                        
                        Map the outcome to one of these valid statuses:
                        - SAVED (no change / still reviewing)
                        - APPLIED (application received)
                        - PHONE_SCREEN (recruiter screen scheduled/requested)
                        - INTERVIEW (technical/hiring manager interview scheduled)
                        - OFFER (job offer received!)
                        - REJECTED (declined / no longer under consideration)
                        
                        Return ONLY a valid JSON object with these keys:
                        "status": "VALID_STATUS_STRING",
                        "summary": "1-sentence reason for mapping"
                        """, 
                        emailBody, app.getStatus().name());

                String parseResponse = chatClient.prompt().user(parsePrompt).call().content();
                
                // Parse JSON response
                Map<String, Object> result = com.jobpilot.common.util.JsonExtractor.extractJson(parseResponse);
                String statusStr = (String) result.get("status");

                if (statusStr != null) {
                    ApplicationStatus newStatus = parseStatus(statusStr, app.getStatus());
                    if (newStatus != app.getStatus()) {
                        app.setStatus(newStatus);
                        // Save email body as notes in application history
                        String notes = app.getNotes() != null ? app.getNotes() : "";
                        app.setNotes(notes + "\n\n[Email Sync - " + java.time.LocalDate.now() + "]\n" + emailBody);
                        applicationRepository.save(app);
                        updatedCount++;
                        log.info("AI Auto-synced status for {} to {}", app.getCompany(), newStatus);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse email status update for {}", app.getCompany(), e);
            }
        }

        return updatedCount;
    }

    private ApplicationStatus parseStatus(String status, ApplicationStatus defaultStatus) {
        try {
            return ApplicationStatus.valueOf(status.toUpperCase().trim());
        } catch (Exception e) {
            return defaultStatus;
        }
    }
}
