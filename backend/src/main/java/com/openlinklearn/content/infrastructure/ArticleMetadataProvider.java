package com.openlinklearn.content.infrastructure;

import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentMetadataProvider;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class ArticleMetadataProvider implements ContentMetadataProvider {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final ContentUrlValidator urlValidator;
    private final PublicDestinationResolver destinationResolver;
    private final ArticleHttpTransport httpTransport;

    public ArticleMetadataProvider(
            ContentUrlValidator urlValidator,
            PublicDestinationResolver destinationResolver,
            ArticleHttpTransport httpTransport
    ) {
        this.urlValidator = urlValidator;
        this.destinationResolver = destinationResolver;
        this.httpTransport = httpTransport;
    }

    @Override
    public boolean supports(ContentType type) {
        return type == ContentType.ARTICLE;
    }

    @Override
    public Optional<ContentMetadata> fetch(ContentUrlValidator.ValidatedUrl url) throws Exception {
        URI currentUri = URI.create(urlValidator.validateAndNormalize(url.normalizedUrl()).normalizedUrl());
        Set<String> visited = new HashSet<>();

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            ContentUrlValidator.ValidatedUrl current = urlValidator.validateAndNormalize(currentUri.toString());
            if (!visited.add(current.normalizedUrl())) {
                return Optional.empty();
            }
            InetAddress[] vettedAddresses = destinationResolver.resolvePublicAddresses(current.host());
            ArticleHttpTransport.ArticleHttpResponse response = httpTransport.get(
                    URI.create(current.normalizedUrl()),
                    vettedAddresses
            );

            if (isRedirect(response.statusCode())) {
                if (redirectCount == MAX_REDIRECTS || response.location() == null) {
                    return Optional.empty();
                }
                ContentUrlValidator.ValidatedUrl redirect = urlValidator.validateRedirectTarget(
                        current.normalizedUrl(),
                        response.location()
                );
                currentUri = URI.create(redirect.normalizedUrl());
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !isHtml(response.contentType()) || response.body() == null
                    || response.body().length > MAX_RESPONSE_BYTES) {
                return Optional.empty();
            }
            return extractMetadata(response.body(), current.host());
        }
        return Optional.empty();
    }

    private Optional<ContentMetadata> extractMetadata(byte[] body, String host) throws IOException {
        Document document = Jsoup.parse(new ByteArrayInputStream(body), null, "https://" + host);
        String title = metaContent(document, "meta[property=og:title]");
        if (title == null) {
            title = metaContent(document, "meta[name=twitter:title]");
        }
        if (title == null && document.title() != null && !document.title().isBlank()) {
            title = document.title().trim();
        }
        if (title == null) {
            title = metaContent(document, "meta[name=title]");
        }
        String sourceName = metaContent(document, "meta[property=og:site_name]");
        if (title == null && sourceName == null) {
            return Optional.empty();
        }
        return Optional.of(new ContentMetadata(title, sourceName == null ? host : sourceName, null));
    }

    private String metaContent(Document document, String selector) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String content = element.attr("content").trim();
        return content.isBlank() ? null : content;
    }

    private boolean isHtml(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("text/html") || mediaType.equals("application/xhtml+xml");
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }
}