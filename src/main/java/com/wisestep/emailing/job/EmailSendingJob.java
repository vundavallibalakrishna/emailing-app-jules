package com.wisestep.emailing.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.domain.job.EmailJobRepository;
import com.wisestep.emailing.domain.job.EmailJobStatus;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Important for DB updates

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component // Make it a Spring-managed bean
public class EmailSendingJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendingJob.class);

    // Dependencies will be injected by AutowiringSpringBeanJobFactory
    @Autowired
    private EmailJobRepository emailJobRepository;

    @Autowired
    private Map<String, EmailSender> emailSenders;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String DEFAULT_PROVIDER_KEY = "sendgrid";


    @Override
    @Transactional // Manages transaction for the job execution, ensuring DB updates are atomic for this job run
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        Long emailJobId = jobDataMap.getLong("emailJobId");
        logger.info("Executing EmailSendingJob for emailJobId: {}", emailJobId);

        Optional<EmailJob> jobOptional = emailJobRepository.findById(emailJobId);
        if (!jobOptional.isPresent()) {
            logger.error("EmailJob with ID {} not found. Cannot execute.", emailJobId);
            // Optionally throw an exception to indicate Quartz job failure if this is critical
            // throw new JobExecutionException("EmailJob not found: " + emailJobId, false); // false = don't refire immediately
            return;
        }

        EmailJob emailJob = jobOptional.get();

        // Prevent re-processing if already completed or actively processing by another thread (though Quartz usually handles this)
        if (emailJob.getStatus() == EmailJobStatus.SENT || emailJob.getStatus() == EmailJobStatus.PROCESSING) {
             if(emailJob.getStatus() == EmailJobStatus.PROCESSING) {
                // Potentially a long-running job that Quartz might try to refire if misconfigured, or a retry.
                // Or, it could be that the previous attempt crashed after setting to PROCESSING but before completion.
                // For now, we'll log and skip. More sophisticated recovery might be needed.
                logger.warn("EmailJob ID {} is already in {} status. Skipping execution.", emailJobId, emailJob.getStatus());
                return;
            }
            logger.info("EmailJob ID {} is already SENT. Skipping execution.", emailJobId);
            return;
        }


        emailJob.setStatus(EmailJobStatus.PROCESSING);
        emailJobRepository.saveAndFlush(emailJob); // Save status change immediately

        try {
            List<AttachmentDto> attachments = deserializeAttachments(emailJob.getAttachmentsJson());
            EmailRequestDto emailRequest = new EmailRequestDto(
                    emailJob.getRecipient(),
                    emailJob.getFromAddress(),
                    emailJob.getSubject(),
                    emailJob.getBody(),
                    emailJob.getProvider(),
                    attachments
            );

            String providerKey = emailJob.getProvider() != null ? emailJob.getProvider().toLowerCase() : DEFAULT_PROVIDER_KEY;
            EmailSender emailSender = emailSenders.get(providerKey);

            if (emailSender == null) {
                throw new JobExecutionException("No EmailSender found for provider: " + providerKey);
            }

            logger.info("Attempting to send email for job ID {} via provider {}", emailJobId, providerKey);
            EmailResponseDto response = emailSender.sendEmail(emailRequest);

            if ("Success".equals(response.getStatus())) {
                emailJob.setStatus(EmailJobStatus.SENT);
                // Try to extract messageId if present in the success message (this is a bit fragile)
                if (response.getMessage() != null && response.getMessage().contains("Message ID: ")) {
                    emailJob.setMessageId(response.getMessage().substring(response.getMessage().indexOf("Message ID: ") + "Message ID: ".length()));
                } else {
                     emailJob.setMessageId(response.getMessage()); // Store the whole message if no specific ID pattern
                }
                emailJob.setErrorMessage(null);
                logger.info("EmailJob ID {} processed successfully. Status: SENT. Provider Message: {}", emailJobId, response.getMessage());
            } else {
                emailJob.setStatus(EmailJobStatus.FAILED);
                emailJob.setErrorMessage(response.getMessage());
                logger.error("EmailJob ID {} failed. Status: FAILED. Error: {}", emailJobId, response.getMessage());
            }
        } catch (Exception e) {
            logger.error("Exception during EmailSendingJob execution for job ID {}: {}", emailJobId, e.getMessage(), e);
            emailJob.setStatus(EmailJobStatus.FAILED);
            emailJob.setErrorMessage("Job execution failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            // To make Quartz retry the job upon exception, throw JobExecutionException and set refire to true
            // For now, we mark as FAILED and don't automatically refire from here. Retries could be a separate strategy.
            // throw new JobExecutionException("Failed to process email job " + emailJobId, e, false); // false = don't refire from this failure
        } finally {
            emailJobRepository.save(emailJob);
            logger.info("Finished processing EmailSendingJob for emailJobId: {}. Final status: {}", emailJobId, emailJob.getStatus());
        }
    }

    private List<AttachmentDto> deserializeAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(attachmentsJson, new TypeReference<List<AttachmentDto>>() {});
        } catch (Exception e) {
            logger.error("Failed to deserialize attachments JSON: {}", attachmentsJson, e);
            // Depending on requirements, either return empty list or propagate error
            return Collections.emptyList(); // Or throw a custom exception
        }
    }
}
