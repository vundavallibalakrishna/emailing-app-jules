package com.wisestep.emailing.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {

    // Find by provider's message ID, useful for linking events to an original message
    List<EmailEvent> findByProviderMessageId(String providerMessageId);

    // Find by recipient and event type, could be useful for analytics
    List<EmailEvent> findByRecipientAndEventType(String recipient, String eventType);

    // Potentially find by EmailJob's ID if we establish that link
    List<EmailEvent> findByEmailJobId(Long emailJobId);

}
