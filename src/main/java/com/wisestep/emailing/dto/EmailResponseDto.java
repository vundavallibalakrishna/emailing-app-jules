package com.wisestep.emailing.dto;

import java.io.Serializable;

public class EmailResponseDto implements Serializable {
    private String status;
    private String message;
    private String providerMessageId; // ID from the email provider (e.g., SendGrid's X-Message-Id)

    // Constructors
    public EmailResponseDto() {
    }

    public EmailResponseDto(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public EmailResponseDto(String status, String message, String providerMessageId) {
        this.status = status;
        this.message = message;
        this.providerMessageId = providerMessageId;
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
