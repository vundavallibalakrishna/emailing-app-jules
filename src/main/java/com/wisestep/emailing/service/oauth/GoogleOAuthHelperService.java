package com.wisestep.emailing.service.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfo;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

@Service
public class GoogleOAuthHelperService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleOAuthHelperService.class);

    private final String clientId;
    private final String clientSecret;
    private final List<String> scopes;

    private final NetHttpTransport httpTransport = new NetHttpTransport();
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    final GoogleClientSecrets clientSecrets; // Made package-private for GmailSender or add getter


    public GoogleOAuthHelperService(
            @Value("${google.oauth.clientId}") String clientId,
            @Value("${google.oauth.clientSecret}") String clientSecret,
            @Value("${google.oauth.scopes}") String scopesString) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scopes = Arrays.asList(scopesString.split(",\\s*"));

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()
            || "YOUR_GOOGLE_CLIENT_ID".equals(clientId) || "YOUR_GOOGLE_CLIENT_SECRET".equals(clientSecret) ) {
            logger.warn("Google OAuth client ID or secret is not configured or using placeholder values. Google OAuth flows will likely fail.");
             // Create a dummy clientSecrets to avoid NullPointerException, actual flows will fail if keys are bad
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId("dummy-id");
            details.setClientSecret("dummy-secret");
            this.clientSecrets = new GoogleClientSecrets().setWeb(details);
        } else {
             GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(this.clientId);
            details.setClientSecret(this.clientSecret);
            this.clientSecrets = new GoogleClientSecrets().setWeb(details);
        }
    }

    public String buildAuthorizationUrl(String userId, String csrfToken, String redirectUri) {
        String state = userId + ":" + csrfToken; // Combine userId and csrfToken into state
        return new GoogleAuthorizationCodeRequestUrl(clientSecrets, redirectUri, scopes)
                .setState(state)
                .setAccessType("offline") // Necessary to get a refresh token
                .setApprovalPrompt("force") // Ensures refresh token is granted, can be "auto"
                .build();
    }

    public GoogleTokenResponse exchangeCodeForTokens(String authCode, String redirectUri) throws IOException {
        logger.debug("Exchanging Google auth code for tokens. Redirect URI: {}", redirectUri);
        try {
            return new GoogleAuthorizationCodeTokenRequest(
                    httpTransport, jsonFactory, clientSecrets, authCode, redirectUri)
                    .execute();
        } catch (IOException e) {
            logger.error("Error exchanging Google auth code for tokens: {}", e.getMessage(), e);
            throw e;
        }
    }

    public GoogleTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        logger.debug("Refreshing Google access token.");
        if (refreshToken == null || refreshToken.isEmpty()) {
            logger.error("Refresh token is null or empty, cannot refresh access token.");
            throw new IllegalArgumentException("Refresh token cannot be null or empty.");
        }
        try {
            return new GoogleRefreshTokenRequest(
                    httpTransport, jsonFactory, refreshToken, clientSecrets)
                    .execute();
        } catch (IOException e) {
            logger.error("Error refreshing Google access token: {}", e.getMessage(), e);
            throw e;
        }
    }

    public String getUserEmail(String accessToken) throws IOException {
        logger.debug("Fetching user email from Google with access token.");
        try {
            Credential credential = new GoogleCredential().setAccessToken(accessToken);
            Oauth2 oauth2Service = new Oauth2.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("EmailingApp") // Replace with your app name
                    .build();
            Userinfo userInfo = oauth2Service.userinfo().get().execute();
            logger.info("Fetched user email: {}", userInfo.getEmail());
            return userInfo.getEmail();
        } catch (IOException e) {
            logger.error("Error fetching user email from Google: {}", e.getMessage(), e);
            throw e;
        }
    }
}
