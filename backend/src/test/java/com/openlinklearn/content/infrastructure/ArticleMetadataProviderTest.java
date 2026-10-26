package com.openlinklearn.content.infrastructure;

import com.openlinklearn.content.application.ContentMetadataResolver;
import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import com.openlinklearn.content.domain.InvalidContentUrlException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleMetadataProviderTest {

    private final ContentUrlValidator urlValidator = new ContentUrlValidator();
    private final PublicDestinationResolver destinationResolver = mock(PublicDestinationResolver.class);
    private final ArticleHttpTransport httpTransport = mock(ArticleHttpTransport.class);
    private final ArticleMetadataProvider provider = new ArticleMetadataProvider(
            urlValidator,
            destinationResolver,
            httpTransport
    );
    private final InetAddress[] publicAddresses = {address(93, 184, 216, 34)};

    ArticleMetadataProviderTest() throws UnknownHostException {
        when(destinationResolver.resolvePublicAddresses(anyString())).thenReturn(publicAddresses);
    }

    @Test
    void extractsOpenGraphTitleAndSiteName() throws Exception {
        when(httpTransport.get(any(URI.class), any(InetAddress[].class))).thenReturn(html(200, """
                <html><head>
                  <meta property="og:title" content="A useful &amp; clear lesson">
                  <meta property="og:site_name" content="Learning Journal">
                </head></html>
                """));

        Optional<ContentMetadata> metadata = provider.fetch(
                urlValidator.validateAndNormalize("https://example.org/article")
        );

        assertEquals(Optional.of(new ContentMetadata("A useful & clear lesson", "Learning Journal", null)), metadata);
        verify(destinationResolver).resolvePublicAddresses("example.org");
    }

    @Test
    void followsRelativeRedirectAndValidatesTheNextHop() throws Exception {
        when(httpTransport.get(any(URI.class), any(InetAddress[].class)))
                .thenReturn(new ArticleHttpTransport.ArticleHttpResponse(302, null, "/final", new byte[0]))
                .thenReturn(html(200, "<html><head><title>Final title</title></head></html>"));

        Optional<ContentMetadata> metadata = provider.fetch(
                urlValidator.validateAndNormalize("https://example.org/start")
        );

        assertEquals("Final title", metadata.orElseThrow().title());
        verify(destinationResolver, times(2)).resolvePublicAddresses("example.org");
        verify(httpTransport, times(2)).get(any(URI.class), any(InetAddress[].class));
    }

    @Test
    void rejectsUnsafeRedirectBeforeAnotherRequest() throws Exception {
        when(httpTransport.get(any(URI.class), any(InetAddress[].class)))
                .thenReturn(new ArticleHttpTransport.ArticleHttpResponse(302, null, "http://127.0.0.1/admin", new byte[0]));

        assertThrows(InvalidContentUrlException.class, () -> provider.fetch(
                urlValidator.validateAndNormalize("https://example.org/start")
        ));

        verify(httpTransport).get(any(URI.class), any(InetAddress[].class));
        verify(destinationResolver, never()).resolvePublicAddresses("127.0.0.1");
    }

    @Test
    void blocksProhibitedDnsAnswersBeforeMakingRequest() throws Exception {
        when(destinationResolver.resolvePublicAddresses("public-looking.example"))
                .thenThrow(new UnknownHostException("Prohibited address"));

        assertThrows(UnknownHostException.class, () -> provider.fetch(
                urlValidator.validateAndNormalize("https://public-looking.example/article")
        ));

        verify(httpTransport, never()).get(any(URI.class), any(InetAddress[].class));
    }

    @Test
    void fallsBackForOversizedOrWrongContentTypeResponses() throws Exception {
        when(httpTransport.get(any(URI.class), any(InetAddress[].class)))
                .thenReturn(new ArticleHttpTransport.ArticleHttpResponse(
                        200, "text/html", null, new byte[1024 * 1024 + 1]
                ));
        assertTrue(provider.fetch(urlValidator.validateAndNormalize("https://example.org/article")).isEmpty());

        when(httpTransport.get(any(URI.class), any(InetAddress[].class)))
                .thenReturn(new ArticleHttpTransport.ArticleHttpResponse(
                        200, "application/json", null, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ));
        assertTrue(provider.fetch(urlValidator.validateAndNormalize("https://example.org/article")).isEmpty());
    }

    @Test
    void resolverUsesFallbackOnTransportTimeout() throws Exception {
        when(httpTransport.get(any(URI.class), any(InetAddress[].class)))
                .thenThrow(new SocketTimeoutException("Read timed out"));
        ContentMetadataResolver resolver = new ContentMetadataResolver(java.util.List.of(provider));

        ContentMetadataResolver.ResolvedMetadata metadata = resolver.resolve(
                urlValidator.validateAndNormalize("https://example.org/article"),
                null,
                ContentType.ARTICLE
        );

        assertEquals("example.org", metadata.title());
        assertEquals("example.org", metadata.sourceName());
    }

    @Test
    void rejectsLocalAndPrivateAddresses() throws Exception {
        PublicDestinationResolver resolver = new PublicDestinationResolver();

        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{10, 0, 0, 1})));
        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte) 169, (byte) 254, 1, 2})));
        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 1})));
        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 88, 99, 1})));
        assertTrue(resolver.isPublicAddress(address(93, 184, 216, 34)));
        assertFalse(resolver.isPublicAddress(InetAddress.getByAddress(new byte[]{
                (byte) 0xfe, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        })));
    }

    @Test
    void supportsOnlyArticleContent() {
        assertTrue(provider.supports(ContentType.ARTICLE));
        assertFalse(provider.supports(ContentType.YOUTUBE));
    }

    private ArticleHttpTransport.ArticleHttpResponse html(int status, String body) {
        return new ArticleHttpTransport.ArticleHttpResponse(
                status,
                "text/html; charset=utf-8",
                null,
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private static InetAddress address(int first, int second, int third, int fourth) {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) first, (byte) second, (byte) third, (byte) fourth});
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}