package com.wisestep.emailing.service.impl;

import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mockJavaMailSender;

    @InjectMocks
    private SmtpEmailSender smtpEmailSender;

    @Test
    void sendEmail_success() throws MessagingException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "<h1>Test Body</h1>", "smtp",
                Collections.singletonList(new AttachmentDto("test.txt", "text/plain", "dGVzdCBjb250ZW50")) // "test content"
        );

        // JavaMailSender.createMimeMessage() returns a new MimeMessage.
        // We need to provide a MimeMessage when mockJavaMailSender.createMimeMessage() is called.
        MimeMessage mimeMessage = new MimeMessage((Session) null); // Basic MimeMessage for test purposes
        when(mockJavaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailResponseDto responseDto = smtpEmailSender.sendEmail(requestDto);

        assertEquals("Success", responseDto.getStatus());
        assertEquals("Email sent successfully via SMTP.", responseDto.getMessage());

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mockJavaMailSender).send(messageCaptor.capture());

        MimeMessage sentMessage = messageCaptor.getValue();
        assertNotNull(sentMessage);
        // Further assertions on MimeMessage content (to, from, subject, body, attachment) can be added here.
        // This requires parsing the MimeMessage, which can be complex.
        // For example, checking the subject:
        assertEquals("Test Subject", sentMessage.getSubject());
        // Checking recipient:
        assertEquals("to@example.com", sentMessage.getAllRecipients()[0].toString());
        // Checking if it's multipart (due to attachment or HTML body)
        try {
            assertTrue(sentMessage.getContentType().contains("multipart/mixed"));
            // A more detailed check could involve iterating through MimeBodyParts
        } catch (Exception e) {
            fail("Could not get content type or content from message", e);
        }
    }

    @Test
    void sendEmail_messagingException() throws MessagingException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "smtp", null
        );
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mockJavaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        doThrow(new MessagingException("SMTP server down")).when(mockJavaMailSender).send(any(MimeMessage.class));

        EmailResponseDto responseDto = smtpEmailSender.sendEmail(requestDto);

        assertEquals("Error", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("MessagingException while sending email via SMTP: SMTP server down"));
    }

    @Test
    void sendEmail_generalExceptionOnAttachmentProcessing() {
        // Simulate an error during Base64 decoding for an attachment
        EmailRequestDto requestDtoWithBadAttachment = new EmailRequestDto(
                "to@example.com", "from@example.com", "Bad Attachment", "Body", "smtp",
                Collections.singletonList(new AttachmentDto("bad.txt", "text/plain", "this is not valid base64!!!"))
        );
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mockJavaMailSender.createMimeMessage()).thenReturn(mimeMessage);


        EmailResponseDto responseDto = smtpEmailSender.sendEmail(requestDtoWithBadAttachment);

        assertEquals("Error", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("Unexpected error while sending email via SMTP:"));
        // The specific message comes from IllegalArgumentException from Base64.getDecoder().decode()
    }
}
