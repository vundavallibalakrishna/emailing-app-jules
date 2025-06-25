package com.wisestep.emailing.domain.event;

// This enum can represent standardized event types if we want to map provider-specific events
// to a common set. However, for flexibility, EmailEvent.eventType will store the raw string
// from the provider. This enum can be used for querying or specific business logic if needed.
public enum EmailEventType {
    // SendGrid specific events (can be expanded or mapped)
    PROCESSED,      // Message has been received and is ready to be delivered.
    DROPPED,        // Message has been dropped, typically due to a recipient complaint or policy.
    DELIVERED,      // Message has been successfully delivered to the receiving server.
    DEFERRED,       // Recipient's email server temporarily rejected message.
    BOUNCE,         // Message permanently rejected by the receiving server (hard bounce).
    OPEN,           // Recipient has opened the HTML message. Tracking must be enabled.
    CLICK,          // Recipient clicked on a link within the message. Tracking must be enabled.
    SPAM_REPORT,    // Recipient marked message as spam.
    UNSUBSCRIBE,    // Recipient clicked on messages's subscription management link (if provided).
    GROUP_UNSUBSCRIBE, // Recipient unsubscribed from a specific group.
    GROUP_RESUBSCRIBE, // Recipient resubscribed to a specific group.

    // Could add more generic types or types from other providers later
    UNKNOWN;        // For events not explicitly mapped

    public static EmailEventType fromString(String text) {
        for (EmailEventType b : EmailEventType.values()) {
            if (b.name().equalsIgnoreCase(text)) {
                return b;
            }
        }
        // Log a warning or handle unknown event types
        // For now, defaulting to UNKNOWN or could throw an IllegalArgumentException
        System.err.println("Warning: Unknown EmailEventType string: " + text + ". Defaulting to UNKNOWN.");
        return UNKNOWN;
    }
}
