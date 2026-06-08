package com.jobpilot.user.service;

import com.jobpilot.user.entity.User;
import com.jobpilot.user.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final UserRepository userRepository;

    @Value("${stripe.api.key:sk_test_mockKeyForLocalDevelopment123}")
    private String stripeApiKey;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public String createCheckoutSession(UUID userId, String userEmail) throws StripeException {
        SessionCreateParams params =
            SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(userEmail)
                .setClientReferenceId(userId.toString())
                .setSuccessUrl(frontendUrl + "/dashboard?session_id={CHECKOUT_SESSION_ID}&success=true")
                .setCancelUrl(frontendUrl + "/pricing?canceled=true")
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("inr")
                                .setUnitAmount(249900L) // ₹2499.00
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Job Pilot  Pro")
                                        .setDescription("Unlimited AI Agents, Salary Negotiation, and Email Sequences")
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    public void handleSuccessfulPayment(String clientReferenceId) {
        if (clientReferenceId == null) return;
        
        try {
            UUID userId = UUID.fromString(clientReferenceId);
            User user = userRepository.findById(userId).orElse(null);
            
            if (user != null) {
                user.setRole("PRO");
                userRepository.save(user);
                log.info("Successfully upgraded user {} to PRO after payment", user.getEmail());
            } else {
                log.warn("Payment succeeded but user not found for ID: {}", clientReferenceId);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid clientReferenceId format in Stripe webhook: {}", clientReferenceId);
        }
    }
}
