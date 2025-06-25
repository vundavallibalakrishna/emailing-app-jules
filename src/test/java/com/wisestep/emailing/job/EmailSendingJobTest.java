package com.wisestep.emailing.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.domain.job.EmailJobRepository;
import com.wisestep.emailing.domain.job.EmailJobStatus;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailSendingJobTest {

    @Mock
    private EmailJobRepository emailJobRepository;

    @Mock
    private EmailSender mockSendGridSender; // Specific mock for one provider

    @Mock
    private EmailSender mockSmtpSender; // Specific mock for another

    @Spy // Use Spy for real ObjectMapper, or @Mock if its methods need mocking
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmailSendingJob emailSendingJob;

    @Mock
    private JobExecutionContext jobExecutionContext;

    @Mock
    private JobDetail jobDetail;

    private JobDataMap jobDataMap;

    private Map<String, EmailSender> emailSendersMap;


    @BeforeEach
    void setUp() {
        jobDataMap = new JobDataMap();
        when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);

        // Setup the map of email senders that would be injected into EmailSendingJob
        emailSendersMap = new HashMap<>();
        emailSendersMap.put("sendgrid", mockSendGridSender);
        emailSendersMap.put("smtp", mockSmtpSender);
        // Inject the map into the job instance
        ReflectionTestUtils.setField(emailSendingJob, "emailSenders", emailSendersMap);
        ReflectionTestUtils.setField(emailSendingJob, "objectMapper", objectMapper); // Ensure spy is injected
    }

    private EmailJob createTestEmailJob(Long id, String provider, EmailJobStatus status, String attachmentsJson) {
        EmailJob job = new EmailJob();
        job.setId(id);
        job.setRecipient("to@example.com");
        job.setFromAddress("from@example.com");
        job.setSubject("Test Subject");
        job.setBody("Test Body");
        job.setProvider(provider);
        job.setStatus(status);
        job.setAttachmentsJson(attachmentsJson);
        return job;
    }

    @Test
    void execute_sendGridSuccess() throws JobExecutionException, JsonProcessingException {
        // Given
        long jobId = 1L;
        List<AttachmentDto> attachments = Collections.singletonList(new AttachmentDto("file.txt", "text/plain", "base64data"));
        String attachmentsJson = objectMapper.writeValueAsString(attachments);
        EmailJob emailJob = createTestEmailJob(jobId, "sendgrid", EmailJobStatus.SCHEDULED, attachmentsJson);
        jobDataMap.put("emailJobId", jobId);

        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));
        when(mockSendGridSender.sendEmail(any(EmailRequestDto.class)))
                .thenReturn(new EmailResponseDto("Success", "Email sent via SendGrid. Message ID: sg-123"));

        // When
        emailSendingJob.execute(jobExecutionContext);

        // Then
        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository, times(2)).save(jobCaptor.capture()); // Once for PROCESSING, once for SENT/FAILED

        List<EmailJob> savedJobs = jobCaptor.getAllValues();
        assertThat(savedJobs.get(0).getStatus()).isEqualTo(EmailJobStatus.PROCESSING); // First save
        assertThat(savedJobs.get(1).getStatus()).isEqualTo(EmailJobStatus.SENT);     // Second save
        assertThat(savedJobs.get(1).getMessageId()).isEqualTo("sg-123");
        assertThat(savedJobs.get(1).getErrorMessage()).isNull();

        ArgumentCaptor<EmailRequestDto> requestCaptor = ArgumentCaptor.forClass(EmailRequestDto.class);
        verify(mockSendGridSender).sendEmail(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAttachments()).hasSize(1);
        assertThat(requestCaptor.getValue().getAttachments().get(0).getFilename()).isEqualTo("file.txt");
    }

    @Test
    void execute_smtpFailure() throws JobExecutionException {
        // Given
        long jobId = 2L;
        EmailJob emailJob = createTestEmailJob(jobId, "smtp", EmailJobStatus.SCHEDULED, null);
        jobDataMap.put("emailJobId", jobId);

        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));
        when(mockSmtpSender.sendEmail(any(EmailRequestDto.class)))
                .thenReturn(new EmailResponseDto("Error", "SMTP Auth Failed"));

        // When
        emailSendingJob.execute(jobExecutionContext);

        // Then
        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository, times(2)).save(jobCaptor.capture());

        List<EmailJob> savedJobs = jobCaptor.getAllValues();
        assertThat(savedJobs.get(0).getStatus()).isEqualTo(EmailJobStatus.PROCESSING);
        assertThat(savedJobs.get(1).getStatus()).isEqualTo(EmailJobStatus.FAILED);
        assertThat(savedJobs.get(1).getErrorMessage()).isEqualTo("SMTP Auth Failed");
        assertThat(savedJobs.get(1).getMessageId()).isNull();
    }

    @Test
    void execute_jobNotFound() throws JobExecutionException {
        // Given
        long jobId = 99L;
        jobDataMap.put("emailJobId", jobId);
        when(emailJobRepository.findById(jobId)).thenReturn(Optional.empty());

        // When
        emailSendingJob.execute(jobExecutionContext);

        // Then
        verify(emailJobRepository, never()).save(any(EmailJob.class));
        verify(mockSendGridSender, never()).sendEmail(any(EmailRequestDto.class));
    }

    @Test
    void execute_providerNotFound() throws JobExecutionException {
        // Given
        long jobId = 3L;
        EmailJob emailJob = createTestEmailJob(jobId, "unknownprovider", EmailJobStatus.SCHEDULED, null);
        jobDataMap.put("emailJobId", jobId);
        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));

        // When
        emailSendingJob.execute(jobExecutionContext); // Should throw JobExecutionException internally, caught by job's own try-catch

        // Then
        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository, times(2)).save(jobCaptor.capture()); // PROCESSING, then FAILED due to exception
        List<EmailJob> savedJobs = jobCaptor.getAllValues();
        assertThat(savedJobs.get(1).getStatus()).isEqualTo(EmailJobStatus.FAILED);
        assertThat(savedJobs.get(1).getErrorMessage()).contains("No EmailSender found for provider: unknownprovider");
    }

    @Test
    void execute_attachmentDeserializationError() throws JobExecutionException, JsonProcessingException {
        long jobId = 4L;
        String invalidJson = "this is not json";
        EmailJob emailJob = createTestEmailJob(jobId, "sendgrid", EmailJobStatus.SCHEDULED, invalidJson);
        jobDataMap.put("emailJobId", jobId);

        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));
        // We don't need to mock objectMapper.readValue if it's a spy and we want to test its real behavior with bad JSON.
        // The job's internal catch block for deserialization should handle this.

        // When
        emailSendingJob.execute(jobExecutionContext);

        // Then
        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository, times(2)).save(jobCaptor.capture());
        List<EmailJob> savedJobs = jobCaptor.getAllValues();
        assertThat(savedJobs.get(1).getStatus()).isEqualTo(EmailJobStatus.FAILED); // Fails because attachments are empty, but sender is called
        assertThat(savedJobs.get(1).getErrorMessage()).contains("Job execution failed: com.fasterxml.jackson.core.JsonParseException");

        // The sender would still be called but with an empty attachment list due to current deserialization error handling
        verify(mockSendGridSender).sendEmail(argThat(req -> req.getAttachments().isEmpty()));
    }


    @Test
    void execute_jobAlreadySent() throws JobExecutionException {
        long jobId = 5L;
        EmailJob emailJob = createTestEmailJob(jobId, "sendgrid", EmailJobStatus.SENT, null);
        jobDataMap.put("emailJobId", jobId);
        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));

        emailSendingJob.execute(jobExecutionContext);

        verify(emailJobRepository, never()).save(any(EmailJob.class)); // Should not attempt to save
        verify(mockSendGridSender, never()).sendEmail(any(EmailRequestDto.class));
    }

    @Test
    void execute_jobAlreadyProcessing() throws JobExecutionException {
        // This tests the guard for PROCESSING status.
        long jobId = 6L;
        EmailJob emailJob = createTestEmailJob(jobId, "sendgrid", EmailJobStatus.PROCESSING, null);
        jobDataMap.put("emailJobId", jobId);
        when(emailJobRepository.findById(jobId)).thenReturn(Optional.of(emailJob));

        emailSendingJob.execute(jobExecutionContext);

        verify(emailJobRepository, never()).save(any(EmailJob.class));
        verify(mockSendGridSender, never()).sendEmail(any(EmailRequestDto.class));
    }
}
