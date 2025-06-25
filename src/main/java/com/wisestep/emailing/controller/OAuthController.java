package com.wisestep.emailing.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.wisestep.emailing.domain.user.UserEmailAccount;
import com.wisestep.emailing.domain.user.UserEmailAccountRepository;
import com.wisestep.emailing.service.oauth.GoogleOAuthHelperService;
// Import MicrosoftOAuthHelperService when created
import com.wisestep.emailing.service.security.TokenEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest; // For session
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

@Controller
@RequestMapping("/oauth2")
public class OAuthController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);
    private static final String CSRF_TOKEN_SESSION_ATTR_PREFIX = "oauthCsrfToken_";
    private static final String USER_ID_SESSION_ATTR_PREFIX = "oauthUserId_";


    private final GoogleOAuthHelperService googleOAuthHelperService;
    private final GoogleOAuthHelperService googleOAuthHelperService;
    private final MicrosoftOAuthHelperService microsoftOAuthHelperService;
    private final UserEmailAccountRepository userEmailAccountRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${google.oauth.redirectUri}")
    private String googleRedirectUri;

    @Value("${microsoft.oauth.redirectUri}")
    private String microsoftRedirectUri;

    // Example: Redirect to frontend. These should be configurable.
    @Value("${app.oauth.successRedirectUri:http://localhost:3000/oauth-success}")
    private String successRedirectUri;

    @Value("${app.oauth.failureRedirectUri:http://localhost:3000/oauth-failure}")
    private String failureRedirectUri;


    @Autowired
    public OAuthController(
            GoogleOAuthHelperService googleOAuthHelperService,
            MicrosoftOAuthHelperService microsoftOAuthHelperService,
            UserEmailAccountRepository userEmailAccountRepository,
            TokenEncryptionService tokenEncryptionService) {
        this.googleOAuthHelperService = googleOAuthHelperService;
        this.microsoftOAuthHelperService = microsoftOAuthHelperService;
        this.userEmailAccountRepository = userEmailAccountRepository;
        this.tokenEncryptionService = tokenEncryptionService;
    }

    @GetMapping("/initiate/google")
    public ResponseEntity<Void> initiateGoogleOAuth(
            @RequestParam("userId") String userId, // Assuming userId is passed by the client
            HttpServletRequest request) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                logger.warn("User ID is required to initiate OAuth flow.");
                // Consider redirecting to an error page or returning an error response
                return ResponseEntity.badRequest().build();
            }
            String csrfToken = UUID.randomUUID().toString();
            HttpSession session = request.getSession(true);
            session.setAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId, csrfToken);
            // session.setAttribute(USER_ID_SESSION_ATTR_PREFIX + csrfToken, userId); // Store userId mapped by CSRF for callback

            String authorizationUrl = googleOAuthHelperService.buildAuthorizationUrl(userId, csrfToken, googleRedirectUri);
            logger.info("Redirecting user {} to Google for OAuth. CSRF token set.", userId);
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, authorizationUrl).build();
        } catch (Exception e) {
            logger.error("Error initiating Google OAuth flow for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/callback/google")
    public ResponseEntity<Void> handleGoogleOAuthCallback(
            @RequestParam("code") String authCode,
            @RequestParam("state") String state,
            HttpServletRequest request) {
        logger.info("Received Google OAuth callback with code and state.");
        String finalRedirectUrl = failureRedirectUri + "?provider=google&error=unknown";

        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                logger.warn("No session found for Google OAuth callback. State validation failed.");
                finalRedirectUrl += "_session_expired";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }

            String[] stateParts = state.split(":", 2);
            if (stateParts.length != 2) {
                logger.warn("Invalid state parameter received: {}", state);
                finalRedirectUrl += "_invalid_state_format";
                 return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }
            String userId = stateParts[0];
            String expectedCsrfToken = stateParts[1];

            String storedCsrfToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId);
            session.removeAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId); // Consume token

            if (storedCsrfToken == null || !storedCsrfToken.equals(expectedCsrfToken)) {
                logger.warn("CSRF token mismatch for user {}. Stored: '{}', Received in state: '{}'", userId, storedCsrfToken, expectedCsrfToken);
                finalRedirectUrl += "_csrf_mismatch";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }

            logger.info("CSRF token validated for user {}.", userId);

            GoogleTokenResponse tokenResponse = googleOAuthHelperService.exchangeCodeForTokens(authCode, googleRedirectUri);
            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken(); // May be null if already granted and not requested with approval_prompt=force
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();
            List<String> scopes = tokenResponse.getScope() != null ? Arrays.asList(tokenResponse.getScope().split(" ")) : Collections.emptyList();


            String accountEmail = googleOAuthHelperService.getUserEmail(accessToken);
            if (accountEmail == null || accountEmail.isEmpty()) {
                logger.error("Could not retrieve user email from Google for user {}.", userId);
                 finalRedirectUrl += "_email_retrieval_failed";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }
            logger.info("Retrieved Google account email {} for user {}.", accountEmail, userId);


            Optional<UserEmailAccount> existingAccountOpt = userEmailAccountRepository.findByUserIdAndProviderAndAccountEmail(userId, "gmail", accountEmail);
            UserEmailAccount userAccount = existingAccountOpt.orElse(new UserEmailAccount());

            userAccount.setUserId(userId);
            userAccount.setProvider("gmail");
            userAccount.setAccountEmail(accountEmail);
            userAccount.setAccessToken(tokenEncryptionService.encrypt(accessToken)); // Encrypt access token too
            if (expiresInSeconds != null) {
                userAccount.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
            }
            if (refreshToken != null && !refreshToken.isEmpty()) { // Only update refresh token if a new one is provided
                userAccount.setEncryptedRefreshToken(tokenEncryptionService.encrypt(refreshToken));
                logger.info("New refresh token received and encrypted for user {}, email {}.", userId, accountEmail);
            } else if (userAccount.getId() == null) { // New account and no refresh token
                 logger.warn("No refresh token received for new Gmail account for user {}, email {}. Access might be short-lived or flow needs approval_prompt=force.", userId, accountEmail);
                 // This is problematic for long-term access.
            }
            userAccount.setScopes(String.join(",", scopes));

            userEmailAccountRepository.save(userAccount);
            logger.info("Successfully saved/updated Gmail account for user {}, email {}.", userId, accountEmail);
            finalRedirectUrl = successRedirectUri + "?provider=google";

        } catch (IOException e) {
            logger.error("IOException during Google OAuth callback for state {}: {}", state, e.getMessage(), e);
            finalRedirectUrl += "_io_exception";
        } catch (Exception e) {
            logger.error("Unexpected error during Google OAuth callback for state {}: {}", state, e.getMessage(), e);
            finalRedirectUrl += "_internal_error";
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
    }

    // TODO: Add similar /initiate/microsoft and /callback/microsoft endpoints

    @GetMapping("/initiate/microsoft")
    public ResponseEntity<Void> initiateMicrosoftOAuth(
            @RequestParam("userId") String userId,
            HttpServletRequest request) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                logger.warn("User ID is required to initiate Microsoft OAuth flow.");
                return ResponseEntity.badRequest().build();
            }
            String csrfToken = UUID.randomUUID().toString();
            HttpSession session = request.getSession(true);
            session.setAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId + "_microsoft", csrfToken); // Provider-specific CSRF

            String authorizationUrl = microsoftOAuthHelperService.buildAuthorizationUrl(userId, csrfToken, microsoftRedirectUri);
            logger.info("Redirecting user {} to Microsoft for OAuth. CSRF token set.", userId);
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, authorizationUrl).build();
        } catch (Exception e) {
            logger.error("Error initiating Microsoft OAuth flow for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/callback/microsoft")
    public ResponseEntity<Void> handleMicrosoftOAuthCallback(
            @RequestParam("code") String authCode,
            @RequestParam("state") String state,
            HttpServletRequest request) {
        logger.info("Received Microsoft OAuth callback with code and state.");
        String finalRedirectUrl = failureRedirectUri + "?provider=microsoft&error=unknown";

        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                logger.warn("No session found for Microsoft OAuth callback. State validation failed.");
                finalRedirectUrl += "_session_expired";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }

            String[] stateParts = state.split(":", 2);
            if (stateParts.length != 2) {
                logger.warn("Invalid state parameter received from Microsoft: {}", state);
                finalRedirectUrl += "_invalid_state_format";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }
            String userId = stateParts[0];
            String expectedCsrfToken = stateParts[1];

            String storedCsrfToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId + "_microsoft");
            session.removeAttribute(CSRF_TOKEN_SESSION_ATTR_PREFIX + userId + "_microsoft");

            if (storedCsrfToken == null || !storedCsrfToken.equals(expectedCsrfToken)) {
                logger.warn("Microsoft CSRF token mismatch for user {}. Stored: '{}', Received: '{}'", userId, storedCsrfToken, expectedCsrfToken);
                finalRedirectUrl += "_csrf_mismatch";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }
            logger.info("Microsoft CSRF token validated for user {}.", userId);

            com.microsoft.aad.msal4j.IAuthenticationResult authResult = microsoftOAuthHelperService.exchangeCodeForTokens(authCode);
            String accessToken = authResult.accessToken();
            String refreshToken = authResult.refreshToken(); // MSAL typically manages refresh token internally via its cache for confidential clients. Explicit refresh token might not always be directly exposed or needed for storage if IAccount is used for subsequent silent calls.
                                                            // However, if we want to store it for long-term use independent of MSAL's cache, we should ensure it's available.
                                                            // For web apps, `acquireTokenByAuthorizationCode` does return a refresh token that can be persisted.

            if (refreshToken == null || refreshToken.isEmpty()) {
                 logger.warn("No refresh token explicitly received from MSAL for user {}. MSAL will manage it in its cache. Ensure offline_access scope was granted.", userId);
                 // If MSAL manages it, we might not need to store it ourselves, but then our app is tied to MSAL's cache lifetime for that user session or IAccount object persistence.
                 // For durable refresh tokens, ensuring 'offline_access' scope is requested is key.
            }


            String accountEmail = microsoftOAuthHelperService.getUserEmail(accessToken);
            if (accountEmail == null || accountEmail.isEmpty()) {
                logger.error("Could not retrieve user email from Microsoft Graph for user {}.", userId);
                finalRedirectUrl += "_email_retrieval_failed";
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
            }
            logger.info("Retrieved Microsoft account email {} for user {}.", accountEmail, userId);

            Optional<UserEmailAccount> existingAccountOpt = userEmailAccountRepository.findByUserIdAndProviderAndAccountEmail(userId, "outlook", accountEmail);
            UserEmailAccount userAccount = existingAccountOpt.orElse(new UserEmailAccount());

            userAccount.setUserId(userId);
            userAccount.setProvider("outlook");
            userAccount.setAccountEmail(accountEmail);
            userAccount.setAccessToken(tokenEncryptionService.encrypt(accessToken));
            // MSAL's IAuthenticationResult might not directly expose 'expires_in' in the same way as GoogleTokenResponse.
            // It's often relative to the token issuance time. The IAccount object or token cache entry has expiry info.
            // For simplicity, let's assume a default or calculate from idTokenClaims if needed.
            // Typically, you'd store the IAccount or necessary details to reconstruct it for silent calls.
            // For now, let's set a placeholder expiry or derive it if possible.
            // If authResult.account().idTokenClaims().get("exp") gives epoch seconds:
            // Long exp = (Long) authResult.account().idTokenClaims().get("exp");
            // userAccount.setAccessTokenExpiresAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(exp), ZoneOffset.UTC));
            // For now, let's set a fixed duration for simplicity, e.g., 1 hour, as MSAL manages true expiry.
            userAccount.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));


            if (refreshToken != null && !refreshToken.isEmpty()) {
                 userAccount.setEncryptedRefreshToken(tokenEncryptionService.encrypt(refreshToken));
                 logger.info("Microsoft refresh token received and encrypted for user {}, email {}.", userId, accountEmail);
            } else if (userAccount.getId() == null) { // New account and no explicit refresh token string
                 logger.warn("No explicit refresh token string received for new Outlook account for user {}, email {}. MSAL manages this via its token cache and IAccount. Ensure 'offline_access' scope.", userId, accountEmail);
            }


            userAccount.setScopes(String.join(",", microsoftOAuthHelperService.scopes)); // Use configured scopes

            userEmailAccountRepository.save(userAccount);
            logger.info("Successfully saved/updated Outlook account for user {}, email {}.", userId, accountEmail);
            finalRedirectUrl = successRedirectUri + "?provider=microsoft";

        } catch (Exception e) {
            logger.error("Error during Microsoft OAuth callback for state {}: {}", state, e.getMessage(), e);
            finalRedirectUrl += "_internal_error";
             if (e.getMessage() != null && e.getMessage().contains("AADSTS")) { // Specific AAD error
                finalRedirectUrl += "_" + extractAadErrorCode(e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, finalRedirectUrl).build();
    }

    private String extractAadErrorCode(String errorMessage) {
        // Basic extraction, can be improved
        if (errorMessage == null) return "unknown_aad_error";
        int startIndex = errorMessage.indexOf("AADSTS");
        if (startIndex != -1) {
            int endIndex = errorMessage.indexOf(":", startIndex);
            if (endIndex == -1) endIndex = errorMessage.indexOf(" ", startIndex);
            if (endIndex == -1) endIndex = errorMessage.length();
            return errorMessage.substring(startIndex, Math.min(endIndex, startIndex + 20)).replaceAll("[^a-zA-Z0-9]", "_");
        }
        return "processing_error";
    }
}
