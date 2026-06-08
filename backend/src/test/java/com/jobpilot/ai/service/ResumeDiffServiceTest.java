package com.jobpilot.ai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResumeDiffService — validates LCS-based diffing algorithm.
 */
class ResumeDiffServiceTest {

    private final ResumeDiffService service = new ResumeDiffService();

    @Test
    @DisplayName("Should detect additions in tailored version")
    void shouldDetectAdditions() {
        String original = "John Doe\nSoftware Engineer";
        String tailored = "John Doe\nSenior Software Engineer\nExpert in Java";

        Map<String, Object> result = service.computeDiff(original, tailored);

        assertNotNull(result.get("diff"));
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertTrue((int) stats.get("additions") > 0);
    }

    @Test
    @DisplayName("Should detect removals in tailored version")
    void shouldDetectRemovals() {
        String original = "John Doe\nObjective: Get a job\nSkills: Java";
        String tailored = "John Doe\nSkills: Java";

        Map<String, Object> result = service.computeDiff(original, tailored);

        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertTrue((int) stats.get("removals") > 0);
    }

    @Test
    @DisplayName("Should show 0 changes for identical content")
    void shouldShowNoChangesForIdentical() {
        String text = "John Doe\nSoftware Engineer\nSkills: Java, Python";

        Map<String, Object> result = service.computeDiff(text, text);

        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertEquals(0, stats.get("additions"));
        assertEquals(0, stats.get("removals"));
    }

    @Test
    @DisplayName("Should handle null inputs gracefully")
    void shouldHandleNulls() {
        Map<String, Object> result = service.computeDiff(null, "Some text");
        assertNotNull(result.get("diff"));
    }

    @Test
    @DisplayName("Should compute structured diff for JSON resumes")
    void shouldComputeStructuredDiff() {
        Map<String, Object> original = Map.of(
                "summary", "Experienced engineer",
                "skills", List.of("Java", "Python")
        );
        Map<String, Object> tailored = Map.of(
                "summary", "Senior engineer with 10 years experience",
                "skills", List.of("Java", "Python", "Kubernetes"),
                "certifications", List.of("AWS SAA")
        );

        Map<String, Object> result = service.computeStructuredDiff(original, tailored);

        assertNotNull(result.get("sections"));
        assertTrue((int) result.get("modifiedSections") > 0);
    }

    @Test
    @DisplayName("Should calculate change percentage")
    void shouldCalculateChangePercentage() {
        String original = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        String tailored = "Line 1\nModified Line\nLine 3\nNew Line\nLine 5";

        Map<String, Object> result = service.computeDiff(original, tailored);
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");

        assertNotNull(stats.get("changePercentage"));
    }
}
