package com.openlinklearn.push.infrastructure;

public interface WebPushProvider {

    void send(Subscription subscription, String payload, int ttlSeconds);

    record Subscription(String endpoint, String p256dhKey, String authKey) {

        @Override
        public String toString() {
            return "Subscription[endpoint=<redacted>, p256dhKey=<redacted>, authKey=<redacted>]";
        }
    }
}