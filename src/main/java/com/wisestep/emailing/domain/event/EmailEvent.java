package com.wisestep.emailing.domain.event;

import com.wisestep.emailing.domain.job.EmailJob; // Assuming EmailJob exists

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;


@Entity
@Table(name = "email_events", indexes = {
        @Index(name = "idx_emailevent_providermessageid", columnList = "providerMessageId"),
        @Index(name = "idx_emailevent_recipient", columnList = "recipient"),
        @Index(name = "idx_emailevent_eventtype", columnList = "eventType"),
        @Index(name = "idx_emailevent_eventtimestamp", columnList = "eventTimestamp")
})
public class EmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_job_id", foreignKey = @ForeignKey(name = "fk_emailevent_emailjob"))
    private EmailJob emailJob; // Link back to the original job if possible

    @Column(nullable = false)
    private String providerMessageId; // e.g., SendGrid's sg_message_id

    @Column(nullable = false, length = 50)
    private String provider; // e.g., "sendgrid", "elasticemail"

    @Column(nullable = false, length = 50)
    private String eventType; // Raw event type from provider (e.g., "delivered", "open", "bounce")

    @Column(nullable = false)
    private LocalDateTime eventTimestamp; // Timestamp from the event payload

    @Column(nullable = false)
    private String recipient;

    @Column(length = 2048) // URLs can be long
    private String url; // For click events

    @Column(length = 45) // Max length for IPv6 is 39, IPv4 is 15
    private String ipAddress; // User's IP for open/click

    @Column(length = 512)
    private String userAgent; // User's agent for open/click

    @Column(length = 1024)
    private String reason; // For bounce, dropped events

    @Lob
    private String detailsJson; // Full webhook payload or additional non-standardized details

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmailJob getEmailJob() {
        return emailJob;
    }

    public void setEmailJob(EmailJob emailJob) {
        this.emailJob = emailJob;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(LocalDateTime eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    // Helper to set from Unix timestamp (long) often provided by webhooks
    public void setEventTimestampFromUnix(long unixTimestamp) {
        this.eventTimestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTimestamp), ZoneOffset.UTC);
    }


    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
