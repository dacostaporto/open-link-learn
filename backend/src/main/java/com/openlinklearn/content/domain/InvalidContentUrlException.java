package com.openlinklearn.content.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidContentUrlException extends ResponseStatusException {

    public InvalidContentUrlException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}