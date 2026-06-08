package com.jobpilot.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured resume data extracted by AI from raw text.
 * Used as the structured output target for OpenAI parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedResume {

    @JsonProperty(required = true)
    private PersonalInfo personalInfo;

    @JsonProperty(required = true)
    private List<Experience> experience;

    @JsonProperty(required = true)
    private List<Education> education;

    @JsonProperty(required = true)
    private List<String> skills;

    private List<Project> projects;

    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalInfo {
        private String name;
        private String email;
        private String phone;
        private String location;
        private String linkedin;
        private String website;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Experience {
        private String company;
        private String title;
        private String location;
        private String startDate;
        private String endDate;
        private List<String> bullets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Education {
        private String institution;
        private String degree;
        private String field;
        private String startDate;
        private String endDate;
        private String gpa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project {
        private String name;
        private String description;
        private List<String> technologies;
        private String url;
    }
}
