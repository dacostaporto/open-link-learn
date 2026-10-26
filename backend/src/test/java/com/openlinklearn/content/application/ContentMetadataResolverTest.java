package com.openlinklearn.content.application;

import com.openlinklearn.content.domain.ContentMetadata;
import com.openlinklearn.content.domain.ContentMetadataProvider;
import com.openlinklearn.content.domain.ContentType;
import com.openlinklearn.content.domain.ContentUrlValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentMetadataResolverTest {

    private final ContentUrlValidator validator = new ContentUrlValidator();

    @Test
    void usesMetadataFromTheMatchingProvider() throws Exception {
        ContentMetadataProvider provider = providerReturning(
                Optional.of(new ContentMetadata("Provider title", "Publisher", "https://img.example/cover.jpg"))
        );
        ContentMetadataResolver resolver = new ContentMetadataResolver(List.of(provider));

        ContentMetadataResolver.ResolvedMetadata result = resolver.resolve(
                validator.validateAndNormalize("https://example.org/article"),
                null,
                ContentType.ARTICLE
        );

        assertEquals(new ContentMetadataResolver.ResolvedMetadata(
                "Provider title", "Publisher", "https://img.example/cover.jpg"), result);
        verify(provider).fetch(any());
    }

    @Test
    void ownerTitleOverridesProviderTitle() throws Exception {
        ContentMetadataProvider provider = providerReturning(
                Optional.of(new ContentMetadata("Provider title", "Publisher", null))
        );
        ContentMetadataResolver resolver = new ContentMetadataResolver(List.of(provider));

        ContentMetadataResolver.ResolvedMetadata result = resolver.resolve(
                validator.validateAndNormalize("https://example.org/article"),
                " Owner title ",
                ContentType.ARTICLE
        );

        assertEquals("Owner title", result.title());
    }

    @Test
    void fallsBackWhenProviderHasNoMetadata() throws Exception {
        ContentMetadataProvider provider = providerReturning(Optional.empty());
        ContentMetadataResolver resolver = new ContentMetadataResolver(List.of(provider));

        ContentMetadataResolver.ResolvedMetadata result = resolver.resolve(
                validator.validateAndNormalize("https://example.org/article"),
                null,
                ContentType.ARTICLE
        );

        assertEquals(new ContentMetadataResolver.ResolvedMetadata("example.org", "example.org", null), result);
    }

    @Test
    void fallsBackWhenProviderFailsOrTimesOut() throws Exception {
        ContentMetadataProvider failingProvider = providerReturning(Optional.empty());
        when(failingProvider.fetch(any())).thenThrow(new IllegalStateException("provider unavailable"));
        ContentMetadataProvider timeoutProvider = providerReturning(Optional.empty());
        when(timeoutProvider.fetch(any())).thenThrow(new TimeoutException("provider timed out"));

        assertFallbackFor(failingProvider);
        assertFallbackFor(timeoutProvider);
    }

    @Test
    void callsOnlyTheProviderForTheDetectedContentType() throws Exception {
        ContentMetadataProvider articleProvider = providerReturning(Optional.empty());
        when(articleProvider.supports(ContentType.ARTICLE)).thenReturn(true);
        ContentMetadataProvider youtubeProvider = providerReturning(
                Optional.of(new ContentMetadata("Video", "YouTube", null))
        );
        when(youtubeProvider.supports(ContentType.YOUTUBE)).thenReturn(true);
        ContentMetadataResolver resolver = new ContentMetadataResolver(List.of(articleProvider, youtubeProvider));

        ContentMetadataResolver.ResolvedMetadata result = resolver.resolve(
                validator.validateAndNormalize("https://youtu.be/video-1"),
                null,
                ContentType.YOUTUBE
        );

        assertEquals("Video", result.title());
        verify(articleProvider, never()).fetch(any());
        verify(youtubeProvider).fetch(any());
    }

    private ContentMetadataProvider providerReturning(Optional<ContentMetadata> metadata) throws Exception {
        ContentMetadataProvider provider = mock(ContentMetadataProvider.class);
        when(provider.supports(ContentType.ARTICLE)).thenReturn(true);
        when(provider.fetch(any())).thenReturn(metadata);
        return provider;
    }

    private void assertFallbackFor(ContentMetadataProvider provider) {
        ContentMetadataResolver resolver = new ContentMetadataResolver(List.of(provider));
        ContentMetadataResolver.ResolvedMetadata result = resolver.resolve(
                validator.validateAndNormalize("https://example.org/article"),
                null,
                ContentType.ARTICLE
        );

        assertEquals(new ContentMetadataResolver.ResolvedMetadata("example.org", "example.org", null), result);
    }
}