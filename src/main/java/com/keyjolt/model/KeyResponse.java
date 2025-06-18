package com.keyjolt.model;

import java.util.List;
import java.util.ArrayList;

/**
 * Response object containing generated key information and download links
 */
public class KeyResponse {
    
    private boolean success;
    private String message;
    private String keyId;
    private List<FileInfo> files;
    private String error;
    
    public KeyResponse() {
        this.files = new ArrayList<>();
    }
    
    public KeyResponse(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }
    
    // Success response with files
    public static KeyResponse success(String message, String keyId, List<FileInfo> files) {
        KeyResponse response = new KeyResponse(true, message);
        response.setKeyId(keyId);
        response.setFiles(files);
        return response;
    }
    
    // Error response
    public static KeyResponse error(String error) {
        KeyResponse response = new KeyResponse(false, "Key generation failed");
        response.setError(error);
        return response;
    }
    
    // Getters and setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
    
    public List<FileInfo> getFiles() {
        return files;
    }
    
    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    /**
     * Inner class to represent file information
     */
    public static class FileInfo {
        private String filename;
        private String downloadUrl;
        private String type; // "pgp_public", "pgp_private", "ssh_public", "ssh_private"
        private long size;
        
        public FileInfo() {}
        
        public FileInfo(String filename, String downloadUrl, String type, long size) {
            this.filename = filename;
            this.downloadUrl = downloadUrl;
            this.type = type;
            this.size = size;
        }
        
        // Getters and setters
        public String getFilename() {
            return filename;
        }
        
        public void setFilename(String filename) {
            this.filename = filename;
        }
        
        public String getDownloadUrl() {
            return downloadUrl;
        }
        
        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
    }
}
