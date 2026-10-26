package com.openlinklearn.push.application.dto;

public record PushSubscriptionRequest(String endpoint, Keys keys, String userAgent) {

    @Override
    public String toString() {
        return "PushSubscriptionRequest[endpoint=<redacted>, keys=<redacted>, userAgent=" + userAgent + "]";
    }

    public record Keys(String p256dh, String auth) {

        @Override
        public String toString() {
            return "Keys[p256dh=<redacted>, auth=<redacted>]";
        }
    }
}