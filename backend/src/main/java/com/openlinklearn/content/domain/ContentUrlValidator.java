package com.openlinklearn.content.domain;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class ContentUrlValidator {

    public ValidatedUrl validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidContentUrlException("A content URL is required.");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim()).normalize();
        } catch (URISyntaxException exception) {
            throw new InvalidContentUrlException("The content URL is malformed.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidContentUrlException("Only HTTP and HTTPS URLs are supported.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidContentUrlException("URLs containing user information are not supported.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidContentUrlException("The content URL must include a valid hostname.");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        String checkedHost = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        rejectUnsafeHost(checkedHost);

        int port = uri.getPort();
        if (port < -1 || port == 0 || port > 65535) {
            throw new InvalidContentUrlException("The content URL contains an invalid port.");
        }
        if ((scheme.equalsIgnoreCase("http") && port == 80)
                || (scheme.equalsIgnoreCase("https") && port == 443)) {
            port = -1;
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = checkedHost.contains(":") ? "[" + checkedHost + "]" : checkedHost;
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String normalizedUrl = normalizedScheme + "://" + normalizedHost
                + (port == -1 ? "" : ":" + port)
                + path
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

        ContentType type = isYouTubeHost(checkedHost) ? ContentType.YOUTUBE : ContentType.ARTICLE;
        String sourceName = type == ContentType.YOUTUBE ? "YouTube" : checkedHost;
        return new ValidatedUrl(rawUrl.trim(), normalizedUrl, checkedHost, type, sourceName);
    }

    public ValidatedUrl validateRedirectTarget(String currentUrl, String location) {
        if (location == null || location.isBlank()) {
            throw new InvalidContentUrlException("The redirect target is empty.");
        }

        try {
            URI currentUri = new URI(validateAndNormalize(currentUrl).normalizedUrl());
            URI redirectUri = new URI(location.trim());
            return validateAndNormalize(currentUri.resolve(redirectUri).toString());
        } catch (URISyntaxException exception) {
            throw new InvalidContentUrlException("The redirect target is malformed.");
        }
    }

    private void rejectUnsafeHost(String host) {
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            throw new InvalidContentUrlException("Local and internal hosts are not supported.");
        }

        if (host.matches("[0-9.]+")) {
            rejectUnsafeIpv4(host);
            return;
        }

        if (host.contains(":")) {
            if (host.contains("%")) {
                throw new InvalidContentUrlException("Scoped IPv6 addresses are not supported.");
            }
            rejectUnsafeIpv6(host);
            return;
        }

        if (isLegacyIpv4Literal(host)) {
            throw new InvalidContentUrlException("Non-standard IP address formats are not supported.");
        }
    }

    private boolean isLegacyIpv4Literal(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length > 4) {
            return false;
        }
        for (String label : labels) {
            if (!label.matches("(?i)(?:0x[0-9a-f]+|[0-9]+)")) {
                return false;
            }
        }
        return true;
    }

    private void rejectUnsafeIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            throw new InvalidContentUrlException("The content URL contains an invalid IP address.");
        }

        int[] octets = new int[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || (parts[index].length() > 1 && parts[index].startsWith("0"))) {
                    throw new NumberFormatException();
                }
                octets[index] = Integer.parseInt(parts[index]);
                if (octets[index] > 255) {
                    throw new NumberFormatException();
                }
            }
        } catch (NumberFormatException exception) {
            throw new InvalidContentUrlException("The content URL contains an invalid IP address.");
        }

        int first = octets[0];
        int second = octets[1];
        int third = octets[2];
        if (isUnsafeIpv4(first, second, third)) {
            throw new InvalidContentUrlException("Private, reserved, and local IP addresses are not supported.");
        }
    }

    private void rejectUnsafeIpv6(String host) {
        try {
            byte[] address = InetAddress.getByName(host).getAddress();
            if (address.length == 4) {
                if (isUnsafeIpv4(address[0] & 0xff, address[1] & 0xff, address[2] & 0xff)) {
                    throw new InvalidContentUrlException("Private, reserved, and local IP addresses are not supported.");
                }
                return;
            }
            if (address.length != 16) {
                throw new InvalidContentUrlException("The content URL contains an invalid IP address.");
            }

            boolean uniqueLocal = (address[0] & 0xfe) == 0xfc;
            boolean mappedIpv4 = allZero(address, 0, 10) && address[10] == (byte) 0xff
                    && address[11] == (byte) 0xff;
            boolean compatibleIpv4 = allZero(address, 0, 12);
            boolean globalUnicast = (address[0] & 0xe0) == 0x20;
            boolean documentation = (address[0] & 0xff) == 0x20
                    && (address[1] & 0xff) == 0x01
                    && (address[2] & 0xff) == 0x0d
                    && (address[3] & 0xff) == 0xb8;
            boolean sixToFour = (address[0] & 0xff) == 0x20 && (address[1] & 0xff) == 0x02;
            boolean teredo = (address[0] & 0xff) == 0x20 && (address[1] & 0xff) == 0x01
                    && address[2] == 0 && address[3] == 0;
            if (!globalUnicast || uniqueLocal || documentation || sixToFour || teredo
                    || InetAddress.getByAddress(address).isAnyLocalAddress()
                    || InetAddress.getByAddress(address).isLoopbackAddress()
                    || InetAddress.getByAddress(address).isLinkLocalAddress()
                    || InetAddress.getByAddress(address).isSiteLocalAddress()
                    || InetAddress.getByAddress(address).isMulticastAddress()
                    || ((mappedIpv4 || compatibleIpv4) && isUnsafeIpv4Tail(address))) {
                throw new InvalidContentUrlException("Private, reserved, and local IP addresses are not supported.");
            }
        } catch (InvalidContentUrlException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidContentUrlException("The content URL contains an invalid IP address.");
        }
    }

    private boolean allZero(byte[] address, int start, int end) {
        for (int index = start; index < end; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isUnsafeIpv4Tail(byte[] address) {
        return isUnsafeIpv4(address[12] & 0xff, address[13] & 0xff, address[14] & 0xff);
    }

    private boolean isUnsafeIpv4(int first, int second, int third) {
        return first == 0 || first == 10 || first == 127 || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && (second == 0 || second == 168))
            || (first == 192 && second == 88 && third == 99)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private boolean isYouTubeHost(String host) {
        return host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be");
    }

    public record ValidatedUrl(
            String url,
            String normalizedUrl,
            String host,
            ContentType type,
            String sourceName
    ) {
    }
}