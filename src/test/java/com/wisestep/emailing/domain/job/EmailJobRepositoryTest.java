package com.wisestep.emailing.domain.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.dto.AttachmentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest // Configures H2, Hibernate, Spring Data JPA for testing
// @ActiveProfiles("test") // If you have a specific test profile with different DB settings
public class EmailJobRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmailJobRepository emailJobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void whenSaveEmailJob_thenFindById_returnsSameJob() throws JsonProcessingException {
        // Given
        EmailJob emailJob = new EmailJob();
        emailJob.setRecipient("test@example.com");
        emailJob.setFromAddress("sender@example.com");
        emailJob.setSubject("Test Subject");
        emailJob.setBody("Test Body");
        emailJob.setProvider("sendgrid");
        emailJob.setStatus(EmailJobStatus.SCHEDULED);

        AttachmentDto attachment = new AttachmentDto("file.txt", "text/plain", "base64data");
        List<AttachmentDto> attachments = Collections.singletonList(attachment);
        emailJob.setAttachmentsJson(objectMapper.writeValueAsString(attachments));

        // When
        EmailJob savedJob = entityManager.persistAndFlush(emailJob); // Use TestEntityManager for precise control
        EmailJob foundJob = emailJobRepository.findById(savedJob.getId()).orElse(null);

        // Then
        assertThat(foundJob).isNotNull();
        assertThat(foundJob.getId()).isEqualTo(savedJob.getId());
        assertThat(foundJob.getRecipient()).isEqualTo("test@example.com");
        assertThat(foundJob.getStatus()).isEqualTo(EmailJobStatus.SCHEDULED);
        assertThat(foundJob.getAttachmentsJson()).isEqualTo(objectMapper.writeValueAsString(attachments));
        assertThat(foundJob.getCreatedAt()).isNotNull(); // Check @PrePersist
        assertThat(foundJob.getUpdatedAt()).isNotNull(); // Check @PrePersist

        // Test @PreUpdate
        foundJob.setStatus(EmailJobStatus.PROCESSING);
        EmailJob updatedJob = entityManager.persistAndFlush(foundJob);
        assertThat(updatedJob.getUpdatedAt()).isNotEqualTo(updatedJob.getCreatedAt());
    }
}
