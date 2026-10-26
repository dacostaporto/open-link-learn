package com.openlinklearn.push.infrastructure;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.ExecutionException;

public class VapidWebPushProvider implements WebPushProvider {

    private final PushService pushService;

    public VapidWebPushProvider(VapidProperties properties) {
        if (properties.vapidPublicKey() == null || properties.vapidPublicKey().isBlank()
                || properties.vapidPrivateKey() == null || properties.vapidPrivateKey().isBlank()
                || properties.vapidSubject() == null || properties.vapidSubject().isBlank()) {
            throw new IllegalStateException("Complete VAPID configuration is required when push delivery is enabled.");
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(
                    properties.vapidPublicKey(),
                    properties.vapidPrivateKey(),
                    properties.vapidSubject());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("VAPID key configuration is invalid.");
        }
    }

    @Override
    public void send(Subscription subscription, String payload, int ttlSeconds) {
        try {
                Notification notification = Notification.builder()
                    .endpoint(subscription.endpoint())
                    .userPublicKey(subscription.p256dhKey())
                    .userAuth(subscription.authKey())
                    .payload(payload)
                    .ttl(ttlSeconds)
                    .build();
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                boolean expired = statusCode == 404 || statusCode == 410;
                boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500;
                throw new WebPushProviderException(statusCode, expired, retryable);
            }
        } catch (WebPushProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebPushProviderException(true);
        } catch (IOException | ExecutionException exception) {
            throw new WebPushProviderException(true);
        } catch (GeneralSecurityException | JoseException exception) {
            throw new WebPushProviderException(false);
        }
    }
}