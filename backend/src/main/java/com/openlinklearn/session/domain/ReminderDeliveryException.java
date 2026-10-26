package com.openlinklearn.session.domain;

public class ReminderDeliveryException extends RuntimeException {

    private final boolean retryable;

    public ReminderDeliveryException(boolean retryable, String message) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}