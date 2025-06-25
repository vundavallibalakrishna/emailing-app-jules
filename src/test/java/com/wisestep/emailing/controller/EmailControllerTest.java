package com.wisestep.emailing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisestep.emailing.domain.job.EmailJob;
import com.wisestep.emailing.dto.BulkEmailRequestDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.service.EmailSchedulingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class) // Specify the controller to test
public class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean // Mocks the EmailSchedulingService for this controller test
    private EmailSchedulingService emailSchedulingService;

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    @Test
    void scheduleBulkEmail_success_singleEmail() throws Exception {
        EmailRequestDto email1 = new EmailRequestDto("to1@example.com", "from@example.com", "Subject 1", "Body 1", "sendgrid", null);
        BulkEmailRequestDto bulkRequest = new BulkEmailRequestDto(Collections.singletonList(email1));

        EmailJob mockJob1 = new EmailJob();
        mockJob1.setId(1L);
        when(emailSchedulingService.scheduleEmailJob(any(EmailRequestDto.class))).thenReturn(mockJob1);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("1 email(s) scheduled successfully."))
                .andExpect(jsonPath("$.jobIds", hasSize(1)))
                .andExpect(jsonPath("$.jobIds[0]").value(1));
    }

    @Test
    void scheduleBulkEmail_success_multipleEmails() throws Exception {
        EmailRequestDto email1 = new EmailRequestDto("to1@example.com", "from@example.com", "Subject 1", "Body 1", "sendgrid", null);
        EmailRequestDto email2 = new EmailRequestDto("to2@example.com", "from@example.com", "Subject 2", "Body 2", "smtp", null);
        BulkEmailRequestDto bulkRequest = new BulkEmailRequestDto(Arrays.asList(email1, email2));

        EmailJob mockJob1 = new EmailJob(); mockJob1.setId(1L);
        EmailJob mockJob2 = new EmailJob(); mockJob2.setId(2L);

        // Make sure the mock returns different IDs for different inputs if necessary,
        // or use thenAnswer for more complex mock logic.
        // For this test, if it's called twice, it will return mockJob1 then mockJob2.
        when(emailSchedulingService.scheduleEmailJob(email1)).thenReturn(mockJob1);
        when(emailSchedulingService.scheduleEmailJob(email2)).thenReturn(mockJob2);


        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("2 email(s) scheduled successfully."))
                .andExpect(jsonPath("$.jobIds", hasSize(2)))
                .andExpect(jsonPath("$.jobIds", containsInAnyOrder(1, 2))); // Order might not be guaranteed
    }

    @Test
    void scheduleBulkEmail_emptyRequest() throws Exception {
        BulkEmailRequestDto emptyRequest = new BulkEmailRequestDto(Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request must contain at least one email."));
    }

    @Test
    void scheduleBulkEmail_nullEmailList() throws Exception {
        BulkEmailRequestDto nullListRequest = new BulkEmailRequestDto(null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullListRequest)))
                .andExpect(status().isBadRequest())
                 //This response comes from @Valid on BulkEmailRequestDto.emails if @NotNull is there
                .andExpect(jsonPath("$.message").value("Request must contain at least one email."));
    }


    @Test
    void scheduleBulkEmail_partialFailure() throws Exception {
        EmailRequestDto email1 = new EmailRequestDto("to1@example.com", "from@example.com", "Subject 1", "Body 1", "sendgrid", null);
        EmailRequestDto email2 = new EmailRequestDto("to2@example.com", "from@example.com", "Subject 2", "Body 2", "smtp", null); // This one will fail
        BulkEmailRequestDto bulkRequest = new BulkEmailRequestDto(Arrays.asList(email1, email2));

        EmailJob mockJob1 = new EmailJob(); mockJob1.setId(1L);
        when(emailSchedulingService.scheduleEmailJob(email1)).thenReturn(mockJob1);
        when(emailSchedulingService.scheduleEmailJob(email2)).thenThrow(new RuntimeException("SMTP server down"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isMultiStatus()) // 207
                .andExpect(jsonPath("$.message").value("1 email(s) scheduled successfully. 1 email(s) failed to schedule. Errors: Failed to schedule for to2@example.com: SMTP server down"))
                .andExpect(jsonPath("$.jobIds", hasSize(1)))
                .andExpect(jsonPath("$.jobIds[0]").value(1));
    }

    @Test
    void scheduleBulkEmail_totalFailure() throws Exception {
        EmailRequestDto email1 = new EmailRequestDto("to1@example.com", "from@example.com", "Subject 1", "Body 1", "sendgrid", null);
        BulkEmailRequestDto bulkRequest = new BulkEmailRequestDto(Collections.singletonList(email1));

        when(emailSchedulingService.scheduleEmailJob(any(EmailRequestDto.class))).thenThrow(new RuntimeException("DB connection failed"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("All email jobs failed to schedule. Errors: Failed to schedule for to1@example.com: DB connection failed"))
                .andExpect(jsonPath("$.jobIds", hasSize(0)));
    }

    // Test for @Valid annotations on EmailRequestDto within BulkEmailRequestDto
    @Test
    void scheduleBulkEmail_invalidEmailInList() throws Exception {
        // EmailRequestDto now has @NotBlank and @Email annotations
        EmailRequestDto invalidEmail = new EmailRequestDto("invalid-email", "", null, "Body", "sendgrid", null); // Invalid 'to', blank 'from', null 'subject'
        BulkEmailRequestDto bulkRequest = new BulkEmailRequestDto(Collections.singletonList(invalidEmail));

        // Spring's @Valid will trigger MethodArgumentNotValidException before controller logic is hit in this way for DTOs
        // The actual error message structure might depend on global exception handling.
        // For @WebMvcTest, it often returns a more generic 400 or specific field errors if ExceptionHandlers are in place.
        // Without specific @ControllerAdvice, it might be a generic 400.
        // Let's assume default behavior where field errors are somewhat indicated.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isBadRequest()); // Expecting 400 due to validation failures
                // Asserting specific error messages from validation can be tricky as format varies.
                // e.g. .andExpect(jsonPath("$.errors[?(@.field == 'emails[0].to')].defaultMessage").value("Invalid 'to' email format."))
                // This depends on how your application serializes BindingResult errors.
    }

}
