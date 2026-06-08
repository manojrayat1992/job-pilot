package com.jobpilot.resume.service;

import com.jobpilot.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Extracts raw text from PDF/DOCX files using Apache Tika.
 */
@Slf4j
@Service
public class ResumeParserService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final Tika tika = new Tika();

    /**
     * Extract raw text from an uploaded resume file.
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        try (InputStream is = file.getInputStream()) {
            String text = tika.parseToString(is);
            if (text == null || text.isBlank()) {
                throw new BadRequestException("Could not extract text from the uploaded file. The file may be image-based or empty.");
            }
            log.info("Extracted {} characters from file: {}", text.length(), file.getOriginalFilename());
            return text.trim();
        } catch (IOException | TikaException e) {
            log.error("Failed to parse file: {}", file.getOriginalFilename(), e);
            throw new BadRequestException("Failed to parse the uploaded file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("Invalid file type. Allowed: PDF, DOCX. Got: " + contentType);
        }
    }
}
