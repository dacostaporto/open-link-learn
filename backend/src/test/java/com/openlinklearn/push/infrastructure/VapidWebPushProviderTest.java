package com.openlinklearn.push.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VapidWebPushProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsFcmErrorStatusAndCodeAndRedactsUrlsAndLongTokens() {
        String responseBody = """
                {
                  "error": {
                    "code": 403,
                    "status": "PERMISSION_DENIED",
                    "message": "Request to https://fcm.googleapis.com/fcm/send/private-token denied for bearer abcdefghijklmnopqrstuvwxyz0123456789",
                    "details": [
                      {"errorCode": "THIRD_PARTY_AUTH_ERROR"}
                    ]
                  }
                }
                """;

        String reason = VapidWebPushProvider.extractProviderReason(responseBody, objectMapper);

        assertEquals(
                "PERMISSION_DENIED; THIRD_PARTY_AUTH_ERROR; Request to [url] denied for [redacted]",
                reason);
        assertFalse(reason.contains("private-token"));
        assertFalse(reason.contains("abcdefghijklmnopqrstuvwxyz0123456789"));
    }

    @Test
    void capturesSanitizedNonJsonAndUnrecognizedJsonBodies() {
        assertEquals("forbidden", VapidWebPushProvider.extractProviderReason("forbidden", objectMapper));
        assertEquals(
                "{\"message\":\"forbidden\"}",
                VapidWebPushProvider.extractProviderReason("{\"message\":\"forbidden\"}", objectMapper));
    }

    @Test
    void reportsEmptyProviderResponseBodies() {
        assertEquals("provider_response_body_empty", VapidWebPushProvider.extractProviderReason("", objectMapper));
        assertEquals("provider_response_body_empty", VapidWebPushProvider.extractProviderReason(null, objectMapper));
    }

    @Test
    void createsFcmCryptoKeyWithUnpaddedVapidPublicKey() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair serverKeys = generator.generateKeyPair();
        KeyPair subscriptionKeys = generator.generateKeyPair();
        String serverPublicKey = encodePublicKey(serverKeys.getPublic());
        String serverPrivateKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(unsignedFixed(((ECPrivateKey) serverKeys.getPrivate()).getS(), 32));
        String subscriptionPublicKey = encodePublicKey(subscriptionKeys.getPublic());

        PushService pushService = new PushService(serverPublicKey, serverPrivateKey, "mailto:test@example.invalid");
        Notification notification = Notification.builder()
                .endpoint("https://fcm.googleapis.com/fcm/send/test")
                .userPublicKey(subscriptionPublicKey)
                .userAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]))
                .payload("test")
                .ttl(60)
                .build();
        HttpPost request = pushService.preparePost(notification, Encoding.AESGCM);

        String p256ecdsa = Arrays.stream(request.getFirstHeader("Crypto-Key").getValue().split(";"))
                .map(String::trim)
                .filter(parameter -> parameter.startsWith("p256ecdsa="))
                .findFirst()
                .orElseThrow();
        assertEquals("p256ecdsa=" + serverPublicKey, p256ecdsa);
        assertFalse(p256ecdsa.endsWith("="));
    }

    private static String encodePublicKey(java.security.PublicKey publicKey) {
        ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
        byte[] raw = new byte[65];
        raw[0] = 4;
        System.arraycopy(unsignedFixed(ecPublicKey.getW().getAffineX(), 32), 0, raw, 1, 32);
        System.arraycopy(unsignedFixed(ecPublicKey.getW().getAffineY(), 32), 0, raw, 33, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] unsignedFixed(BigInteger value, int length) {
        byte[] encoded = value.toByteArray();
        byte[] fixed = new byte[length];
        int copyLength = Math.min(encoded.length, length);
        System.arraycopy(encoded, encoded.length - copyLength, fixed, length - copyLength, copyLength);
        return fixed;
    }
}
