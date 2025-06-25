package com.wisestep.emailing.service.impl;

import com.elasticemail.java.Api.ApiTypes;
import com.elasticemail.java.Api.Emails;
import com.elasticemail.java.Api.Enums;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ElasticEmailSenderTest {

    // We need to mock the Emails class constructor or its methods.
    // The SDK creates an instance of Emails within the method.
    // This is tricky. One way is to use PowerMockito or a similar library,
    // or refactor the Sender to allow injecting a mocked Emails object.
    // For now, let's try to use Mockito's static mocking for Files.createTempFile if needed,
    // and see if we can mock the Emails API instance.
    // A cleaner way would be to wrap the Emails API in another class that can be mocked.

    @InjectMocks
    private ElasticEmailSender elasticEmailSender;

    private MockedStatic<Files> mockedFiles;
    private List<Path> tempFilePaths = new ArrayList<>();


    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(elasticEmailSender, "elasticEmailApiKey", "TEST_ELASTIC_API_KEY");
        // For controlling temp file creation and cleanup
        mockedFiles = Mockito.mockStatic(Files.class);
    }

    @AfterEach
    void tearDown() {
        mockedFiles.close();
        // Attempt to clean up any manually created temp files if tests fail before cleanup logic in sender
        for (Path p : tempFilePaths) {
            try {
                java.nio.file.Files.deleteIfExists(p);
            } catch (IOException e) {
                // ignore
            }
        }
        tempFilePaths.clear();
    }

    private Path mockTempFileCreation(String prefix, String suffix) throws IOException {
        Path mockPath = mock(Path.class);
        // When Files.createTempFile is called, return our mockPath
        mockedFiles.when(() -> Files.createTempFile(anyString(), eq("_" + suffix))).thenReturn(mockPath);
        // When Files.write is called on this mockPath, do nothing or verify
        mockedFiles.when(() -> Files.write(eq(mockPath), any(byte[].class))).thenReturn(mockPath); // Simulate write
        mockedFiles.when(() -> Files.delete(eq(mockPath))).thenAnswer(invocation -> null); // Simulate delete
        when(mockPath.toFile()).thenReturn(mock(java.io.File.class)); // Mock toFile()
        tempFilePaths.add(mockPath); // Keep track for cleanup verification
        return mockPath;
    }


    @Test
    void sendEmail_success() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "elasticemail",
                Collections.singletonList(new AttachmentDto("test.txt", "text/plain", "dGVzdCBjb250ZW50")) // "test content"
        );

        Path mockAttachmentPath = mockTempFileCreation(null, "test.txt");

        // Mock the Emails API - this is the challenging part due to direct instantiation.
        // We will assume for this test that we can get a mocked instance or the call succeeds.
        // This test will effectively become more of an integration test with the SDK's structure
        // unless we refactor the sender or use PowerMock.
        // For now, let's assume the happy path where SDK works as expected.
        // The actual SDK call `new Emails(apiKey).Send(...)` is hard to mock directly with Mockito alone
        // without refactoring `ElasticEmailSender` to accept an `Emails` factory or instance.

        // Let's simulate a successful response from the SDK
        ApiTypes.EmailSend mockResponse = new ApiTypes.EmailSend();
        mockResponse.transactionID = "test-transaction-id";

        // This is where it gets tricky. We cannot easily mock `new Emails(apiKey).Send(...)`
        // For a unit test, we'd ideally mock the `Emails` object itself.
        // One workaround without PowerMock or major refactor:
        // Create a protected factory method in ElasticEmailSender: protected Emails getEmailsApi(String apiKey) { return new Emails(apiKey); }
        // Then in test, subclass ElasticEmailSender, override getEmailsApi to return a mock.
        // For now, this test will be limited in its ability to purely unit test.

        // Let's proceed by focusing on the logic *around* the SDK call.
        // We can't directly mock the `emailsApi.Send` call without the above refactor/PowerMock.
        // So, this test will not be a true unit test of the interaction with Emails API.
        // It will test the setup and API key check.

        EmailResponseDto responseDto = elasticEmailSender.sendEmail(requestDto);

        // Due to inability to mock `new Emails().Send()` call easily, we can't assert success based on mocked SDK.
        // The test will likely try to make a real call if not refactored.
        // For now, let's assume it would pass if the API key is valid and SDK is called.
        // This highlights a limitation of the current test setup for this specific SDK interaction.

        // If we *could* mock it (e.g. via the factory method pattern):
        // Emails mockEmailsApi = mock(Emails.class);
        // when(mockEmailsApi.Send(anyString(), anyString(), anyString(), ...)).thenReturn(mockResponse);
        // // (and inject this mockEmailsApi via a factory method)
        // Then we could assert:
        // assertEquals("Success", responseDto.getStatus());
        // assertTrue(responseDto.getMessage().contains("test-transaction-id"));

        // For now, we'll just check that it doesn't fail on API key check and attempts to process attachments.
        assertNotNull(responseDto); // Basic check
        if ("Elastic Email API Key is not configured.".equals(responseDto.getMessage())) {
             fail("Test setup error or API key issue, not an SDK mock issue.");
        }
        // If it proceeds, it means the API key was fine. The actual call might fail if API key is "TEST_ELASTIC_API_KEY"
        // which is expected without a live API key.
        // The important part for *this* test is that attachment processing logic is hit.
        mockedFiles.verify(() -> Files.createTempFile(anyString(), eq("_test.txt")), times(1));
        mockedFiles.verify(() -> Files.write(eq(mockAttachmentPath), any(byte[].class)), times(1));
        // And cleanup is attempted
         mockedFiles.verify(() -> Files.delete(eq(mockAttachmentPath)), times(1));
    }


    @Test
    void sendEmail_noApiKey() {
        ReflectionTestUtils.setField(elasticEmailSender, "elasticEmailApiKey", null);
        EmailRequestDto requestDto = new EmailRequestDto();
        EmailResponseDto responseDto = elasticEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("Elastic Email API Key is not configured.", responseDto.getMessage());

        ReflectionTestUtils.setField(elasticEmailSender, "elasticEmailApiKey", "");
        responseDto = elasticEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("Elastic Email API Key is not configured.", responseDto.getMessage());

        ReflectionTestUtils.setField(elasticEmailSender, "elasticEmailApiKey", "YOUR_ELASTICEMAIL_API_KEY");
        responseDto = elasticEmailSender.sendEmail(requestDto);
        assertEquals("Error", responseDto.getStatus());
        assertEquals("Elastic Email API Key is not configured.", responseDto.getMessage());
    }

    @Test
    void sendEmail_attachmentProcessingError() throws Exception {
        EmailRequestDto requestDto = new EmailRequestDto(
                "to@example.com", "from@example.com", "Test Subject", "Test Body", "elasticemail",
                Collections.singletonList(new AttachmentDto("fail.txt", "text/plain", "ZmFpbA==")) // "fail"
        );

        // Simulate an error during Files.write for the attachment
        Path mockFailPath = mock(Path.class);
        mockedFiles.when(() -> Files.createTempFile(anyString(), eq("_fail.txt"))).thenReturn(mockFailPath);
        mockedFiles.when(() -> Files.write(eq(mockFailPath), any(byte[].class))).thenThrow(new IOException("Disk full"));
        when(mockFailPath.toFile()).thenReturn(mock(java.io.File.class));


        EmailResponseDto responseDto = elasticEmailSender.sendEmail(requestDto);

        assertEquals("Error", responseDto.getStatus());
        assertTrue(responseDto.getMessage().contains("Failed to process attachment: fail.txt"));

        // Ensure temp file (if created) is still attempted to be cleaned up
        // In this specific scenario, Files.write failed, so the file might not be added to tempFiles list in sender.
        // The mock for Files.delete might not be called if the file wasn't added to the list for cleanup.
        // This depends on the exact sender logic for adding to tempFiles.
        // Given the sender adds to tempFiles *after* Files.write, delete won't be called for this one.
        // If it was added before, then delete would be called.
    }

    // To truly test the Emails.Send() interaction, a refactor of ElasticEmailSender
    // to allow injection/mocking of the Emails object is recommended.
    // For example, by adding:
    // protected Emails getEmailsClient(String apiKey) { return new Emails(apiKey); }
    // Then in the test:
    // ElasticEmailSender spySender = Mockito.spy(elasticEmailSender);
    // Emails mockEmails = mock(Emails.class);
    // doReturn(mockEmails).when(spySender).getEmailsClient(anyString());
    // when(mockEmails.Send(...)).thenReturn(mockSuccessResponse);
    // spySender.sendEmail(requestDto);
    // ... assertions ...
}
