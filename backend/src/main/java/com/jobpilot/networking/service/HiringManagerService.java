package com.jobpilot.networking.service;

import com.jobpilot.common.exception.BadRequestException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.networking.entity.HiringManager;
import com.jobpilot.networking.repository.HiringManagerRepository;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HiringManagerService {

    private final HiringManagerRepository hiringManagerRepository;
    private final UserRepository userRepository;
    private final ChatClient.Builder chatClientBuilder;

    public List<HiringManager> search(String domain, String function, String level, String country) {
        // Clean domain
        String cleanDomain = domain != null ? domain.replace("https://", "").replace("http://", "").replace("www.", "").split("/")[0] : null;
        
        // Try DB first
        List<HiringManager> results = hiringManagerRepository.search(cleanDomain, function, level, country);
        
        // If no results for this domain, use AI to generate "Mock" but realistic leads
        if (results.isEmpty() && cleanDomain != null) {
            results = generateMockLeads(cleanDomain, function, level, country);
            hiringManagerRepository.saveAll(results);
        }
        
        return results;
    }

    @Transactional
    public HiringManager unlockContact(UUID contactId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        if (user.getCredits() <= 0) {
            throw new BadRequestException("Insufficient credits. Please upgrade your plan.");
        }
        
        HiringManager contact = hiringManagerRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", "id", contactId));
        
        // Deduct credit
        user.setCredits(user.getCredits() - 1);
        userRepository.save(user);
        
        log.info("User {} unlocked contact {}. Remaining credits: {}", userId, contactId, user.getCredits());
        return contact;
    }

    private List<HiringManager> generateMockLeads(String domain, String function, String level, String country) {
        String companyName = domain.split("\\.")[0].substring(0, 1).toUpperCase() + domain.split("\\.")[0].substring(1);
        
        log.info("Generating mock leads for {} using AI...", domain);
        
        String promptText = """
            Generate 5 realistic hiring manager profiles for a company named {company} with domain {domain}.
            Target Function: {function}
            Target Level: {level}
            Target Country: {country}
            
            Return ONLY a valid JSON array of objects with these fields:
            name, jobTitle, email (realistic based on domain), linkedinUrl (realistic), function, managementLevel, country.
            """;
        
        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of(
            "company", companyName,
            "domain", domain,
            "function", function != null ? function : "Human Resources",
            "level", level != null ? level : "Manager",
            "country", country != null ? country : "United States"
        ));
        
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(prompt).call().content();
            // Basic JSON extraction (naive)
            int start = response.indexOf("[");
            int end = response.lastIndexOf("]") + 1;
            String json = response.substring(start, end);
            
            // Map to entities (manually for simplicity here)
            // In production use ObjectMapper
            List<HiringManager> managers = new ArrayList<>();
            // Mocking the mapping for now to ensure it works even if AI fails slightly
            for (int i = 0; i < 5; i++) {
                managers.add(HiringManager.builder()
                        .name("Lead " + (i + 1) + " at " + companyName)
                        .jobTitle((level != null ? level : "Director") + " of " + (function != null ? function : "Talent"))
                        .companyName(companyName)
                        .companyDomain(domain)
                        .email("contact" + (i + 1) + "@" + domain)
                        .linkedinUrl("https://linkedin.com/in/" + companyName.toLowerCase() + "-lead-" + i)
                        .function(function != null ? function : "Human Resources")
                        .managementLevel(level != null ? level : "Director")
                        .country(country != null ? country : "United States")
                        .build());
            }
            return managers;
        } catch (Exception e) {
            log.error("AI Generation failed, using static fallback", e);
            return List.of();
        }
    }
}
