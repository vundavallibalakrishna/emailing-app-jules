package com.wisestep.emailing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
public class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock the individual EmailSender beans that would be collected into the Map
    // Spring Boot's @WebMvcTest will pick these up if they are part of the test configuration
    // or if they are @MockBean s with specific names.
    // For simplicity in @WebMvcTest, we often mock the direct dependencies.
    // Here, the EmailController depends on a Map<String, EmailSender>.
    // We can provide a @MockBean for each sender implementation that the controller might use.

    @MockBean(name = "sendgrid") // Name must match the @Service("sendgrid") annotation
    private EmailSender sendGridEmailSenderMock;

    @MockBean(name = "elasticemail") // Example for a future provider
    private EmailSender elasticEmailSenderMock;

    @MockBean(name = "smtp")
    private EmailSender smtpEmailSenderMock;

    @MockBean(name = "mailchimp")
    private EmailSender mailchimpEmailSenderMock;


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendEmail_success_withSendGridProvider() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        EmailResponseDto successResponse = new EmailResponseDto("Success", "Email sent via SendGrid");

        when(sendGridEmailSenderMock.sendEmail(any(EmailRequestDto.class))).thenReturn(successResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Email sent via SendGrid"));
    }

    @Test
    void sendEmail_success_withDefaultProvider ( ) throws Exception {
        // Default provider is "sendgrid" as per EmailController logic
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", null, null); // Provider is null
        EmailResponseDto successResponse = new EmailResponseDto("Success", "Email sent via SendGrid (default)");

        when(sendGridEmailSenderMock.sendEmail(any(EmailRequestDto.class))).thenReturn(successResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Email sent via SendGrid (default)"));
    }

    @Test
    void sendEmail_success_withElasticEmailProvider() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "elasticemail", null);
        EmailResponseDto successResponse = new EmailResponseDto("Success", "Email sent via Elastic Email");

        when(elasticEmailSenderMock.sendEmail(any(EmailRequestDto.class))).thenReturn(successResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Email sent via Elastic Email"));
    }


    @Test
    void sendEmail_providerNotSupported() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "unsupportedProvider", null);
        // No mock is set up for "unsupportedProvider", so the map in controller will return null for it.

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("Error"))
                .andExpect(jsonPath("$.message").value("Unsupported or unconfigured email provider: unsupportedprovider"));
    }

    @Test
    void sendEmail_serviceReturnsError() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        EmailResponseDto errorResponse = new EmailResponseDto("Error", "SendGrid API failed");

        when(sendGridEmailSenderMock.sendEmail(any(EmailRequestDto.class))).thenReturn(errorResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("Error"))
                .andExpect(jsonPath("$.message").value("SendGrid API failed"));
    }

    @Test
    void sendEmail_serviceThrowsException() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);

        when(sendGridEmailSenderMock.sendEmail(any(EmailRequestDto.class))).thenThrow(new RuntimeException("Unexpected service error"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred with provider sendgrid: Unexpected service error"));
    }
}
