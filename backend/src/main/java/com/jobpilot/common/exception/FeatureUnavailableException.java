package com.jobpilot.common.exception;

/** A feature has no production integration yet and must not simulate success. */
public class FeatureUnavailableException extends RuntimeException {
    public FeatureUnavailableException(String message) {
        super(message);
    }
}
