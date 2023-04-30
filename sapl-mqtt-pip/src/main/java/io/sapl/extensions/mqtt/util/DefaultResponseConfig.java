package io.sapl.extensions.mqtt.util;

import lombok.Data;

/**
 * These data objects store the default response configuration.
 */
@Data
public class DefaultResponseConfig {
	private final long   defaultResponseTimeout;
	private final String defaultResponseType;
}
