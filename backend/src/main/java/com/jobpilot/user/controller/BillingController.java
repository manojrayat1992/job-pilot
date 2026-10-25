package com.jobpilot.user.controller;

import com.jobpilot.common.exception.FeatureUnavailableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paid upgrades remain closed until the subscription lifecycle is implemented. */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    @PostMapping("/create-checkout-session")
    public void createCheckoutSession() {
        throw new FeatureUnavailableException("Paid upgrades are not available yet. No payment was initiated.");
    }

    @PostMapping("/webhook")
    public void handleStripeWebhook() {
        // A non-2xx response avoids acknowledging an event we have not processed.
        throw new FeatureUnavailableException("Billing event processing is not available yet.");
    }
}
