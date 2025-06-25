package com.wisestep.emailing.service.impl;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.wisestep.emailing.domain.user.UserEmailAccount;
import com.wisestep.emailing.domain.user.UserEmailAccountRepository;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import com.wisestep.emailing.service.oauth.GoogleOAuthHelperService;
import com.wisestep.emailing.service.security.TokenEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Properties;

@Service("gmail")
public class GmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(GmailSender.class);

    private final UserEmailAccountRepository userEmailAccountRepository;
    private final GoogleOAuthHelperService googleOAuthHelperService;
    private final TokenEncryptionService tokenEncryptionService;
    private final NetHttpTransport httpTransport = new NetHttpTransport();
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    @Autowired
    public GmailSender(UserEmailAccountRepository userEmailAccountRepository,
                       GoogleOAuthHelperService googleOAuthHelperService,
                       TokenEncryptionService tokenEncryptionService) {
        this.userEmailAccountRepository = userEmailAccountRepository;
        this.googleOAuthHelperService = googleOAuthHelperService;
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        String userAccountEmail = requestDto.getFrom();
        String userId = requestDto.getUserId(); // Get userId from DTO
        logger.info("Attempting to send email via Gmail for user ID: {}, account: {}", userId, userAccountEmail);

        if (userId == null || userId.isEmpty()) {
            logger.error("User ID is missing in the request for Gmail sending. Cannot identify user account.");
            return new EmailResponseDto("Error", "User ID is required for sending via linked Gmail account.");
        }

        UserEmailAccount account = userEmailAccountRepository.findByUserIdAndProviderAndAccountEmail(userId, "gmail", userAccountEmail)
                .orElse(null);

        if (account == null) {
            logger.error("No Gmail account configured for user ID {} and email: {}", userId, userAccountEmail);
            return new EmailResponseDto("Error", "Gmail account not configured for user " + userId + " with email " + userAccountEmail);
        }

        if (account.getEncryptedRefreshToken() == null) {
            logger.error("Missing refresh token for Gmail account: {}. Re-authorization might be needed.", userAccountEmail);
            return new EmailResponseDto("Error", "Missing refresh token for Gmail account: " + userAccountEmail + ". Please re-authorize.");
        }

        try {
            String refreshToken = tokenEncryptionService.decrypt(account.getEncryptedRefreshToken());
            String accessToken = tokenEncryptionService.decrypt(account.getAccessToken()); // Assuming access token is stored and encrypted

            // Check if access token is expired or close to expiry (e.g., within 5 minutes)
            if (accessToken == null || account.getAccessTokenExpiresAt() == null ||
                LocalDateTime.now().isAfter(account.getAccessTokenExpiresAt().minusMinutes(5))) {
                logger.info("Access token for {} is expired or nearing expiry. Refreshing...", userAccountEmail);
                GoogleTokenResponse tokenResponse = googleOAuthHelperService.refreshAccessToken(refreshToken);
                accessToken = tokenResponse.getAccessToken();
                account.setAccessToken(tokenEncryptionService.encrypt(accessToken));
                account.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
                userEmailAccountRepository.save(account); // Save updated token and expiry
                logger.info("Access token refreshed and updated for {}", userAccountEmail);
            }

            Credential credential = new GoogleCredential.Builder()
                    .setClientSecrets(googleOAuthHelperService.clientSecrets) // clientSecrets is package-private or use a getter
                    .setJsonFactory(jsonFactory)
                    .setTransport(httpTransport)
                    .build()
                    .setAccessToken(accessToken)
                    .setRefreshToken(refreshToken); // Refresh token might be used by library if it supports auto-refresh

            Gmail gmailService = new Gmail.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("EmailingApp") // Replace with your app name
                    .build();

            MimeMessage mimeMessage = createMimeMessage(requestDto);
            Message message = createMessageWithEmail(mimeMessage);

            Message sentMessage = gmailService.users().messages().send("me", message).execute();
            logger.info("Email sent successfully via Gmail for account {}. Message ID: {}", userAccountEmail, sentMessage.getId());
            return new EmailResponseDto("Success", "Email sent successfully via Gmail.", sentMessage.getId());

        } catch (Exception e) {
            logger.error("Error sending email via Gmail for account {}: {}", userAccountEmail, e.getMessage(), e);
            // More specific error handling could be added here, e.g., for token refresh failures
            // or specific Gmail API errors.
            return new EmailResponseDto("Error", "Failed to send email via Gmail: " + e.getMessage());
        }
    }

    private MimeMessage createMimeMessage(EmailRequestDto requestDto) throws MessagingException, IOException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(requestDto.getFrom()));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(requestDto.getTo()));
        email.setSubject(requestDto.getSubject());

        MimeMultipart multipart = new MimeMultipart("related");

        // HTML part
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(requestDto.getBody(), "text/html; charset=utf-8");
        multipart.addBodyPart(htmlPart);

        // Attachments
        if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
            for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource source = new ByteArrayDataSource(
                        java.util.Base64.getDecoder().decode(attachmentDto.getData()),
                        attachmentDto.getContentType()
                );
                attachmentPart.setDataHandler(new javax.activation.DataHandler(source));
                attachmentPart.setFileName(attachmentDto.getFilename());
                multipart.addBodyPart(attachmentPart);
            }
        }
        email.setContent(multipart);
        return email;
    }

    private Message createMessageWithEmail(MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        email.writeTo(baos);
        String encodedEmail = Base64.encodeBase64URLSafeString(baos.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }
}
