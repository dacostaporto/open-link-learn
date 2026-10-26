package com.openlinklearn.content.infrastructure;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

public interface ArticleHttpTransport {

    ArticleHttpResponse get(URI uri, InetAddress[] vettedAddresses) throws IOException, InterruptedException;

    record ArticleHttpResponse(int statusCode, String contentType, String location, byte[] body) {
    }
}