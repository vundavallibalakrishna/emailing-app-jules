package com.wisestep.emailing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
public class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @Qualifier("sendgrid") // Ensure this matches the qualifier in the controller
    private EmailSender sendGridEmailSender;

    // If you add other providers, mock them as well
    // @MockBean
    // @Qualifier("mailgun")
    // private EmailSender mailgunEmailSender;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sendEmail_success_withSendGridProvider() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        EmailResponseDto successResponse = new EmailResponseDto("Success", "Email sent via SendGrid");

        when(sendGridEmailSender.sendEmail(any(EmailRequestDto.class))).thenReturn(successResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Email sent via SendGrid"));
    }

    @Test
    void sendEmail_success_withDefaultProvider ( ) throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", null, null); // Provider is null
        EmailResponseDto successResponse = new EmailResponseDto("Success", "Email sent via SendGrid (default)");

        when(sendGridEmailSender.sendEmail(any(EmailRequestDto.class))).thenReturn(successResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Email sent via SendGrid (default)"));
    }


    @Test
    void sendEmail_providerNotSupported() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "unsupportedProvider", null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("Error"))
                .andExpect(jsonPath("$.message").value("Unsupported email provider: unsupportedProvider"));
    }

    @Test
    void sendEmail_serviceReturnsError() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Subject", "Body", "sendgrid", null);
        EmailResponseDto errorResponse = new EmailResponseDto("Error", "SendGrid API failed");

        when(sendGridEmailSender.sendEmail(any(EmailRequestDto.class))).thenReturn(errorResponse);

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

        when(sendGridEmailSender.sendEmail(any(EmailRequestDto.class))).thenThrow(new RuntimeException("Unexpected service error"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred: Unexpected service error"));
    }
}
