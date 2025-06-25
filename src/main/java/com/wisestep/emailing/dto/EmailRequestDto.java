package com.wisestep.emailing.dto;

import java.io.Serializable;
import java.util.List;

public class EmailRequestDto implements Serializable {
    private String to;
    private String from;
    private String subject;
    private String body;
    private String provider; // e.g., "sendgrid", "mailgun"
    private List<AttachmentDto> attachments;

    // Constructors
    public EmailRequestDto() {
    }

    public EmailRequestDto(String to, String from, String subject, String body, String provider, List<AttachmentDto> attachments) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.provider = provider;
        this.attachments = attachments;
    }

    // Getters and Setters
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public List<AttachmentDto> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentDto> attachments) {
        this.attachments = attachments;
    }
}
