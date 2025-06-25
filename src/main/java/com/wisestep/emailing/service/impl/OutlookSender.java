package com.wisestep.emailing.service.impl;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.SilentParameters;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.HeaderOption;
import com.microsoft.graph.requests.AttachmentCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;

import com.wisestep.emailing.domain.user.UserEmailAccount;
import com.wisestep.emailing.domain.user.UserEmailAccountRepository;
import com.wisestep.emailing.dto.AttachmentDto;
import com.wisestep.emailing.dto.EmailRequestDto;
import com.wisestep.emailing.dto.EmailResponseDto;
import com.wisestep.emailing.service.EmailSender;
import com.wisestep.emailing.service.oauth.MicrosoftOAuthHelperService;
import com.wisestep.emailing.service.security.TokenEncryptionService;

import okhttp3.Request; // MS Graph SDK uses OkHttp
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


@Service("outlook")
public class OutlookSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(OutlookSender.class);

    private final UserEmailAccountRepository userEmailAccountRepository;
    private final MicrosoftOAuthHelperService microsoftOAuthHelperService;
    private final TokenEncryptionService tokenEncryptionService;

    @Autowired
    public OutlookSender(UserEmailAccountRepository userEmailAccountRepository,
                         MicrosoftOAuthHelperService microsoftOAuthHelperService,
                         TokenEncryptionService tokenEncryptionService) {
        this.userEmailAccountRepository = userEmailAccountRepository;
        this.microsoftOAuthHelperService = microsoftOAuthHelperService;
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @Override
    public EmailResponseDto sendEmail(EmailRequestDto requestDto) {
        String userAccountEmail = requestDto.getFrom();
        String userId = requestDto.getUserId(); // Get userId from DTO
        logger.info("Attempting to send email via Outlook (Graph API) for user ID: {}, account: {}", userId, userAccountEmail);

        if (userId == null || userId.isEmpty()) {
            logger.error("User ID is missing in the request for Outlook sending. Cannot identify user account.");
            return new EmailResponseDto("Error", "User ID is required for sending via linked Outlook account.");
        }

        UserEmailAccount account = userEmailAccountRepository.findByUserIdAndProviderAndAccountEmail(userId, "outlook", userAccountEmail)
                .orElse(null);

        if (account == null) {
            logger.error("No Outlook account configured for user ID {} and email: {}", userId, userAccountEmail);
            return new EmailResponseDto("Error", "Outlook account not configured for user " + userId + " with email " + userAccountEmail);
        }
        if (account.getEncryptedRefreshToken() == null && account.getAccessToken() == null) { // Check if any token exists
             logger.error("Missing tokens for Outlook account: {}. Re-authorization might be needed.", userAccountEmail);
            return new EmailResponseDto("Error", "Missing tokens for Outlook account: " + userAccountEmail + ". Please re-authorize.");
        }


        try {
            String accessToken = null;
            IAccount msalAccount = null; // MSAL's IAccount object, if available/rehydrated

            // Attempt to get current access token or refresh
            if (account.getAccessToken() != null && account.getAccessTokenExpiresAt() != null &&
                LocalDateTime.now().isBefore(account.getAccessTokenExpiresAt().minusMinutes(5))) {
                accessToken = tokenEncryptionService.decrypt(account.getAccessToken());
                logger.debug("Using stored, valid access token for Outlook account {}", userAccountEmail);
            } else {
                logger.info("Access token for Outlook account {} is expired, missing, or nearing expiry. Attempting refresh.", userAccountEmail);
                String refreshTokenString = null;
                if (account.getEncryptedRefreshToken() != null) {
                    refreshTokenString = tokenEncryptionService.decrypt(account.getEncryptedRefreshToken());
                }

                if (refreshTokenString != null && !refreshTokenString.isEmpty()) {
                     // Try to use acquireTokenByRefreshToken first as it's more direct if we have the string
                    try {
                        IAuthenticationResult authResult = microsoftOAuthHelperService.acquireTokenByRefreshToken(refreshTokenString);
                        accessToken = authResult.accessToken();
                        msalAccount = authResult.account(); // Get the IAccount for future silent calls

                        // Update stored tokens
                        account.setAccessToken(tokenEncryptionService.encrypt(accessToken));
                        // MSAL IAuthenticationResult doesn't directly give 'expires_in_seconds'.
                        // It's often derived from idToken or managed internally.
                        // For simplicity, setting a fixed window or deriving from idToken claims if available.
                        // Long exp = (Long) authResult.account().idTokenClaims().get("exp"); // Example
                        account.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Defaulting to 1 hour

                        // MSAL typically handles refresh token rotation; the new IAuthenticationResult might contain an updated refresh token.
                        // However, the explicit refresh token string might not change unless it's a very specific flow.
                        // If authResult.refreshToken() is available and different, update it.
                        // For now, we assume the existing refresh token remains valid or MSAL handles its update.
                        if (authResult.refreshToken() != null && !authResult.refreshToken().equals(refreshTokenString)) {
                            account.setEncryptedRefreshToken(tokenEncryptionService.encrypt(authResult.refreshToken()));
                             logger.info("Outlook refresh token was updated during access token refresh for {}", userAccountEmail);
                        }
                        userEmailAccountRepository.save(account);
                        logger.info("Access token for Outlook account {} refreshed successfully.", userAccountEmail);
                    } catch (Exception refreshException) {
                        logger.error("Failed to refresh access token for Outlook account {} using refresh token string: {}. Re-authorization needed.", userAccountEmail, refreshException.getMessage());
                        return new EmailResponseDto("Error", "Failed to refresh Outlook session. Please re-authorize. Details: " + refreshException.getMessage());
                    }
                } else {
                     logger.error("No refresh token available for Outlook account {} to refresh access token. Re-authorization needed.", userAccountEmail);
                    return new EmailResponseDto("Error", "Outlook session expired and no refresh token available. Please re-authorize.");
                }
            }


            GraphServiceClient<okhttp3.Request> graphClient = GraphServiceClient.builder()
                .authenticationProvider(request -> {
                    request.addHeader("Authorization", "Bearer " + accessToken);
                }).buildClient();

            Message graphMessage = new Message();
            graphMessage.subject = requestDto.getSubject();

            ItemBody body = new ItemBody();
            body.contentType = BodyType.HTML; // Assuming HTML body
            body.content = requestDto.getBody();
            graphMessage.body = body;

            LinkedList<Recipient> toRecipientsList = new LinkedList<>();
            Recipient toRecipient = new Recipient();
            EmailAddress toEmailAddress = new EmailAddress();
            toEmailAddress.address = requestDto.getTo();
            toRecipient.emailAddress = toEmailAddress;
            toRecipientsList.add(toRecipient);
            graphMessage.toRecipients = toRecipientsList;

            // From address is implicitly the authenticated user.
            // If you need to set a specific "from" (e.g. shared mailbox if permissions allow),
            // you'd set graphMessage.from = ... but this requires 'Mail.Send.Shared' permissions.

            if (requestDto.getAttachments() != null && !requestDto.getAttachments().isEmpty()) {
                graphMessage.attachments = new AttachmentCollectionPage();
                graphMessage.attachments.getCurrentPage().ensureCapacity(requestDto.getAttachments().size());

                for (AttachmentDto attachmentDto : requestDto.getAttachments()) {
                    FileAttachment fileAttachment = new FileAttachment();
                    fileAttachment.oDataType = "#microsoft.graph.fileAttachment";
                    fileAttachment.name = attachmentDto.getFilename();
                    fileAttachment.contentType = attachmentDto.getContentType();
                    fileAttachment.contentBytes = java.util.Base64.getDecoder().decode(attachmentDto.getData());
                    graphMessage.attachments.getCurrentPage().add(fileAttachment);
                }
            }

            // Send the message
            // The 'me' user context is used for sending.
            graphClient.me().sendMail(graphMessage, true).buildRequest().post(); // saveToSentItems = true

            // Graph API's sendMail is void and doesn't return the message ID directly in this call.
            // To get the ID, you'd typically create a draft, get its ID, then send the draft.
            // Or, rely on webhooks / Graph notifications if tracking is needed.
            // For now, we don't have a direct message ID from this sendMail call.
            logger.info("Email sent successfully via Outlook (Graph API) for account {}.", userAccountEmail);
            return new EmailResponseDto("Success", "Email sent successfully via Outlook.", null); // No direct message ID from this Graph call

        } catch (Exception e) {
            logger.error("Error sending email via Outlook for account {}: {}", userAccountEmail, e.getMessage(), e);
            return new EmailResponseDto("Error", "Failed to send email via Outlook: " + e.getMessage());
        }
    }
}
