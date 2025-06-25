package com.wisestep.emailing.service.impl;

import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Base64;

@Service("smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailSender.class);

    @Autowired
    private JavaMailSender javaMailSender;

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            // Use true for multipart message support (for attachments)
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(requestDto.getTo());
            helper.setFrom(requestDto.getFrom()); // Ensure this 'from' is allowed by the SMTP server
            helper.setSubject(requestDto.getSubject());

            // Assuming the body can be HTML. If only plain text, set the second param to false.
            // For sending both HTML and plain text, you might need to call setText(plain, html)
            helper.setText(requestDto.getBody(), true); // true indicates HTML

            if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
                for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                    byte[] fileData = Base64.getDecoder().decode(attachmentDto.getData());
                    ByteArrayResource byteArrayResource = new ByteArrayResource(fileData);
                    helper.addAttachment(attachmentDto.getFilename(), byteArrayResource, attachmentDto.getContentType());
                }
            }

            javaMailSender.send(mimeMessage);
            logger.info("Email sent successfully via SMTP to {}", requestDto.getTo());
            // SMTP doesn't typically return a message ID in the same way as API services
            return new EmailResponseDto("Success", "Email sent successfully via SMTP.");

        } catch (MessagingException e) {
            logger.error("Error sending email via SMTP", e);
            return new EmailResponseDto("Error", "MessagingException while sending email via SMTP: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending email via SMTP", e);
            return new EmailResponseDto("Error", "Unexpected error while sending email via SMTP: " + e.getMessage());
        }
    }
}
