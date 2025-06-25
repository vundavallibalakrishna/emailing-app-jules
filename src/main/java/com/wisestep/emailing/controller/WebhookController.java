package com.wisestep.emailing.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.helpers.eventwebhook.EventWebhookHelper;
import com.sendgrid.helpers.eventwebhook.BadSignatureException;
import com.wisestep.emailing.service.WebhookEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookEventService webhookEventService;
    private final ObjectMapper objectMapper; // For parsing the request body

    @Value("${sendgrid.webhook.verificationKey}")
    private String sendGridVerificationKey;

    @Autowired
    public WebhookController(WebhookEventService webhookEventService, ObjectMapper objectMapper) {
        this.webhookEventService = webhookEventService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sendgrid")
    public ResponseEntity<String> handleSendGridWebhook(
            @RequestBody String requestBody,
            @RequestHeader("X-Twilio-Email-Event-Webhook-Signature") String signature,
            @RequestHeader("X-Twilio-Email-Event-Webhook-Timestamp") String timestamp) {

        logger.info("Received SendGrid webhook. Timestamp: {}, Signature: {}", timestamp, signature);
        logger.debug("SendGrid webhook payload: {}", requestBody);

        if (sendGridVerificationKey == null || "YOUR_SENDGRID_WEBHOOK_VERIFICATION_KEY".equals(sendGridVerificationKey) || sendGridVerificationKey.isEmpty()) {
            logger.error("SendGrid webhook verification key is not configured. Denying request.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing misconfigured.");
        }

        EventWebhookHelper eventWebhookHelper = new EventWebhookHelper();
        try {
            // Convert the ECDSA public key string (from SendGrid dashboard) to a usable ECPublicKey object
            // The library expects a base64 encoded public key string.
            java.security.PublicKey publicKey = eventWebhookHelper.convertPublicKeyToECDSA(sendGridVerificationKey);

            if (!eventWebhookHelper.verifySignature(publicKey, requestBody, signature, timestamp)) {
                logger.warn("SendGrid webhook signature verification failed.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Signature verification failed.");
            }
            logger.info("SendGrid webhook signature verified successfully.");

            // Parse the JSON request body (it's an array of event objects)
            List<Map<String, Object>> events = objectMapper.readValue(requestBody, new TypeReference<List<Map<String, Object>>>() {});
            webhookEventService.processSendGridEvents(events);

            return ResponseEntity.ok("Events processed.");

        } catch (BadSignatureException e) {
            logger.warn("SendGrid webhook signature verification failed (BadSignatureException): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Signature verification failed.");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error with SendGrid webhook public key processing: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing error (key issue).");
        } catch (Exception e) {
            logger.error("Error processing SendGrid webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook.");
        }
    }
}
