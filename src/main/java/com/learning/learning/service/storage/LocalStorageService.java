package com.learning.learning.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem storage implementation.
 * Used for development and when S3 is not configured.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String key) throws IOException {
        return store(file.getInputStream(), key, file.getContentType(), file.getSize());
    }

    @Override
    public String store(InputStream inputStream, String key, String contentType, long size) throws IOException {
        Path filePath = Paths.get(uploadDir, key);

        // Create parent directories if they don't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Copy file to destination
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("File stored locally at: {}", filePath);
        return key;
    }

    @Override
    public InputStream retrieve(String key) throws IOException {
        Path filePath = Paths.get(uploadDir, key);

        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + key);
        }

        return Files.newInputStream(filePath);
    }

    @Override
    public void delete(String key) throws IOException {
        Path filePath = Paths.get(uploadDir, key);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            logger.info("File deleted: {}", filePath);
        } else {
            logger.warn("File not found for deletion: {}", key);
        }
    }

    @Override
    public boolean exists(String key) {
        Path filePath = Paths.get(uploadDir, key);
        return Files.exists(filePath);
    }

    @Override
    public String getDownloadUrl(String key, int expirationMinutes) {
        // For local storage, return the relative path
        // The controller will handle serving the file
        return "/documents/download/" + key;
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    /**
     * Get the full filesystem path for a storage key
     */
    public Path getFullPath(String key) {
        return Paths.get(uploadDir, key);
    }
}
