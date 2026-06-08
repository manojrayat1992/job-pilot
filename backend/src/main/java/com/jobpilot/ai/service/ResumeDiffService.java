package com.jobpilot.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Resume Version Diffing Service — Computes a structured diff between
 * two versions of a resume (original vs. AI-tailored) to show exactly
 * what was changed and why.
 */
@Slf4j
@Service
public class ResumeDiffService {

    /**
     * Compute a line-by-line diff between two resume text versions.
     * Returns structured diff with additions, removals, and unchanged sections.
     */
    public Map<String, Object> computeDiff(String originalText, String tailoredText) {
        if (originalText == null) originalText = "";
        if (tailoredText == null) tailoredText = "";

        String[] originalLines = originalText.split("\\n");
        String[] tailoredLines = tailoredText.split("\\n");

        List<Map<String, Object>> diffEntries = new ArrayList<>();
        int maxLen = Math.max(originalLines.length, tailoredLines.length);

        int additions = 0, removals = 0, unchanged = 0;

        // Simple line-by-line comparison (suitable for resume text)
        // Uses LCS-inspired approach for better alignment
        List<String> origList = Arrays.asList(originalLines);
        List<String> tailList = Arrays.asList(tailoredLines);

        int[][] dp = computeLCS(origList, tailList);
        List<Map<String, Object>> alignedDiff = traceback(dp, origList, tailList);

        for (Map<String, Object> entry : alignedDiff) {
            String type = (String) entry.get("type");
            switch (type) {
                case "added" -> additions++;
                case "removed" -> removals++;
                case "unchanged" -> unchanged++;
                case "modified" -> { additions++; removals++; }
            }
        }

        // Compute summary statistics
        int totalChanges = additions + removals;
        double changePercentage = originalLines.length > 0
                ? (double) totalChanges / originalLines.length * 100 : 0;

        return Map.of(
                "diff", alignedDiff,
                "stats", Map.of(
                        "totalLines", maxLen,
                        "additions", additions,
                        "removals", removals,
                        "unchanged", unchanged,
                        "changePercentage", Math.round(changePercentage * 10.0) / 10.0
                ),
                "originalLineCount", originalLines.length,
                "tailoredLineCount", tailoredLines.length
        );
    }

    /**
     * Compare two parsed JSON resume structures and produce a section-by-section diff.
     */
    public Map<String, Object> computeStructuredDiff(Map<String, Object> original, Map<String, Object> tailored) {
        List<Map<String, Object>> sectionDiffs = new ArrayList<>();

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(original.keySet());
        allKeys.addAll(tailored.keySet());

        for (String key : allKeys) {
            Object origVal = original.get(key);
            Object tailVal = tailored.get(key);

            Map<String, Object> sectionDiff = new HashMap<>();
            sectionDiff.put("section", key);

            if (origVal == null && tailVal != null) {
                sectionDiff.put("type", "added");
                sectionDiff.put("newValue", tailVal);
            } else if (origVal != null && tailVal == null) {
                sectionDiff.put("type", "removed");
                sectionDiff.put("oldValue", origVal);
            } else if (origVal != null && !origVal.equals(tailVal)) {
                sectionDiff.put("type", "modified");
                sectionDiff.put("oldValue", origVal);
                sectionDiff.put("newValue", tailVal);
            } else {
                sectionDiff.put("type", "unchanged");
                continue; // Skip unchanged sections in output
            }

            sectionDiffs.add(sectionDiff);
        }

        return Map.of(
                "sections", sectionDiffs,
                "totalSections", allKeys.size(),
                "modifiedSections", sectionDiffs.size()
        );
    }

    // ─── LCS-based diff algorithm ───

    private int[][] computeLCS(List<String> a, List<String> b) {
        int m = a.size(), n = b.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.get(i - 1).trim().equals(b.get(j - 1).trim())) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp;
    }

    private List<Map<String, Object>> traceback(int[][] dp, List<String> a, List<String> b) {
        List<Map<String, Object>> result = new ArrayList<>();
        int i = a.size(), j = b.size();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a.get(i - 1).trim().equals(b.get(j - 1).trim())) {
                result.add(0, Map.of(
                        "type", "unchanged",
                        "content", a.get(i - 1),
                        "lineOriginal", i,
                        "lineTailored", j
                ));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, Map.of(
                        "type", "added",
                        "content", b.get(j - 1),
                        "lineTailored", j
                ));
                j--;
            } else if (i > 0) {
                result.add(0, Map.of(
                        "type", "removed",
                        "content", a.get(i - 1),
                        "lineOriginal", i
                ));
                i--;
            }
        }
        return result;
    }
}
