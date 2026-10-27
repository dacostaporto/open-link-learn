package com.openlinklearn.push.infrastructure;

public class WebPushProviderException extends RuntimeException {

    private final int statusCode;
    private final boolean expired;
    private final boolean retryable;
    private final String providerReason;

    public WebPushProviderException(int statusCode, boolean expired, boolean retryable) {
        this(statusCode, expired, retryable, null);
    }

    public WebPushProviderException(int statusCode, boolean expired, boolean retryable, String providerReason) {
        super("Web Push delivery failed with provider status " + statusCode + ".");
        this.statusCode = statusCode;
        this.expired = expired;
        this.retryable = retryable;
        this.providerReason = providerReason;
    }

    public WebPushProviderException(boolean retryable) {
        super("Web Push provider transport failed.");
        this.statusCode = 0;
        this.expired = false;
        this.retryable = retryable;
        this.providerReason = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getProviderReason() {
        return providerReason;
    }
}