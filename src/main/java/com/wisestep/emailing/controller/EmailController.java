package com.wisestep.emailing.controller;

import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.dto.BulkEmailRequestDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.JobScheduledResponseDto;
import com.wisestep.emailing.service.EmailSchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    private final EmailSchedulingService emailSchedulingService;

    @Autowired
    public EmailController(EmailSchedulingService emailSchedulingService) {
        this.emailSchedulingService = emailSchedulingService;
    }

    @PostMapping("/send")
    public ResponseEntity<JobScheduledResponseDto> scheduleBulkEmail(@Valid @RequestBody BulkEmailRequestDto bulkRequestDto) {
        if (bulkRequestDto == null || bulkRequestDto.getEmails() == null || bulkRequestDto.getEmails().isEmpty()) {
            return ResponseEntity.badRequest().body(new JobScheduledResponseDto("Request must contain at least one email.", null, null));
        }

        logger.info("Received bulk email scheduling request for {} emails.", bulkRequestDto.getEmails().size());
        List<Long> scheduledJobIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String batchId = UUID.randomUUID().toString(); // Optional batch tracking ID

        for (EmailRequestDto requestDto : bulkRequestDto.getEmails()) {
            try {
                EmailJob scheduledJob = emailSchedulingService.scheduleEmailJob(requestDto);
                scheduledJobIds.add(scheduledJob.getId());
            } catch (Exception e) {
                logger.error("Failed to schedule email job for recipient {}: {}", requestDto.getTo(), e.getMessage(), e);
                // Collect errors or decide on overall response strategy
                errors.add("Failed to schedule for " + requestDto.getTo() + ": " + e.getMessage());
            }
        }

        if (scheduledJobIds.isEmpty() && !errors.isEmpty()) {
            // All jobs failed to schedule
            String combinedErrors = errors.stream().collect(Collectors.joining("; "));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new JobScheduledResponseDto("All email jobs failed to schedule. Errors: " + combinedErrors, batchId, scheduledJobIds));
        } else if (!errors.isEmpty()) {
            // Some jobs scheduled, some failed
            String successMessage = scheduledJobIds.size() + " email(s) scheduled successfully.";
            String failureMessage = errors.size() + " email(s) failed to schedule. Errors: " + errors.stream().collect(Collectors.joining("; "));
            return ResponseEntity.status(HttpStatus.MULTI_STATUS) // 207 Multi-Status
                    .body(new JobScheduledResponseDto(successMessage + " " + failureMessage, batchId, scheduledJobIds));
        } else {
            // All jobs scheduled successfully
            return ResponseEntity.ok(new JobScheduledResponseDto(
                    scheduledJobIds.size() + " email(s) scheduled successfully.", batchId, scheduledJobIds
            ));
        }
    }
}
