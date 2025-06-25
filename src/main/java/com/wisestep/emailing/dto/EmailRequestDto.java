package com.wisestep.emailing.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

public class EmailRequestDto implements Serializable {

    @NotBlank(message = "Recipient email ('to') cannot be blank.")
    @Email(message = "Invalid 'to' email format.")
    private String to;

    @NotBlank(message = "Sender email ('from') cannot be blank.")
    @Email(message = "Invalid 'from' email format.")
    private String from;

    @NotBlank(message = "Subject cannot be blank.")
    private String subject;

    @NotBlank(message = "Body cannot be blank.")
    private String body;

    // Provider can be optional if a default is always assumed by the controller/service
    private String provider; // e.g., "sendgrid", "mailgun"

    // Attachments can be optional
    private List<AttachmentDto> attachments;

    private String userId; // Optional: Application-specific User ID, for linking UserEmailAccount

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
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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
