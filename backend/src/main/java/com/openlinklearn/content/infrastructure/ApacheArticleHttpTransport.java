package com.openlinklearn.content.infrastructure;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class ApacheArticleHttpTransport implements ArticleHttpTransport {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    @Override
    public ArticleHttpResponse get(URI uri, InetAddress[] vettedAddresses) throws IOException {
        DnsResolver pinnedResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!host.equalsIgnoreCase(uri.getHost())) {
                    throw new UnknownHostException("Unexpected host for pinned article request.");
                }
                return vettedAddresses.clone();
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(1))
                .setResponseTimeout(Timeout.ofSeconds(3))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(pinnedResolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .build())
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build()) {
            HttpGet request = new HttpGet(uri);
            request.setHeader("Accept", "text/html,application/xhtml+xml");
            request.setHeader("User-Agent", "OpenLinkLearnMetadata/1.0");
            return client.execute(request, response -> {
                byte[] body = response.getEntity() == null
                        ? new byte[0]
                        : response.getEntity().getContent().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("The article metadata response exceeded the size limit.");
                }
                Header contentType = response.getFirstHeader("Content-Type");
                Header location = response.getFirstHeader("Location");
                return new ArticleHttpResponse(
                        response.getCode(),
                        contentType == null ? null : contentType.getValue(),
                        location == null ? null : location.getValue(),
                        body
                );
            });
        }
    }
}