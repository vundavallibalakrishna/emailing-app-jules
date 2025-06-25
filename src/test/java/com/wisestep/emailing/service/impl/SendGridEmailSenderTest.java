package com.wisestep.emailing.service.impl;

import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SendGridEmailSenderTest {

    @Mock
    private SendGrid mockSendGrid;

    @InjectMocks
    private SendGridEmailSender sendGridEmailSender;

    @BeforeEach
    void setUp() {
        // Inject the API key using ReflectionTestUtils because @Value won't work in this unit test context
        ReflectionTestUtils.setField(sendGridEmailSender, "sendGridApiKey", "TEST_API_KEY");
    }

    @Test
    void sendEmail_success() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "sendgrid",
                Collections.singletonList(new AttachmentDto("test.txt", "text/plain", "dGVzdCBjb250ZW50")) // "test content" Base64
        );

        Response mockSgResponse = new Response();
        mockSgResponse.setStatusCode(202); // SendGrid success status
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Message-Id", "test-message-id");
        mockSgResponse.setHeaders(headers);
        mockSgResponse.setBody("{\"message\":\"success\"}");


        when(mockSendGrid.api(any(Request.class))).thenReturn(mockSgResponse);

        EmailResponseDto responseDto = sendGridEmailSender.sendEmail(requestDto);

        assertEquals("Success", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("test-message-id"));

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mockSendGrid).api(requestCaptor.capture());
        Request capturedRequest = requestCaptor.getValue();

        assertTrue(capturedRequest.getBody().contains("\"to\":{\"email\":\"to@example.com\"}"));
        assertTrue(capturedRequest.getBody().contains("\"from\":{\"email\":\"from@example.com\"}"));
        assertTrue(capturedRequest.getBody().contains("\"subject\":\"Test Subject\""));
        assertTrue(capturedRequest.getBody().contains("\"content\":\"dGVzdCBjb250ZW50\"")); // Check for Base64 data
        assertTrue(capturedRequest.getBody().contains("\"filename\":\"test.txt\""));
    }

    @Test
    void sendEmail_sendGridError() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "sendgrid", null
        );

        Response mockSgResponse = new Response();
        mockSgResponse.setStatusCode(400); // SendGrid error status
        mockSgResponse.setBody("{\"errors\":[{\"message\":\"Invalid API key\"}]}");

        when(mockSendGrid.api(any(Request.class))).thenReturn(mockSgResponse);

        EmailResponseDto responseDto = sendGridEmailSender.sendEmail(requestDto);

        assertEquals("Error", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("Status: 400"));
        assertTrue(responseDto.getMessage().contains("Invalid API key"));
    }

    @Test
    void sendEmail_ioException() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "sendgrid", null
        );

        when(mockSendGrid.api(any(Request.class))).thenThrow(new IOException("Network error"));

        EmailResponseDto responseDto = sendGridEmailSender.sendEmail(requestDto);

        assertEquals("Error", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("IOException while sending email via SendGrid: Network error"));
    }

    @Test
    void sendEmail_noApiKey() {
        ReflectionTestUtils.setField(sendGridEmailSender, "sendGridApiKey", null);
        EmailRequestDto requestDto = new EmailRequestDto();
        EmailResponseDto responseDto = sendGridEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("SendGrid API Key is not configured.", responseDto.getMessage());

        ReflectionTestUtils.setField(sendGridEmailSender, "sendGridApiKey", "");
        responseDto = sendGridEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("SendGrid API Key is not configured.", responseDto.getMessage());

        ReflectionTestUtils.setField(sendGridEmailSender, "sendGridApiKey", "YOUR_SENDGRID_API_KEY");
        responseDto = sendGridEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("SendGrid API Key is not configured.", responseDto.getMessage());
    }
}
