package com.wisestep.emailing.domain.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {
    // We can add custom query methods here if needed later,
    // e.g., findByStatus(EmailJobStatus status);
}
