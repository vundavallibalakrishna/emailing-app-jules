package com.wisestep.emailing.service.impl;

import com.mailchimp.clients.MailchimpClient;
import com.mailchimp.clients.Messages;
import com.mailchimp.clients.models.Message;
import com.mailchimp.clients.models.MessageAttachment;
import com.mailchimp.clients.models.MessageRecipient;
import com.mailchimp.clients.models.MessageResponse;
import com.mailchimp.clients.models.RecipientType;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Service("mailchimp")
public class MailchimpEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(MailchimpEmailSender.class);

    @Value("${mailchimp.api.key}")
    private String mailchimpApiKey;

    // The Mailchimp Transactional SDK uses a MailchimpClient which can be configured once.
    // However, the Messages API client is obtained from this MailchimpClient.
    // For simplicity, we can create the client inside the send method or initialize it if it's thread-safe.
    // The SDK examples show creating MailchimpClient and then Messages client per operation or as needed.

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        if (mailchimpApiKey == null || mailchimpApiKey.isEmpty() || "YOUR_MAILCHIMP_API_KEY".equals(mailchimpApiKey)) {
            logger.error("Mailchimp API Key is not configured.");
            return new EmailResponseDto("Error", "Mailchimp API Key is not configured.");
        }

        MailchimpClient mailchimpClient = new MailchimpClient(mailchimpApiKey);
        Messages messagesApi = mailchimpClient.messages();

        Message message = new Message();
        message.setFromEmail(requestDto.getFrom());
        // message.setFromName("Optional From Name"); // Can be added if available in DTO
        message.setSubject(requestDto.getSubject());
        message.setHtml(requestDto.getBody()); // Assuming body is HTML
        // message.setText("Plain text content if you have it separately");

        MessageRecipient recipient = new MessageRecipient();
        recipient.setEmail(requestDto.getTo());
        recipient.setType(RecipientType.TO);
        message.setTo(Collections.singletonList(recipient));

        // Handle attachments
        if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
            List<MessageAttachment> attachments = new ArrayList<>();
            for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                MessageAttachment mailchimpAttachment = new MessageAttachment();
                mailchimpAttachment.setType(attachmentDto.getContentType());
                mailchimpAttachment.setName(attachmentDto.getFilename());
                mailchimpAttachment.setContent(attachmentDto.getData()); // SDK expects Base64 encoded string
                attachments.add(mailchimpAttachment);
            }
            message.setAttachments(attachments);
        }

        // message.setTrackOpens(true);
        // message.setTrackClicks(true);
        // Other options like tags, metadata can be set here

        try {
            MessageResponse[] responses = messagesApi.send(message, false); // false for async, true for sync (deprecated)
                                                                          // The send method without async param is preferred.
            // The SDK's send method changed. Let's use the one without the boolean async parameter.
            // It seems the current SDK `messagesApi.send(message)` is the way.

            // Re-checking SDK... The method is `messages.send(MessagePostRequest).`
            // The `Message` object we built is likely the `MessagePostRequest`.

            MessageResponse[] apiResponses = messagesApi.send(message);


            if (apiResponses != null && apiResponses.length > 0) {
                MessageResponse firstResponse = apiResponses[0];
                // Check status of the first response (assuming one email to one recipient)
                // Possible statuses: "sent", "queued", "scheduled", "rejected", "invalid"
                String status = firstResponse.getStatus();
                String messageId = firstResponse.getId();
                logger.info("Mailchimp send response. Status: {}, ID: {}, Email: {}", status, messageId, firstResponse.getEmail());

                if ("sent".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status) || "scheduled".equalsIgnoreCase(status)) {
                    return new EmailResponseDto("Success", "Email " + status + " successfully via Mailchimp. Message ID: " + messageId, messageId);
                } else {
                    String rejectReason = firstResponse.getRejectReason();
                    logger.error("Failed to send email via Mailchimp. Status: {}, Reason: {}", status, rejectReason);
                    return new EmailResponseDto("Error", "Failed to send email via Mailchimp. Status: " + status + (rejectReason != null ? ", Reason: " + rejectReason : ""), messageId); // messageId might be null if rejected early
                }
            } else {
                logger.error("Failed to send email via Mailchimp. No response from API.");
                return new EmailResponseDto("Error", "Failed to send email via Mailchimp. No response from API.", null);
            }

        } catch (IOException e) { // The SDK's send method throws IOException
            logger.error("Error sending email via Mailchimp (IOException)", e);
            return new EmailResponseDto("Error", "IOException while sending email via Mailchimp: " + e.getMessage());
        } catch (Exception e) { // Catching other potential runtime exceptions
            logger.error("Unexpected error sending email via Mailchimp", e);
            return new EmailResponseDto("Error", "Unexpected error while sending email via Mailchimp: " + e.getMessage());
        }
    }
}
