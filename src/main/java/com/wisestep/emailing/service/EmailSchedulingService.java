package com.wisestep.emailing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.domain.job.EmailJobRepository;
import com.wisestep.emailing.domain.job.EmailJobStatus;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.job.EmailSendingJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmailSchedulingService {

    private static final Logger logger = LoggerFactory.getLogger(EmailSchedulingService.class);

    private final EmailJobRepository emailJobRepository;
    private final Scheduler quartzScheduler;
    private final ObjectMapper objectMapper; // For serializing attachments

    @Autowired
    public EmailSchedulingService(EmailJobRepository emailJobRepository,
                                  Scheduler quartzScheduler,
                                  ObjectMapper objectMapper) {
        this.emailJobRepository = emailJobRepository;
        this.quartzScheduler = quartzScheduler;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmailJob scheduleEmailJob(EmailRequestDto requestDto) throws SchedulerException, JsonProcessingException {
        EmailJob emailJob = new EmailJob();
        emailJob.setRecipient(requestDto.getTo());
        emailJob.setFromAddress(requestDto.getFrom());
        emailJob.setSubject(requestDto.getSubject());
        emailJob.setBody(requestDto.getBody());
        emailJob.setProvider(requestDto.getProvider() != null ? requestDto.getProvider().toLowerCase() : "sendgrid"); // Default provider
        emailJob.setUserId(requestDto.getUserId()); // Populate userId
        emailJob.setStatus(EmailJobStatus.SCHEDULED);

        if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
            emailJob.setAttachmentsJson(convertAttachmentsToJson(requestDto.getAttachments()));
        }

        EmailJob savedJob = emailJobRepository.save(emailJob);
        logger.info("Saved EmailJob with ID: {}", savedJob.getId());

        scheduleQuartzJob(savedJob.getId());
        logger.info("Scheduled Quartz job for EmailJob ID: {}", savedJob.getId());

        return savedJob;
    }

    private String convertAttachmentsToJson(List<AttachmentDto> attachments) throws JsonProcessingException {
        return objectMapper.writeValueAsString(attachments);
    }

    private void scheduleQuartzJob(Long emailJobId) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(EmailSendingJob.class)
                .withIdentity("emailJob_" + emailJobId, "email-jobs")
                .usingJobData("emailJobId", emailJobId)
                .storeDurably(false) // false: delete job when no more triggers, true: keep job
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity("emailTrigger_" + emailJobId, "email-triggers")
                .startNow() // Schedule to run immediately
                .build();

        try {
            if (quartzScheduler.checkExists(jobDetail.getKey())) {
                logger.warn("Job with key {} already exists. It will not be rescheduled unless it's updated.", jobDetail.getKey());
                // Potentially delete and reschedule, or update, depending on desired behavior
                // For now, if it exists, we assume it might be from a previous attempt that failed before completion
                // or a concurrent request. To be safe, we can delete and add.
                // quartzScheduler.deleteJob(jobDetail.getKey());
            }
            quartzScheduler.scheduleJob(jobDetail, trigger);
            logger.info("Quartz job scheduled for emailJobId: {}. JobKey: {}, TriggerKey: {}", emailJobId, jobDetail.getKey(), trigger.getKey());
        } catch (SchedulerException e) {
            logger.error("Error scheduling Quartz job for emailJobId: {}", emailJobId, e);
            // Consider how to handle this failure. Should the EmailJob be marked as FAILED?
            // Or should this bubble up to controller to inform user?
            // For now, re-throw to indicate scheduling failure.
            throw e;
        }
    }
}
