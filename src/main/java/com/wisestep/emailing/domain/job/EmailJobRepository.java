package com.wisestep.emailing.domain.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {
    // We can add custom query methods here if needed later,
    // e.g., findByStatus(EmailJobStatus status);

    // Find by the provider's message ID. This ID should be unique per provider.
    // Since multiple jobs could theoretically have the same messageId if from different providers (unlikely with UUIDs but possible),
    // it might be safer to query by provider AND messageId if that becomes an issue.
    // For now, assuming messageId from a specific provider like SendGrid is unique enough for our linking.
    List<EmailJob> findByMessageId(String messageId);
}
