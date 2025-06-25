package com.wisestep.emailing.dto;

import java.io.Serializable;

public class EmailResponseDto implements Serializable {
    private String status;
    private String message;

    // Constructors
    public EmailResponseDto() {
    }

    public EmailResponseDto(String status, String message) {
        this.status = status;
        this.message = message;
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
