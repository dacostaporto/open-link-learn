package com.openlinklearn.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

@Schema(name = "ApiProblem", description = "RFC 9457-style API error response.")
public record ApiProblem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance
) {
}