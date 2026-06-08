package com.jobpilot.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust JSON extraction utility for LLM responses.
 * Handles markdown code blocks, raw text fallbacks, unescaped newlines,
 * and other common LLM response quirks.
 */
@Slf4j
public final class JsonExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json|js|javascript|j)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
    private static final Pattern BRACE_BLOCK = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);

    private JsonExtractor() {}

    /**
     * Extracts a JSON Map from an LLM response string.
     * Strategy order:
     * 1. Try markdown ```json ... ``` block extraction
     * 2. Try finding outermost { ... } braces
     * 3. Fallback: wrap raw text in a generic response map
     */
    public static Map<String, Object> extractJson(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Empty AI response received");
            return Map.of("content", "", "rawResponse", true);
        }

        String cleaned = response.trim();

        // Strategy 1: Extract from ```json ... ``` block
        Matcher blockMatcher = JSON_BLOCK.matcher(cleaned);
        if (blockMatcher.find()) {
            String jsonContent = blockMatcher.group(1).trim();
            Map<String, Object> result = tryParse(jsonContent);
            if (result != null) return result;

            // Strategy 1b: Try with escaped newlines inside the markdown block
            String escaped = escapeInternalNewlines(jsonContent);
            result = tryParse(escaped);
            if (result != null) return result;
        }

        // Strategy 2: Find outermost { ... }
        int start = cleaned.indexOf('{');
        int end = findMatchingBrace(cleaned, start);
        if (start != -1 && end != -1 && end > start) {
            String jsonContent = cleaned.substring(start, end + 1);
            Map<String, Object> result = tryParse(jsonContent);
            if (result != null) return result;

            // Strategy 2b: Try with escaped newlines
            String escaped = escapeInternalNewlines(jsonContent);
            result = tryParse(escaped);
            if (result != null) return result;
        }

        // Strategy 3: Try parsing the entire response as JSON
        Map<String, Object> result = tryParse(cleaned);
        if (result != null) return result;

        // Strategy 4: Fallback — wrap raw text
        log.warn("No valid JSON found in AI response, wrapping raw text. First 200 chars: {}",
                cleaned.substring(0, Math.min(200, cleaned.length())));
        return Map.of(
                "content", cleaned,
                "answer", cleaned,
                "coverLetter", cleaned,
                "rawResponse", true
        );
    }

    /**
     * Try to parse a JSON string. Returns null on failure (no exception thrown).
     */
    private static Map<String, Object> tryParse(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.trace("JSON parse attempt failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Find the position of the matching closing brace for an opening brace.
     */
    private static int findMatchingBrace(String text, int openPos) {
        if (openPos < 0 || openPos >= text.length()) return -1;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Escape literal newlines/carriage returns that appear inside JSON string values.
     * LLMs sometimes produce unescaped newlines which break JSON parsers.
     */
    public static String escapeInternalNewlines(String json) {
        if (json == null || json.isEmpty()) return json;

        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean esc = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (esc) {
                sb.append(c);
                esc = false;
            } else if (c == '\\') {
                sb.append(c);
                esc = true;
            } else if (c == '"') {
                sb.append(c);
                inString = !inString;
            } else if ((c == '\n' || c == '\r') && inString) {
                sb.append(c == '\n' ? "\\n" : "\\r");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
