package com.openlinklearn.content.application;

import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentMetadataProvider;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ContentMetadataResolver {

    private final List<ContentMetadataProvider> providers;

    public ContentMetadataResolver(List<ContentMetadataProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ResolvedMetadata resolve(
            ContentUrlValidator.ValidatedUrl url,
            String requestedTitle,
            ContentType selectedType
    ) {
        Optional<ContentMetadata> metadata = findMetadata(url);
        ContentMetadata providerMetadata = metadata.orElse(null);
        String title = hasText(requestedTitle)
                ? requestedTitle.trim()
                : providerMetadata != null && hasText(providerMetadata.title())
                        ? providerMetadata.title().trim()
                        : url.host();
        String sourceName = providerMetadata != null && hasText(providerMetadata.sourceName())
                ? providerMetadata.sourceName().trim()
                : selectedType == ContentType.YOUTUBE ? "YouTube" : url.host();
        String thumbnailUrl = providerMetadata == null ? null : providerMetadata.thumbnailUrl();
        Integer durationSeconds = providerMetadata == null ? null : providerMetadata.durationSeconds();
        return new ResolvedMetadata(title, sourceName, thumbnailUrl, durationSeconds);
    }

    private Optional<ContentMetadata> findMetadata(ContentUrlValidator.ValidatedUrl url) {
        for (ContentMetadataProvider provider : providers) {
            if (!provider.supports(url.type())) {
                continue;
            }
            try {
                Optional<ContentMetadata> result = provider.fetch(url);
                if (result != null && result.isPresent()) {
                    return result;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ResolvedMetadata(String title, String sourceName, String thumbnailUrl, Integer durationSeconds) {

        public ResolvedMetadata(String title, String sourceName, String thumbnailUrl) {
            this(title, sourceName, thumbnailUrl, null);
        }
    }
}