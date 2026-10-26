package com.openlinklearn.content.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentUrlValidatorTest {

    private final ContentUrlValidator validator = new ContentUrlValidator();

    @Test
    void normalizesHostAndDefaultPortWithoutDroppingQueryParameters() {
        ContentUrlValidator.ValidatedUrl url = validator.validateAndNormalize(
                " HTTPS://WWW.YouTube.com:443/watch?v=video-123&t=4#details "
        );

        assertEquals("https://www.youtube.com/watch?v=video-123&t=4", url.normalizedUrl());
        assertEquals(ContentType.YOUTUBE, url.type());
        assertEquals("YouTube", url.sourceName());
    }

    @Test
    void normalizesArticleUrlWithoutDroppingIdentityParameters() {
        ContentUrlValidator.ValidatedUrl url = validator.validateAndNormalize(
                "HTTP://Example.ORG:80/course/../lesson?unit=4&token=keep#section"
        );

        assertEquals("http://example.org/lesson?unit=4&token=keep", url.normalizedUrl());
        assertEquals(ContentType.ARTICLE, url.type());
    }

    @Test
    void suppliesArticleTypeForOtherPublicWebsites() {
        ContentUrlValidator.ValidatedUrl url = validator.validateAndNormalize("https://example.org/learn");

        assertEquals(ContentType.ARTICLE, url.type());
        assertEquals("example.org", url.sourceName());
    }

    @Test
    void allowsPublicIpv6Hosts() {
        ContentUrlValidator.ValidatedUrl url = validator.validateAndNormalize("https://[2606:4700:4700::1111]/learn");

        assertEquals("https://[2606:4700:4700::1111]/learn", url.normalizedUrl());
    }

    @Test
    void rejectsUnsupportedAndLocalUrls() {
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("file:///etc/passwd"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("https://"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://example.org:99999/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://user:pass@example.org/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://localhost/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://printer.local/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://api.internal/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://127.0.0.1/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://169.254.1.2/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://100.64.0.1/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://192.88.99.1/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://0177.0.0.1/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://0x7f000001/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://192.168.1.5/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://[::1]/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://[::ffff:127.0.0.1]/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://[fd00::1]/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://[fe80::1%25eth0]/item"));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateAndNormalize("http://[2001:db8::1]/item"));
        }

        @Test
        void acceptsSupportedYoutubeVariantsAndPublicAddresses() {
        assertEquals(ContentType.YOUTUBE, validator.validateAndNormalize("https://youtu.be/video-1").type());
        assertEquals(ContentType.YOUTUBE, validator.validateAndNormalize("https://m.youtube.com/watch?v=video-1").type());
        assertEquals("https://8.8.8.8/lesson", validator.validateAndNormalize("https://8.8.8.8/lesson").normalizedUrl());
        assertEquals("https://[2606:4700:4700::1111]/learn",
            validator.validateAndNormalize("https://[2606:4700:4700::1111]/learn").normalizedUrl());
        }

        @Test
        void validatesRelativeAndAbsoluteRedirectTargetsWithoutFetching() {
        ContentUrlValidator.ValidatedUrl relative = validator.validateRedirectTarget(
            "https://example.org/course/start",
            "../lesson?unit=2"
        );
        assertEquals("https://example.org/lesson?unit=2", relative.normalizedUrl());

        assertThrows(InvalidContentUrlException.class, () -> validator.validateRedirectTarget(
            "https://example.org/course/start",
            "//127.0.0.1/admin"
        ));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateRedirectTarget(
            "https://example.org/course/start",
            "http://[::ffff:10.0.0.1]/admin"
        ));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateRedirectTarget(
            "https://example.org/course/start",
            "javascript:alert(1)"
        ));
        assertThrows(InvalidContentUrlException.class, () -> validator.validateRedirectTarget(
            "https://example.org/course/start",
            "http://[::1"
        ));
    }
}