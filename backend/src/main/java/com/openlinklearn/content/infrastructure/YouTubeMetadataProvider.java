package com.openlinklearn.content.infrastructure;

import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentMetadataProvider;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

@Component
public class YouTubeMetadataProvider implements ContentMetadataProvider {

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    // Watch pages are ~1.5 MB and the duration markers sit deep in the document.
    private static final int MAX_PAGE_BYTES = 3 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final URI OEMBED_ENDPOINT = URI.create("https://www.youtube.com/oembed");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YouTubeMetadataProvider(HttpClient youtubeMetadataHttpClient, ObjectMapper objectMapper) {
        this.httpClient = youtubeMetadataHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ContentType type) {
        return type == ContentType.YOUTUBE;
    }

    @Override
    public Optional<ContentMetadata> fetch(ContentUrlValidator.ValidatedUrl url) throws Exception {
        String encodedUrl = URLEncoder.encode(url.normalizedUrl(), StandardCharsets.UTF_8);
        URI requestUri = URI.create(OEMBED_ENDPOINT + "?url=" + encodedUrl + "&format=json");
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, responseInfo -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES));
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !isJson(response) || response.body() == null) {
            return Optional.empty();
        }

        JsonNode json = objectMapper.readTree(response.body());
        String title = textField(json, "title");
        String author = textField(json, "author_name");
        String thumbnailUrl = safeThumbnailUrl(textField(json, "thumbnail_url"));
        Integer durationSeconds = fetchDurationSeconds(url.normalizedUrl());
        if (title == null && author == null && thumbnailUrl == null && durationSeconds == null) {
            return Optional.empty();
        }
        return Optional.of(new ContentMetadata(title, author, thumbnailUrl, durationSeconds));
    }

    private Integer fetchDurationSeconds(String videoUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(videoUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/html")
                    .header("User-Agent", "OpenLinkLearnMetadata/1.0")
                    .header("Accept-Language", "en")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, responseInfo -> new BoundedBodySubscriber(MAX_PAGE_BYTES));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
                return null;
            }

            Document document = Jsoup.parse(new String(response.body(), StandardCharsets.UTF_8), videoUrl);
            Element duration = document.selectFirst("meta[itemprop=duration], meta[property=video:duration]");
            String value = duration == null ? null : duration.attr("content");
            Integer metadataDuration = parseDurationSeconds(value);
            return metadataDuration != null ? metadataDuration : parsePlayerResponseDuration(document);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parsePlayerResponseDuration(Document document) {
        for (Element script : document.select("script")) {
            String scriptText = script.data();
            int marker = scriptText.indexOf("ytInitialPlayerResponse");
            if (marker < 0) {
                continue;
            }
            int jsonStart = scriptText.indexOf('{', marker);
            if (jsonStart < 0) {
                continue;
            }
            try (JsonParser parser = objectMapper.getFactory().createParser(scriptText.substring(jsonStart))) {
                JsonNode response = objectMapper.readTree(parser);
                String lengthSeconds = response.path("videoDetails").path("lengthSeconds").asText(null);
                if (lengthSeconds != null) {
                    try {
                        long seconds = Long.parseLong(lengthSeconds);
                        if (seconds > 0 && seconds <= Integer.MAX_VALUE) {
                            return (int) seconds;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Integer parseDurationSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Duration.parse(value.trim()).getSeconds();
            return seconds > 0 && seconds <= Integer.MAX_VALUE ? (int) seconds : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isJson(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase().startsWith("application/json"))
                .orElse(false);
    }

    private String textField(JsonNode json, String fieldName) {
        JsonNode value = json.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue().trim();
    }

    private String safeThumbnailUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getRawUserInfo() == null
                    && host != null
                    && (host.equalsIgnoreCase("ytimg.com") || host.toLowerCase().endsWith(".ytimg.com"))) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int chunkSize = buffer.remaining();
                if (body.size() + chunkSize > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException(
                            "The YouTube metadata response exceeded the size limit."
                    ));
                    return;
                }
                byte[] chunk = new byte[chunkSize];
                buffer.get(chunk);
                body.write(chunk, 0, chunk.length);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(body.toByteArray());
        }
    }
}