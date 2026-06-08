package com.jobpilot.storage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * File storage abstraction — swappable between local, S3, MinIO.
 */
public interface FileStorageService {

    /**
     * Store a file and return the relative path.
     */
    String store(MultipartFile file, String directory);

    /**
     * Load a file as a Resource.
     */
    Resource load(String filePath);

    /**
     * Delete a file.
     */
    void delete(String filePath);
}
