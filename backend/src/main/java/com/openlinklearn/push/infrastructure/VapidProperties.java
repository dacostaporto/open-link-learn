package com.openlinklearn.push.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openlinklearn.push")
public record VapidProperties(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject
) {

	@Override
	public String toString() {
		return "VapidProperties[enabled=" + enabled
				+ ", vapidPublicKey=<redacted>, vapidPrivateKey=<redacted>, vapidSubject=<redacted>]";
	}
}