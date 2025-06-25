package com.wisestep.emailing.dto;

import java.io.Serializable;

public class AttachmentDto implements Serializable {
    private String filename;
    private String contentType;
    private String data; // Base64 encoded content

    // Constructors
    public AttachmentDto() {
    }

    public AttachmentDto(String filename, String contentType, String data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
    }

    // Getters and Setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
