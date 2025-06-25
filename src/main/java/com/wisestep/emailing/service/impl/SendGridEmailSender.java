package com.wisestep.emailing.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

@Service("sendgrid")
public class SendGridEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(SendGridEmailSender.class);

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        if (sendGridApiKey == null || sendGridApiKey.isEmpty() || "YOUR_SENDGRID_API_KEY".equals(sendGridApiKey)) {
            logger.error("SendGrid API Key is not configured.");
            return new EmailResponseDto("Error", "SendGrid API Key is not configured.");
        }

        Mail mail = new Mail();
        mail.setFrom(new Email(requestDto.getFrom()));
        mail.setSubject(requestDto.getSubject());

        Personalization personalization = new Personalization();
        personalization.addTo(new Email(requestDto.getTo()));
        mail.addPersonalization(personalization);

        mail.addContent(new Content("text/plain", requestDto.getBody())); // Assuming plain text body for now

        if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
            for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                Attachments attachments = new Attachments();
                byte[] fileData = Base64.getDecoder().decode(attachmentDto.getData());
                attachments.setContent(Base64.getEncoder().encodeToString(fileData));
                attachments.setType(attachmentDto.getContentType());
                attachments.setFilename(attachmentDto.getFilename());
                attachments.setDisposition("attachment");
                // attachments.setContentId("someContentId"); // Optional: if you need to reference in HTML body
                mail.addAttachments(attachments);
            }
        }

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            logger.info("SendGrid response status code: {}", response.getStatusCode());
            logger.info("SendGrid response body: {}", response.getBody());
            logger.info("SendGrid response headers: {}", response.getHeaders());

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                return new EmailResponseDto("Success", "Email sent successfully via SendGrid. Message ID: " + response.getHeaders().get("X-Message-Id"));
            } else {
                logger.error("Error sending email via SendGrid. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                return new EmailResponseDto("Error", "Failed to send email via SendGrid. Status: " + response.getStatusCode() + ", Body: " + response.getBody());
            }
        } catch (IOException ex) {
            logger.error("Error sending email via SendGrid", ex);
            return new EmailResponseDto("Error", "IOException while sending email via SendGrid: " + ex.getMessage());
        }
    }
}
