package com.jobpilot.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JsonExtractor utility — validates all strategies
 * for extracting JSON from various LLM response formats.
 */
class JsonExtractorTest {

    @Test
    @DisplayName("Should parse clean JSON object")
    void shouldParseCleanJson() {
        String input = """
                {"name": "John", "score": 85}
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertEquals("John", result.get("name"));
        assertEquals(85, result.get("score"));
    }

    @Test
    @DisplayName("Should extract JSON from markdown code block")
    void shouldExtractFromMarkdownBlock() {
        String input = """
                Here's the analysis:
                ```json
                {"score": 92, "summary": "Great candidate"}
                ```
                Hope this helps!
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertEquals(92, result.get("score"));
        assertEquals("Great candidate", result.get("summary"));
    }

    @Test
    @DisplayName("Should extract JSON from non-json markdown block")
    void shouldExtractFromGenericBlock() {
        String input = """
                ```
                {"answer": "Use the STAR method"}
                ```
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertEquals("Use the STAR method", result.get("answer"));
    }

    @Test
    @DisplayName("Should extract JSON embedded in prose text")
    void shouldExtractFromProseText() {
        String input = """
                Based on my analysis, here is the result:
                {"overallScore": 78, "tips": ["Add metrics", "Use keywords"]}
                I recommend focusing on the tips above.
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertEquals(78, result.get("overallScore"));
        assertNotNull(result.get("tips"));
    }

    @Test
    @DisplayName("Should handle nested JSON objects correctly")
    void shouldHandleNestedJson() {
        String input = """
                {"outer": {"inner": "value"}, "list": [1, 2, 3]}
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertNotNull(result.get("outer"));
        assertNotNull(result.get("list"));
    }

    @Test
    @DisplayName("Should handle JSON with unescaped newlines in strings")
    void shouldHandleUnescapedNewlines() {
        String input = "{\"text\": \"line one\nline two\", \"score\": 5}";
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertNotNull(result.get("text"));
        assertEquals(5, result.get("score"));
    }

    @Test
    @DisplayName("Should return fallback map for pure text responses")
    void shouldFallbackForPureText() {
        String input = "This is just a plain text response with no JSON at all.";
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertTrue((Boolean) result.get("rawResponse"));
        assertNotNull(result.get("content"));
    }

    @Test
    @DisplayName("Should return fallback for null input")
    void shouldHandleNull() {
        Map<String, Object> result = JsonExtractor.extractJson(null);
        assertTrue((Boolean) result.get("rawResponse"));
    }

    @Test
    @DisplayName("Should return fallback for empty input")
    void shouldHandleEmpty() {
        Map<String, Object> result = JsonExtractor.extractJson("   ");
        assertTrue((Boolean) result.get("rawResponse"));
    }

    @Test
    @DisplayName("Should handle JSON with special characters")
    void shouldHandleSpecialChars() {
        String input = """
                {"title": "Senior Engineer (L5)", "salary": "$150,000+", "note": "It's great"}
                """;
        Map<String, Object> result = JsonExtractor.extractJson(input);
        assertEquals("Senior Engineer (L5)", result.get("title"));
    }
}
