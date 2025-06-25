package com.wisestep.emailing.service.oauth;

import com.microsoft.aad.msal4j.*;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.GraphServiceClient;

import okhttp3.Request; // Ensure this is the correct Request if needed by Graph SDK, or remove if not directly used.
// It's more likely GraphServiceClient handles OkHttp internally.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
public class MicrosoftOAuthHelperService {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftOAuthHelperService.class);

    private final String clientId;
    private final String clientSecret;
    private final String authority;
    private final String redirectUri;
    private final Set<String> scopes;

    private IConfidentialClientApplication confidentialClientApplication;

    public MicrosoftOAuthHelperService(
            @Value("${microsoft.oauth.clientId}") String clientId,
            @Value("${microsoft.oauth.clientSecret}") String clientSecret,
            @Value("${microsoft.oauth.authority}") String authority,
            @Value("${microsoft.oauth.redirectUri}") String redirectUri,
            @Value("${microsoft.oauth.scopes}") String scopesString) throws MalformedURLException {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authority = authority;
        this.redirectUri = redirectUri;
        this.scopes = Arrays.stream(scopesString.split(",\\s*")).collect(Collectors.toSet());

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty() ||
            "YOUR_MICROSOFT_CLIENT_ID".equals(clientId) || "YOUR_MICROSOFT_CLIENT_SECRET".equals(clientSecret)) {
            logger.warn("Microsoft OAuth client ID or secret is not configured or using placeholder values. Microsoft OAuth flows will likely fail.");
            // MSAL app creation might fail here if authority is also bad / not reachable
            // For robustness, could delay client app creation or handle failure more gracefully.
        }
        // Initialize the ConfidentialClientApplication
        // This can throw MalformedURLException if authority is bad
        try {
             this.confidentialClientApplication = ConfidentialClientApplication.builder(
                        clientId, ClientCredentialFactory.createFromSecret(clientSecret))
                .authority(this.authority)
                .build();
        } catch (MalformedURLException e) {
            logger.error("Failed to initialize Microsoft ConfidentialClientApplication due to malformed authority URL: {}", this.authority, e);
            throw e; // Propagate to prevent service creation if critically misconfigured
        }

    }

    public String buildAuthorizationUrl(String userId, String csrfToken) throws MalformedURLException {
        String state = userId + ":" + csrfToken;
        AuthorizationRequestUrlParameters parameters = AuthorizationRequestUrlParameters
                .builder(redirectUri, scopes)
                .responseMode(ResponseMode.QUERY) // Or FORM_POST
                .prompt(Prompt.SELECT_ACCOUNT)    // Or CONSENT, LOGIN, NONE
                .state(state)
                .build();
        return confidentialClientApplication.getAuthorizationRequestUrl(parameters).toString();
    }

    public IAuthenticationResult exchangeCodeForTokens(String authCode) throws Exception {
        logger.debug("Exchanging Microsoft auth code for tokens. Redirect URI: {}", redirectUri);
        AuthorizationCodeParameters parameters = AuthorizationCodeParameters.builder(
                        authCode, new URI(redirectUri))
                .scopes(scopes)
                .build();
        try {
            CompletableFuture<IAuthenticationResult> future = confidentialClientApplication.acquireToken(parameters);
            IAuthenticationResult result = future.join(); // Wait for completion
            logger.info("Successfully exchanged auth code for MSAL tokens. Account: {}", result.account().username());
            return result;
        } catch (Exception e) {
            logger.error("Error exchanging Microsoft auth code for tokens: {}", e.getMessage(), e);
            throw e;
        }
    }

    public IAuthenticationResult refreshAccessToken(String refreshTokenValue, IAccount account) throws Exception {
         logger.debug("Refreshing Microsoft access token for account: {}", account != null ? account.username() : "unknown (account was null)");
         if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
             logger.error("Refresh token is null or empty, cannot refresh access token.");
             throw new IllegalArgumentException("Refresh token cannot be null or empty.");
         }
        // MSAL does not have a direct acquireTokenByRefreshToken method that takes the refresh token string directly
        // for ConfidentialClientApplication in the same way some other libraries do.
        // Instead, MSAL caches the refresh token associated with an IAccount.
        // You typically use acquireTokenSilently, and MSAL uses the cached refresh token if the access token is expired.

        SilentParameters silentParameters;
        if (account != null) {
            silentParameters = SilentParameters.builder(scopes, account).authority(authority).build();
        } else {
            // If account is not available (e.g., not cached or passed), silent flow won't work easily.
            // This indicates a potential issue in how the refresh token or account context is managed.
            // For long-lived server-side scenarios without user interaction, storing and reusing refresh tokens
            // with acquireTokenByRefreshToken (if available for the flow type, or by constructing appropriate parameters) is key.
            // The MSAL Java docs suggest that for confidential clients, the refresh token is typically handled by MSAL's cache
            // when you get the IAccount object from the initial acquireToken call.
            // If you only have the refresh token string, you might need to re-evaluate the flow or how MSAL expects to use it.
            // For now, let's assume account object is available from previous auth or from UserEmailAccount entity.
             logger.error("IAccount object is null. Cannot perform silent token acquisition for refresh. Re-authentication might be needed.");
            throw new IllegalStateException("IAccount is required for silent token refresh with MSAL.");
        }

        try {
            CompletableFuture<IAuthenticationResult> future = confidentialClientApplication.acquireTokenSilently(silentParameters);
            IAuthenticationResult result = future.join();
            logger.info("Successfully refreshed MSAL tokens silently for account: {}.", result.account().username());
            return result;
        } catch (MsalInteractionRequiredException e) {
            logger.warn("Silent token acquisition failed for account {}, interaction required: {}. This might happen if refresh token is invalid or permissions changed.", account.username(), e.getMessage());
            // This means silent refresh failed, and user interaction (re-authentication) is needed.
            // Or, if you had the refresh token string explicitly and MSAL supported it for this client type,
            // you might try acquireTokenByRefreshToken.
            // For now, we re-throw or handle as an error indicating re-auth is needed.
            throw e;
        } catch (Exception e) {
            logger.error("Error refreshing Microsoft access token for account {}: {}", account.username(), e.getMessage(), e);
            throw e;
        }
    }

    // Simpler refresh if only refresh token string is available and using a flow that supports it
    // This is more typical for web apps where you might store the refresh token string.
    // Check MSAL documentation for the best way to use a raw refresh token string with ConfidentialClientApplication.
    // It might involve `RefreshTokenParameters`.
    public IAuthenticationResult acquireTokenByRefreshToken(String refreshTokenString) throws Exception {
        logger.debug("Attempting to acquire token by refresh token string for Microsoft Graph.");
        if (refreshTokenString == null || refreshTokenString.isEmpty()) {
            throw new IllegalArgumentException("Refresh token string cannot be null or empty.");
        }
        RefreshTokenParameters parameters = RefreshTokenParameters.builder(scopes, refreshTokenString).build();
        try {
            CompletableFuture<IAuthenticationResult> future = confidentialClientApplication.acquireToken(parameters);
            IAuthenticationResult result = future.join();
            logger.info("Successfully acquired token using refresh token string for account: {}", result.account().username());
            return result;
        } catch (Exception e) {
            logger.error("Error acquiring token by refresh token string for Microsoft Graph: {}", e.getMessage(), e);
            throw e;
        }
    }


    public String getUserEmail(String accessToken) throws IOException {
        logger.debug("Fetching user email from Microsoft Graph with access token.");

        GraphServiceClient<okhttp3.Request> graphClient = GraphServiceClient.builder()
            .authenticationProvider(request -> {
                // This is a simple way to add the token.
                // For more robust scenarios, use an AuthenticationProvider implementation from msgraph-sdk-java-auth.
                request.addHeader("Authorization", "Bearer " + accessToken);
            }).buildClient();

        try {
            User user = graphClient.me().buildRequest().get();
            if (user == null || user.mail == null || user.mail.isEmpty()) {
                 // Fallback to userPrincipalName if mail is not set (common for some account types)
                if (user != null && user.userPrincipalName != null && !user.userPrincipalName.isEmpty()) {
                    logger.info("Fetched user UPN (as email) from Microsoft Graph: {}", user.userPrincipalName);
                    return user.userPrincipalName;
                }
                logger.warn("Could not retrieve primary email (mail field) or UPN from Microsoft Graph for the user.");
                return null; // Or throw
            }
            logger.info("Fetched user email from Microsoft Graph: {}", user.mail);
            return user.mail;
        } catch (Exception e) {
            logger.error("Error fetching user email from Microsoft Graph: {}", e.getMessage(), e);
            // Convert to IOException or a custom app exception if preferred
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to get user email from Microsoft Graph.", e);
        }
    }
}
