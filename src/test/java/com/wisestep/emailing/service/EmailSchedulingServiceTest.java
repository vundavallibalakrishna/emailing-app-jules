package com.wisestep.emailing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.domain.job.EmailJobRepository;
import com.wisestep.emailing.domain.job.EmailJobStatus;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.job.EmailSendingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailSchedulingServiceTest {

    @Mock
    private EmailJobRepository emailJobRepository;

    @Mock
    private Scheduler quartzScheduler;

    // Real ObjectMapper is fine for this unit test as it's just for serialization/deserialization logic.
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmailSchedulingService emailSchedulingService;

    @BeforeEach
    void setUp() {
        // If objectMapper was @Mock, setup here. But using real one.
        // For direct injection of real ObjectMapper into emailSchedulingService if it wasn't constructor injected:
        // ReflectionTestUtils.setField(emailSchedulingService, "objectMapper", new ObjectMapper());
    }

    @Test
    void scheduleEmailJob_success() throws Exception {
        // Given
        AttachmentDto attachmentDto = new AttachmentDto("attach.txt", "text/plain", "YXR0YWNoY29udGVudA==");
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Subject", "Body", "sendgrid", Collections.singletonList(attachmentDto)
        );

        EmailJob savedEmailJob = new EmailJob();
        savedEmailJob.setId(1L);
        // Simulate what repository save would do (it sets ID and returns the object)
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> {
            EmailJob jobToSave = invocation.getArgument(0);
            jobToSave.setId(1L); // Simulate ID generation
            return jobToSave;
        });

        when(quartzScheduler.checkExists(any(JobKey.class))).thenReturn(false);


        // When
        EmailJob resultJob = emailSchedulingService.scheduleEmailJob(requestDto);

        // Then
        assertThat(resultJob).isNotNull();
        assertThat(resultJob.getId()).isEqualTo(1L);

        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository).save(jobCaptor.capture());
        EmailJob capturedJob = jobCaptor.getValue();

        assertThat(capturedJob.getRecipient()).isEqualTo(requestDto.getTo());
        assertThat(capturedJob.getFromAddress()).isEqualTo(requestDto.getFrom());
        assertThat(capturedJob.getSubject()).isEqualTo(requestDto.getSubject());
        assertThat(capturedJob.getBody()).isEqualTo(requestDto.getBody());
        assertThat(capturedJob.getProvider()).isEqualTo("sendgrid");
        assertThat(capturedJob.getStatus()).isEqualTo(EmailJobStatus.SCHEDULED);
        assertThat(capturedJob.getAttachmentsJson()).isEqualTo(objectMapper.writeValueAsString(requestDto.getAttachments()));

        ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(quartzScheduler).scheduleJob(jobDetailCaptor.capture(), triggerCaptor.capture());

        JobDetail capturedJobDetail = jobDetailCaptor.getValue();
        assertThat(capturedJobDetail.getJobClass()).isEqualTo(EmailSendingJob.class);
        assertThat(capturedJobDetail.getJobDataMap().getLong("emailJobId")).isEqualTo(1L);
        assertThat(capturedJobDetail.getKey().getName()).isEqualTo("emailJob_1");

        Trigger capturedTrigger = triggerCaptor.getValue();
        assertThat(capturedTrigger.getKey().getName()).isEqualTo("emailTrigger_1");
    }

    @Test
    void scheduleEmailJob_defaultProvider() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", null, null); // No provider
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quartzScheduler.checkExists(any(JobKey.class))).thenReturn(false);


        emailSchedulingService.scheduleEmailJob(requestDto);

        ArgumentCaptor<EmailJob> jobCaptor = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getProvider()).isEqualTo("sendgrid"); // Check default provider
    }

    @Test
    void scheduleEmailJob_jobAlreadyExists() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> {
            EmailJob job = invocation.getArgument(0);
            job.setId(2L);
            return job;
        });
        // Simulate job already exists
        when(quartzScheduler.checkExists(any(JobKey.class))).thenReturn(true);

        emailSchedulingService.scheduleEmailJob(requestDto);

        // Verify scheduleJob is still called (current logic logs a warning but proceeds)
        verify(quartzScheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
        // Could add log verification here if a testing logger was injected/used
    }


    @Test
    void scheduleEmailJob_schedulerThrowsException() throws SchedulerException {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> {
            EmailJob job = invocation.getArgument(0);
            job.setId(3L);
            return job;
        });
        when(quartzScheduler.checkExists(any(JobKey.class))).thenReturn(false);
        doThrow(new SchedulerException("Test scheduler error")).when(quartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

        Exception exception = assertThrows(SchedulerException.class, () -> {
            emailSchedulingService.scheduleEmailJob(requestDto);
        });

        assertThat(exception.getMessage()).contains("Test scheduler error");
    }
}
