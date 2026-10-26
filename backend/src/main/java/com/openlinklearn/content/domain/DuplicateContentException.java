package com.openlinklearn.content.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateContentException extends ResponseStatusException {

    public DuplicateContentException() {
        super(HttpStatus.CONFLICT, "This URL is already in your backlog.");
    }
}