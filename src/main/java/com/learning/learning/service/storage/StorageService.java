package com.learning.learning.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Storage service interface for file operations.
 * Implementations can use local filesystem or cloud storage (S3).
 */
public interface StorageService {

    /**
     * Store a file and return the storage key/path
     *
     * @param file     The file to store
     * @param key      The storage key (path) for the file
     * @return The storage key where the file was saved
     */
    String store(MultipartFile file, String key) throws IOException;

    /**
     * Store a file from an input stream
     *
     * @param inputStream The input stream of the file
     * @param key         The storage key (path) for the file
     * @param contentType The MIME type of the file
     * @param size        The size of the file in bytes
     * @return The storage key where the file was saved
     */
    String store(InputStream inputStream, String key, String contentType, long size) throws IOException;

    /**
     * Retrieve a file as an input stream
     *
     * @param key The storage key of the file
     * @return InputStream of the file contents
     */
    InputStream retrieve(String key) throws IOException;

    /**
     * Delete a file
     *
     * @param key The storage key of the file to delete
     */
    void delete(String key) throws IOException;

    /**
     * Check if a file exists
     *
     * @param key The storage key to check
     * @return true if file exists
     */
    boolean exists(String key);

    /**
     * Get a URL for downloading the file.
     * For local storage, returns a relative path.
     * For S3, returns a pre-signed URL with temporary access.
     *
     * @param key              The storage key of the file
     * @param expirationMinutes How long the URL should be valid (for S3)
     * @return URL or path to access the file
     */
    String getDownloadUrl(String key, int expirationMinutes);

    /**
     * Get the storage type identifier
     *
     * @return "local" or "s3"
     */
    String getStorageType();
}
