package com.wisestep.emailing.domain;

import java.io.Serializable;
import java.util.List;

public class Email implements Serializable {

    private String to;
    private String from;
    private String subject;
    private String body;
    private List<EmailAttachment> attachments;

    public Email(String to, String from, String subject, String body, List<EmailAttachment> attachments) {
        this.to = to;
        this.from = from;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
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

    public List<EmailAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<EmailAttachment> attachments) {
        this.attachments = attachments;
    }
}

