package com.wisestep.emailing.service.impl;

import com.mailchimp.clients.MailchimpClient;
import com.mailchimp.clients.Messages;
import com.mailchimp.clients.models.Message;
import com.mailchimp.clients.models.MessageResponse;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MailchimpEmailSenderTest {

    @InjectMocks
    private MailchimpEmailSender mailchimpEmailSender;

    // We need to mock MailchimpClient and its Messages API
    // Since MailchimpClient is instantiated directly within the sendEmail method,
    // we need to use Mockito's constructor mocking feature.

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailchimpEmailSender, "mailchimpApiKey", "TEST_MAILCHIMP_API_KEY");
    }

    @Test
    void sendEmail_success() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "<p>Test Body</p>", "mailchimp",
                Collections.singletonList(new AttachmentDto("test.txt", "text/plain", "dGVzdCBjb250ZW50")) // "test content"
        );

        MessageResponse mockApiResponse = new MessageResponse();
        mockApiResponse.setId("test-message-id");
        mockApiResponse.setStatus("sent");
        mockApiResponse.setEmail("to@example.com");

        // Mocking the constructor of MailchimpClient and the subsequent chained calls
        try (MockedConstruction<MailchimpClient> mockedClientConstruction = Mockito.mockConstruction(MailchimpClient.class,
                (mockClient, context) -> {
                    Messages mockMessagesApi = Mockito.mock(Messages.class);
                    when(mockClient.messages()).thenReturn(mockMessagesApi);
                    when(mockMessagesApi.send(any(Message.class))).thenReturn(new MessageResponse[]{mockApiResponse});
                })) {

            EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);

            assertEquals("Success", responseDto.getStatus());
            assertTrue(responseDto.getMessage().contains("Email sent successfully via Mailchimp. Message ID: test-message-id"));

            // Verify MailchimpClient was constructed (implicitly verified by mock logic)
            assertEquals(1, mockedClientConstruction.constructed().size());
            MailchimpClient constructedClient = mockedClientConstruction.constructed().get(0);
            Messages messagesApi = constructedClient.messages(); // Get the mocked Messages API

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messagesApi).send(messageCaptor.capture());

            Message sentMessage = messageCaptor.getValue();
            assertEquals("to@example.com", sentMessage.getTo().get(0).getEmail());
            assertEquals("from@example.com", sentMessage.getFromEmail());
            assertEquals("Test Subject", sentMessage.getSubject());
            assertEquals("<p>Test Body</p>", sentMessage.getHtml());
            assertEquals(1, sentMessage.getAttachments().size());
            assertEquals("test.txt", sentMessage.getAttachments().get(0).getName());
            assertEquals("dGVzdCBjb250ZW50", sentMessage.getAttachments().get(0).getContent());
        }
    }

    @Test
    void sendEmail_success_statusQueued() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "<p>Test Body</p>", "mailchimp",null);

        MessageResponse mockApiResponse = new MessageResponse();
        mockApiResponse.setId("test-queued-id");
        mockApiResponse.setStatus("queued");
        mockApiResponse.setEmail("to@example.com");

        try (MockedConstruction<MailchimpClient> mockedClientConstruction = Mockito.mockConstruction(MailchimpClient.class,
                (mockClient, context) -> {
                    Messages mockMessagesApi = Mockito.mock(Messages.class);
                    when(mockClient.messages()).thenReturn(mockMessagesApi);
                    when(mockMessagesApi.send(any(Message.class))).thenReturn(new MessageResponse[]{mockApiResponse});
                })) {

            EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);

            assertEquals("Success", responseDto.getStatus());
            assertTrue(responseDto.getMessage().contains("Email queued successfully via Mailchimp. Message ID: test-queued-id"));
        }
    }


    @Test
    void sendEmail_apiReturnsRejected() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Test", "Body", "mailchimp", null);

        MessageResponse mockApiResponse = new MessageResponse();
        mockApiResponse.setStatus("rejected");
        mockApiResponse.setRejectReason("invalid_sender");
        mockApiResponse.setEmail("to@example.com");

        try (MockedConstruction<MailchimpClient> mockedClientConstruction = Mockito.mockConstruction(MailchimpClient.class,
                (mockClient, context) -> {
                    Messages mockMessagesApi = Mockito.mock(Messages.class);
                    when(mockClient.messages()).thenReturn(mockMessagesApi);
                    when(mockMessagesApi.send(any(Message.class))).thenReturn(new MessageResponse[]{mockApiResponse});
                })) {

            EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);

            assertEquals("Error", responseDto.getStatus());
            assertTrue(responseDto.getMessage().contains("Status: rejected, Reason: invalid_sender"));
        }
    }

    @Test
    void sendEmail_apiReturnsNoResponse() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Test", "Body", "mailchimp", null);

        try (MockedConstruction<MailchimpClient> mockedClientConstruction = Mockito.mockConstruction(MailchimpClient.class,
                (mockClient, context) -> {
                    Messages mockMessagesApi = Mockito.mock(Messages.class);
                    when(mockClient.messages()).thenReturn(mockMessagesApi);
                    when(mockMessagesApi.send(any(Message.class))).thenReturn(null); // Simulate null response array
                })) {

            EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);

            assertEquals("Error", responseDto.getStatus());
            assertEquals("Failed to send email via Mailchimp. No response from API.", responseDto.getMessage());
        }
    }


    @Test
    void sendEmail_ioExceptionDuringSend() throws IOException {
        EmailRequestDto requestDto = new EmailRequestDto("to@example.com", "from@example.com", "Test", "Body", "mailchimp", null);

        try (MockedConstruction<MailchimpClient> mockedClientConstruction = Mockito.mockConstruction(MailchimpClient.class,
                (mockClient, context) -> {
                    Messages mockMessagesApi = Mockito.mock(Messages.class);
                    when(mockClient.messages()).thenReturn(mockMessagesApi);
                    when(mockMessagesApi.send(any(Message.class))).thenThrow(new IOException("Network timeout"));
                })) {

            EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);

            assertEquals("Error", responseDto.getStatus());
            assertTrue(responseDto.getMessage().contains("IOException while sending email via Mailchimp: Network timeout"));
        }
    }

    @Test
    void sendEmail_noApiKey() {
        ReflectionTestUtils.setField(mailchimpEmailSender, "mailchimpApiKey", null);
        EmailRequestDto requestDto = new EmailRequestDto();
        EmailResponseDto responseDto = mailchimpEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("Mailchimp API Key is not configured.", responseDto.getMessage());

        ReflectionTestUtils.setField(mailchimpEmailSender, "mailchimpApiKey", "YOUR_MAILCHIMP_API_KEY");
        responseDto = mailchimpEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("Mailchimp API Key is not configured.", responseDto.getMessage());
    }
}
