package com.wisestep.emailing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.event.EmailEvent;
import com.wisestep.emailing.domain.event.EmailEventRepository;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.domain.job.EmailJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookEventService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookEventService.class);

    private final EmailEventRepository emailEventRepository;
    private final EmailJobRepository emailJobRepository; // To link event to job
    private final ObjectMapper objectMapper; // To store full event details as JSON

    @Autowired
    public WebhookEventService(EmailEventRepository emailEventRepository,
                               EmailJobRepository emailJobRepository,
                               ObjectMapper objectMapper) {
        this.emailEventRepository = emailEventRepository;
        this.emailJobRepository = emailJobRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processSendGridEvents(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            logger.info("No SendGrid events to process.");
            return;
        }

        for (Map<String, Object> eventMap : events) {
            try {
                EmailEvent emailEvent = new EmailEvent();
                emailEvent.setProvider("sendgrid");

                // Safely extract fields from the map
                String sgMessageId = (String) eventMap.get("sg_message_id");
                if (sgMessageId == null || sgMessageId.isEmpty()) {
                    // Try unique args if sg_message_id is missing (though it should usually be there)
                    // For SendGrid, sg_message_id is the primary identifier.
                    // If we passed a custom arg like 'email_job_id' we could use that.
                    // For now, if sg_message_id is missing, we might not be able to link it effectively.
                    logger.warn("SendGrid event received without sg_message_id. Event data: {}", eventMap);
                    // Continue to next event or decide on error handling
                    // For now, we'll try to save it without linking to an EmailJob directly via sg_message_id
                }
                emailEvent.setProviderMessageId(sgMessageId);


                String eventType = (String) eventMap.get("event");
                emailEvent.setEventType(eventType != null ? eventType.toLowerCase() : "unknown");

                Object timestampObj = eventMap.get("timestamp");
                if (timestampObj instanceof Number) {
                    emailEvent.setEventTimestampFromUnix(((Number) timestampObj).longValue());
                } else {
                    logger.warn("Invalid or missing timestamp for event with sg_message_id: {}. Using current time.", sgMessageId);
                    emailEvent.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
                }

                emailEvent.setRecipient((String) eventMap.get("email"));
                emailEvent.setUrl((String) eventMap.get("url"));
                emailEvent.setIpAddress((String) eventMap.get("ip"));
                emailEvent.setUserAgent((String) eventMap.get("useragent"));
                emailEvent.setReason((String) eventMap.get("reason"));
                // Add other SendGrid specific fields as needed, e.g., asm_group_id for unsubscribe events

                try {
                    emailEvent.setDetailsJson(objectMapper.writeValueAsString(eventMap));
                } catch (JsonProcessingException e) {
                    logger.error("Error serializing SendGrid event details to JSON for sg_message_id: {}", sgMessageId, e);
                    emailEvent.setDetailsJson("{\"error\":\"Could not serialize event details\"}");
                }

                // Attempt to link to an EmailJob
                if (sgMessageId != null) {
                    // SendGrid's sg_message_id might contain a suffix like ".filterd" or ". agadsg"
                    // We need the core part that matches what we stored.
                    // Typically, the X-Message-ID header we store is the core sg_message_id.
                    // Example: sg_message_id can be "sendgrid_internal_id.filterd" or just "sendgrid_internal_id"
                    // Let's assume our stored messageId in EmailJob is the core one.
                    String coreSgMessageId = sgMessageId.split("\\.")[0];
                    List<EmailJob> jobs = emailJobRepository.findByMessageId(coreSgMessageId);
                    if (!jobs.isEmpty()) {
                        if (jobs.size() > 1) {
                            logger.warn("Multiple EmailJobs found for providerMessageId: {}. Using the first one.", coreSgMessageId);
                        }
                        emailEvent.setEmailJob(jobs.get(0));
                        // Optionally, update EmailJob status based on eventType here
                        // e.g., if eventType is "delivered", update EmailJob status.
                        // This requires careful state management.
                    } else {
                        logger.warn("No EmailJob found for providerMessageId: {} (derived from sg_message_id: {})", coreSgMessageId, sgMessageId);
                    }
                }


                emailEventRepository.save(emailEvent);
                logger.info("Saved SendGrid event: Type='{}', Recipient='{}', ProviderMessageID='{}'",
                        emailEvent.getEventType(), emailEvent.getRecipient(), emailEvent.getProviderMessageId());

            } catch (Exception e) {
                logger.error("Error processing a SendGrid event: {}. Event data: {}", e.getMessage(), eventMap, e);
                // Decide if one failed event should stop processing others in the batch.
                // For now, continue with the next event.
            }
        }
    }
}
