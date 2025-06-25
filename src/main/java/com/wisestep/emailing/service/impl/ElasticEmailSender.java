package com.wisestep.emailing.service.impl;

import com.elasticemail.java.Api.ApiTypes;
import com.elasticemail.java.Api.Emails;
import com.elasticemail.java.Api.Enums;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service("elasticemail")
public class ElasticEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(ElasticEmailSender.class);

    @Value("${elasticemail.api.key}")
    private String elasticEmailApiKey;

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        if (elasticEmailApiKey == null || elasticEmailApiKey.isEmpty() || "YOUR_ELASTICEMAIL_API_KEY".equals(elasticEmailApiKey)) {
            logger.error("Elastic Email API Key is not configured.");
            return new EmailResponseDto("Error", "Elastic Email API Key is not configured.");
        }

        Emails emailsApi = new Emails(elasticEmailApiKey);
        List<File> tempFiles = new ArrayList<>();

        try {
            ApiTypes.EmailSend response = null;
            List<Path> attachmentPaths = new ArrayList<>();

            if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
                for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                    try {
                        byte[] fileData = Base64.getDecoder().decode(attachmentDto.getData());
                        Path tempFile = Files.createTempFile(UUID.randomUUID().toString(), "_" + attachmentDto.getFilename());
                        Files.write(tempFile, fileData);
                        attachmentPaths.add(tempFile);
                        tempFiles.add(tempFile.toFile()); // For cleanup
                    } catch (Exception e) {
                        logger.error("Failed to create or write temporary file for attachment: {}", attachmentDto.getFilename(), e);
                        // Decide if to proceed without this attachment or fail
                        return new EmailResponseDto("Error", "Failed to process attachment: " + attachmentDto.getFilename());
                    }
                }
            }

            // The Elastic Email Java SDK's send method seems to directly take parameters
            // rather than a single mail object like SendGrid's V3 SDK.
            response = emailsApi.Send(
                    requestDto.getSubject(),
                    requestDto.getFrom(), // From address
                    requestDto.getFrom(), // From name (can be same as address or a display name)
                    null, // replyTo
                    null, // replyToName
                    requestDto.getTo(), // To addresses, comma-separated
                    null, // cc
                    null, // bcc
                    null, // lists
                    null, // segments
                    null, // mergeSourceFilename
                    null, // channel
                    requestDto.getBody(), // bodyHtml
                    requestDto.getBody(), // bodyText (provide plain text version)
                    null, // charset
                    null, // charsetBodyHtml
                    null, // charsetBodyText
                    Enums.EncodingType.UserProvided, // encodingType
                    null, // template
                    attachmentPaths, // attachmentFiles (List<Path>)
                    null, // headers
                    null, // postBack
                    null, // merge
                    null, // timeOffSetMinutes
                    null, // poolName
                    false, // isTransactional
                    null // trackingoptions
            );


            if (response != null && response.transactionID != null && !response.transactionID.isEmpty()) {
                logger.info("Email sent successfully via Elastic Email. Transaction ID: {}", response.transactionID);
                return new EmailResponseDto("Success", "Email sent successfully via Elastic Email. Transaction ID: " + response.transactionID, response.transactionID);
            } else {
                // The SDK might throw an exception for errors, or return a null/empty response.
                // It's better to check their documentation for specific error handling.
                // Assuming if transactionID is missing, it's an error.
                logger.error("Failed to send email via Elastic Email. Response: {}", response != null ? response.toString() : "null");
                return new EmailResponseDto("Error", "Failed to send email via Elastic Email. Response details: " + (response != null ? response.toString() : "null"), null);
            }

        } catch (Exception e) {
            logger.error("Error sending email via Elastic Email", e);
            return new EmailResponseDto("Error", "Exception while sending email via Elastic Email: " + e.getMessage());
        } finally {
            for (File tempFile : tempFiles) {
                if (tempFile.exists()) {
                    try {
                        Files.delete(tempFile.toPath());
                    } catch (IOException e) {
                        logger.warn("Failed to delete temporary attachment file: {}", tempFile.getAbsolutePath(), e);
                    }
                }
            }
        }
    }
}
