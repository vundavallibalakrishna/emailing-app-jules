package com.wisestep.emailing.controller;

import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    private final Map<String, EmailSender> emailSenders;
    private static final String DEFAULT_PROVIDER = "sendgrid"; // Or make this configurable

    @Autowired
    public EmailController(Map<String, EmailSender> emailSenders) {
        this.emailSenders = emailSenders;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResponseDto> sendEmail(@RequestBody EmailRequestDto requestDto) {
        logger.info("Received email send request: To={}, From={}, Subject={}, Provider={}",
                requestDto.getTo(), requestDto.getFrom(), requestDto.getSubject(), requestDto.getProvider());

        EmailResponseDto responseDto;
        EmailSender emailSender;

        String providerKey = requestDto.getProvider();
        if (providerKey == null || providerKey.trim().isEmpty()) {
            logger.warn("No provider specified in the request, defaulting to {}.", DEFAULT_PROVIDER);
            providerKey = DEFAULT_PROVIDER;
        } else {
            providerKey = providerKey.toLowerCase(); // Ensure consistent key lookup
        }

        emailSender = emailSenders.get(providerKey);

        if (emailSender == null) {
            logger.error("Unsupported or unconfigured email provider: {}", providerKey);
            responseDto = new EmailResponseDto("Error", "Unsupported or unconfigured email provider: " + providerKey);
            return ResponseEntity.badRequest().body(responseDto);
        }

        try {
            responseDto = emailSender.sendEmail(requestDto);
            if ("Success".equals(responseDto.getStatus())) {
                return ResponseEntity.ok(responseDto);
            } else {
                logger.error("Email sending failed with provider {}. Service response: {}", providerKey, responseDto.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
            }
        } catch (Exception e) {
            logger.error("Unexpected error occurred while sending email with provider {}", providerKey, e);
            responseDto = new EmailResponseDto("Error", "An unexpected error occurred with provider " + providerKey + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
        }
    }
}
