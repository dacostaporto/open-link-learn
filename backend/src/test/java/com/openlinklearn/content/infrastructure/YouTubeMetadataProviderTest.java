package com.openlinklearn.content.infrastructure;

import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YouTubeMetadataProviderTest {

    private final ContentUrlValidator validator = new ContentUrlValidator();

    @Test
    void mapsOembedTitleAuthorAndThumbnail() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<?> response = jsonResponse(200, """
                {"title":"Video title","author_name":"Channel name","thumbnail_url":"https://i.ytimg.com/vi/video-1/hqdefault.jpg"}
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(client, new ObjectMapper());

        Optional<ContentMetadata> metadata = provider.fetch(
                validator.validateAndNormalize("https://youtu.be/video-1")
        );

        assertEquals(Optional.of(new ContentMetadata(
                "Video title", "Channel name", "https://i.ytimg.com/vi/video-1/hqdefault.jpg"
        )), metadata);
    }

    @Test
    void appliesRequestTimeoutAndUsesOembedEndpoint() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> response = jsonResponse(404, "{}");
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(client, new ObjectMapper());
        HttpRequest[] captured = new HttpRequest[1];
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    captured[0] = invocation.getArgument(0);
                    return response;
                });

        Optional<ContentMetadata> metadata = provider.fetch(
                validator.validateAndNormalize("https://www.youtube.com/watch?v=video-1")
        );

        assertTrue(metadata.isEmpty());
        assertTrue(captured[0].uri().toString().startsWith("https://www.youtube.com/oembed?url="));
        assertTrue(captured[0].uri().toString().contains("format=json"));
        assertEquals(Duration.ofSeconds(3), captured[0].timeout().orElseThrow());
    }

    @Test
    void returnsNoMetadataForRejectedOrInvalidResponses() throws Exception {
        HttpClient rejectedClient = mock(HttpClient.class);
        HttpResponse<byte[]> rejectedResponse = jsonResponse(401, "{}");
        when(rejectedClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rejectedResponse);
        assertTrue(new YouTubeMetadataProvider(rejectedClient, new ObjectMapper())
                .fetch(validator.validateAndNormalize("https://youtu.be/video-1")).isEmpty());

        HttpClient invalidThumbnailClient = mock(HttpClient.class);
        HttpResponse<byte[]> invalidThumbnailResponse = jsonResponse(200, """
                {"title":"Video title","author_name":"Channel","thumbnail_url":"http://localhost/image.jpg"}
                """);
        when(invalidThumbnailClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(invalidThumbnailResponse);
        Optional<ContentMetadata> metadata = new YouTubeMetadataProvider(invalidThumbnailClient, new ObjectMapper())
                .fetch(validator.validateAndNormalize("https://youtu.be/video-1"));

        assertEquals(Optional.of(new ContentMetadata("Video title", "Channel", null)), metadata);
    }

    @Test
    void rejectsOversizedResponseAndPropagatesTimeoutForFallback() throws Exception {
        HttpClient oversizedClient = mock(HttpClient.class);
        HttpResponse<byte[]> oversizedResponse = jsonResponse(200, "x".repeat(64 * 1024 + 1));
        when(oversizedClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(oversizedResponse);
        YouTubeMetadataProvider oversizedProvider = new YouTubeMetadataProvider(oversizedClient, new ObjectMapper());
        assertThrows(java.io.IOException.class, () -> oversizedProvider.fetch(
                validator.validateAndNormalize("https://youtu.be/video-1")
        ));

        HttpClient timeoutClient = mock(HttpClient.class);
        when(timeoutClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timed out"));
        YouTubeMetadataProvider timeoutProvider = new YouTubeMetadataProvider(timeoutClient, new ObjectMapper());
        assertThrows(HttpTimeoutException.class, () -> timeoutProvider.fetch(
                validator.validateAndNormalize("https://youtu.be/video-1")
        ));
    }

    @Test
    void extractsDurationFromYoutubePageMetadata() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> oembedResponse = jsonResponse(200, """
                {"title":"Video title","author_name":"Channel name","thumbnail_url":"https://i.ytimg.com/vi/video-1/hqdefault.jpg"}
                """);
        HttpResponse<byte[]> pageResponse = htmlResponse(200, """
                <html><head><meta itemprop="duration" content="PT1H2M3S"></head></html>
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(oembedResponse, pageResponse);
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(client, new ObjectMapper());

        Optional<ContentMetadata> metadata = provider.fetch(
                validator.validateAndNormalize("https://youtu.be/video-1")
        );

        assertEquals(3723, metadata.orElseThrow().durationSeconds());
    }

    @Test
    void extractsDurationFromInitialPlayerResponse() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> oembedResponse = jsonResponse(200, """
                {"title":"Video title","author_name":"Channel name","thumbnail_url":"https://i.ytimg.com/vi/video-1/hqdefault.jpg"}
                """);
        HttpResponse<byte[]> pageResponse = htmlResponse(200, """
                <script>var ytInitialPlayerResponse = {"videoDetails":{"lengthSeconds":"95"}};</script>
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(oembedResponse, pageResponse);
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(client, new ObjectMapper());

        Optional<ContentMetadata> metadata = provider.fetch(
                validator.validateAndNormalize("https://youtu.be/video-1")
        );

        assertEquals(95, metadata.orElseThrow().durationSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsDurationFromLargeWatchPageThroughTheBoundedSubscriber() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> oembedResponse = jsonResponse(200, """
                {"title":"Video title","author_name":"Channel name","thumbnail_url":"https://i.ytimg.com/vi/video-1/hqdefault.jpg"}
                """);
        String page = "<html><head>" + "<!--" + "x".repeat(900 * 1024) + "-->"
                + "<meta itemprop=\"duration\" content=\"PT9M4S\"></head></html>";
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(oembedResponse)
                .thenAnswer(invocation -> {
                    HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    HttpResponse.ResponseInfo info = mock(HttpResponse.ResponseInfo.class);
                    HttpResponse.BodySubscriber<byte[]> subscriber = handler.apply(info);
                    subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
                        @Override public void request(long n) { }
                        @Override public void cancel() { }
                    });
                    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(page.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                    subscriber.onComplete();
                    byte[] body = subscriber.getBody().toCompletableFuture().join();
                    HttpResponse<byte[]> response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn(200);
                    when(response.body()).thenReturn(body);
                    return response;
                });
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(client, new ObjectMapper());

        Optional<ContentMetadata> metadata = provider.fetch(
                validator.validateAndNormalize("https://www.youtube.com/watch?v=wi3mTANVdgI")
        );

        assertEquals(544, metadata.orElseThrow().durationSeconds());
    }

    @Test
    void supportsOnlyYoutubeContent() {
        YouTubeMetadataProvider provider = new YouTubeMetadataProvider(mock(HttpClient.class), new ObjectMapper());

        assertTrue(provider.supports(ContentType.YOUTUBE));
        assertFalse(provider.supports(ContentType.ARTICLE));
    }

    @SuppressWarnings("unchecked")
        private HttpResponse<byte[]> jsonResponse(int status, String json) {
                HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json; charset=utf-8")),
                (name, value) -> true
        ));
        when(response.body()).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return response;
    }

        @SuppressWarnings("unchecked")
        private HttpResponse<byte[]> htmlResponse(int status, String html) {
                HttpResponse<byte[]> response = mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(status);
                when(response.headers()).thenReturn(HttpHeaders.of(
                                Map.of("Content-Type", List.of("text/html; charset=utf-8")),
                                (name, value) -> true
                ));
                when(response.body()).thenReturn(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return response;
        }
}