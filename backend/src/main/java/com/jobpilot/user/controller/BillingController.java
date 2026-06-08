package com.jobpilot.user.controller;

import com.jobpilot.common.dto.ApiResponse;
import com.jobpilot.common.util.SecurityUtils;
import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.jobpilot.user.repository.UserRepository;
import com.jobpilot.user.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Manage subscriptions and credits")
public class BillingController {

    private final UserRepository userRepository;
    private final StripeService stripeService;

    @Value("${stripe.webhook.secret:whsec_test_secret}")
    private String endpointSecret;

    @PostMapping("/create-checkout-session")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create Stripe Checkout Session for PRO upgrade")
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckoutSession() {
        try {
            UUID userId = SecurityUtils.getCurrentUserId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String checkoutUrl = stripeService.createCheckoutSession(userId, user.getEmail());

            return ResponseEntity.ok(ApiResponse.ok(
                    Map.of("url", checkoutUrl),
                    "Checkout session created"
            ));
        } catch (StripeException e) {
            log.error("Stripe error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create checkout session"));
        }
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe Webhook Endpoint")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        Event event = null;
        try {
            // Using a dummy secret for local dev if not configured
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            log.warn("Webhook signature verification failed (this is expected if testing without proper webhook secret): {}", e.getMessage());
            // For local development without proper webhooks, we can simulate parsing or just fail.
            // Returning 400 stops Stripe from retrying infinitely for bad signatures.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        // Handle the checkout.session.completed event
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session != null) {
                String clientReferenceId = session.getClientReferenceId();
                stripeService.handleSuccessfulPayment(clientReferenceId);
            }
        }

        return ResponseEntity.ok("Success");
    }
}
