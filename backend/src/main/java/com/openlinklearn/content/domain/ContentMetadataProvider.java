package com.openlinklearn.content.domain;

import java.util.Optional;

public interface ContentMetadataProvider {

    boolean supports(ContentType type);

    Optional<ContentMetadata> fetch(ContentUrlValidator.ValidatedUrl url) throws Exception;
}