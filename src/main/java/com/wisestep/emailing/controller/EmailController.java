package com.wisestep.emailing.controller;

import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private static final Logger logger = LoggerFactory.getLogger(EmailController.class);

    private final EmailSender sendGridEmailSender;
    // You can inject other providers here using @Qualifier
    // private final EmailSender mailgunEmailSender;

    @Autowired
    public EmailController(@Qualifier("sendgrid") EmailSender sendGridEmailSender) {
        this.sendGridEmailSender = sendGridEmailSender;
        // this.mailgunEmailSender = mailgunEmailSender;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResponseDto> sendEmail(@RequestBody EmailRequestDto requestDto) {
        logger.info("Received email send request: To={}, From={}, Subject={}, Provider={}",
                requestDto.getTo(), requestDto.getFrom(), requestDto.getSubject(), requestDto.getProvider());

        EmailResponseDto responseDto;
        EmailSender emailSender;

        // Basic provider selection logic
        // This can be enhanced with a factory or strategy pattern for more providers
        if ("sendgrid".equalsIgnoreCase(requestDto.getProvider())) {
            emailSender = sendGridEmailSender;
        } else if (requestDto.getProvider() == null || requestDto.getProvider().trim().isEmpty()) {
            // Default to SendGrid if no provider is specified
            logger.warn("No provider specified in the request, defaulting to SendGrid.");
            emailSender = sendGridEmailSender;
        }
        // else if ("mailgun".equalsIgnoreCase(requestDto.getProvider())) {
        // emailSender = mailgunEmailSender; // Assuming mailgunEmailSender is injected
        // }
        else {
            logger.error("Unsupported email provider: {}", requestDto.getProvider());
            responseDto = new EmailResponseDto("Error", "Unsupported email provider: " + requestDto.getProvider());
            return ResponseEntity.badRequest().body(responseDto);
        }

        try {
            responseDto = emailSender.sendEmail(requestDto);
            if ("Success".equals(responseDto.getStatus())) {
                return ResponseEntity.ok(responseDto);
            } else {
                // Log the detailed error message from the service, but perhaps return a more generic one to the client
                logger.error("Email sending failed. Service response: {}", responseDto.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
            }
        } catch (Exception e) {
            logger.error("Unexpected error occurred while sending email", e);
            responseDto = new EmailResponseDto("Error", "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDto);
        }
    }
}
