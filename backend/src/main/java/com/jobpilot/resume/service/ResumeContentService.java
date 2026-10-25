package com.jobpilot.resume.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Validates the supported document schema and derives text from the same saved content. */
@Service
@RequiredArgsConstructor
public class ResumeContentService {
    private final ObjectMapper mapper;
    private static final Set<String> ROOT = Set.of("personalInfo", "summary", "experience", "education", "skills", "projects");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*(?:%|[kKmM])?");

    public Map<String, Object> validatedCopy(Map<String, Object> value) {
        if (value == null || !ROOT.containsAll(value.keySet()) ||
                !value.keySet().containsAll(Set.of("personalInfo", "experience", "education", "skills"))) {
            throw new BadRequestException("Resume must contain personalInfo, experience, education and skills using the supported schema.");
        }
        value = mapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        for (String key : List.of("experience", "education", "skills", "projects")) {
            if (value.containsKey(key) && value.get(key) == null) value.put(key, new ArrayList<>());
        }
        // Older builder payloads placed summary inside personalInfo as well as at the root.
        if (value.get("personalInfo") instanceof Map<?, ?> personal && personal.containsKey("summary")) {
            Object legacy = personal.remove("summary");
            if (value.get("summary") == null || "".equals(value.get("summary"))) value.put("summary", legacy);
            else if (legacy != null && !legacy.equals(value.get("summary"))) {
                throw new BadRequestException("Resume contains conflicting summary fields. Please correct it before saving.");
            }
        }
        object(value.get("personalInfo"), Set.of("name", "email", "phone", "location", "linkedin", "website"), Set.of());
        textValue(value.get("summary"));
        rows(value.get("experience"), Set.of("company", "title", "location", "startDate", "endDate", "dates", "duration", "bullets"), Set.of("bullets"));
        rows(value.get("education"), Set.of("institution", "degree", "field", "startDate", "endDate", "dates", "duration", "gpa"), Set.of());
        strings(value.get("skills"));
        if (value.get("projects") != null) rows(value.get("projects"), Set.of("name", "description", "technologies", "url"), Set.of("technologies"));
        try {
            if (mapper.writeValueAsString(value).length() > 100_000) throw new BadRequestException("Resume content is too large.");
            return mapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IllegalArgumentException ex) { throw new BadRequestException("Invalid resume document."); }
        catch (java.io.IOException ex) { throw new BadRequestException("Invalid resume document."); }
    }

    public Map<String, Object> validateTailoring(Map<String, Object> source, Map<String, Object> proposed) {
        var clean = validatedCopy(proposed);
        // Keep identity, chronology, qualifications, skills and project technologies unchanged.
        // Only summary, existing experience bullets and existing project descriptions may be rewritten.
        if (!protectedFacts(source).equals(protectedFacts(clean))) {
            throw new BadRequestException("AI changed protected resume facts. No draft was saved. Please retry.");
        }
        if (!numbers(valuesOnly(source)).containsAll(numbers(valuesOnly(clean)))) {
            throw new BadRequestException("AI introduced a new numeric claim. No draft was saved. Please retry.");
        }
        return clean;
    }

    private Map<String, Object> protectedFacts(Map<String, Object> value) {
        var copy = validatedCopy(value);
        copy.remove("summary");
        strip(copy.get("experience"), "bullets");
        strip(copy.get("projects"), "description");
        return copy;
    }
    private void strip(Object rows, String key) {
        if (rows instanceof List<?> list) for (Object row : list) ((Map<?, ?>) row).remove(key);
    }
    private String valuesOnly(Object value) {
        if (value instanceof Map<?, ?> map) return map.values().stream().map(this::valuesOnly).collect(java.util.stream.Collectors.joining(" "));
        if (value instanceof List<?> list) return list.stream().map(this::valuesOnly).collect(java.util.stream.Collectors.joining(" "));
        return value instanceof String text ? text : "";
    }
    private Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        var matcher = NUMBER.matcher(text);
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }
    private void rows(Object value, Set<String> keys, Set<String> arrays) {
        if (!(value instanceof List<?> list) || list.size() > 100) invalid();
        for (Object row : (List<?>) value) object(row, keys, arrays);
    }
    private void object(Object value, Set<String> keys, Set<String> arrays) {
        if (!(value instanceof Map<?, ?> map) || !keys.containsAll(map.keySet())) invalid();
        ((Map<?, ?>) value).forEach((key, item) -> { if (arrays.contains(key)) strings(item); else textValue(item); });
    }
    private void strings(Object value) {
        if (!(value instanceof List<?> list) || list.size() > 100) invalid();
        for (Object item : (List<?>) value) { if (!(item instanceof String)) invalid(); textValue(item); }
    }
    private void textValue(Object value) {
        if (value != null && (!(value instanceof String text) || text.length() > 10_000)) invalid();
    }
    private void invalid() { throw new BadRequestException("Resume contains unsupported fields or invalid field types."); }

    public String toText(Map<String, Object> content) {
        StringBuilder text = new StringBuilder();
        for (String section : List.of("personalInfo", "summary", "experience", "education", "skills", "projects")) {
            Object value = content.get(section);
            if (value == null || value instanceof List<?> list && list.isEmpty()) continue;
            text.append(section.equals("personalInfo") ? "CONTACT" : section.toUpperCase(Locale.ROOT)).append('\n');
            append(text, value);
            text.append('\n');
        }
        return text.toString().strip();
    }
    private void append(StringBuilder text, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.keySet().stream().map(Object::toString).sorted().forEach(key -> {
                Object item = map.get(key);
                if (item == null || item instanceof String s && s.isBlank()) return;
                String label = key.replaceAll("([a-z])([A-Z])", "$1 $2");
                text.append(Character.toUpperCase(label.charAt(0))).append(label.substring(1)).append(": ");
                if (item instanceof List<?>) text.append('\n');
                append(text, item);
            });
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String) text.append("• ");
                append(text, item);
                if (item instanceof Map<?, ?>) text.append('\n');
            }
        } else if (value instanceof String s && !s.isBlank()) text.append(s).append('\n');
    }
}
