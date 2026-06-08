package com.jobpilot.common.exception;

/**
 * Thrown when a user has exhausted their AI credit quota.
 */
public class CreditExhaustedException extends RuntimeException {
    public CreditExhaustedException(String message) {
        super(message);
    }
}
