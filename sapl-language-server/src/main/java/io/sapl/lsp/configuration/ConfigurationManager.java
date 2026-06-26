/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.lsp.configuration;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages LSP configuration resolution for the language server.
 * Delegates to a {@link ConfigurationProvider} for actual configuration lookup.
 *
 * <p>
 * For standalone LSP usage, instantiate with
 * {@link DefaultConfigurationProvider}.
 * For embedded usage, provide a custom {@link ConfigurationProvider}
 * implementation.
 */
@Slf4j
public class ConfigurationManager {

    private static final String DEFAULT_CONFIG_ID = "default";

    private final ConfigurationProvider configurationProvider;

    /**
     * Creates a configuration manager with the default configuration provider.
     * Suitable for standalone LSP server usage.
     */
    public ConfigurationManager() {
        this(new DefaultConfigurationProvider());
    }

    /**
     * Creates a configuration manager with a custom configuration provider.
     * Suitable for embedded usage where configurations come from an external
     * source.
     *
     * @param configurationProvider the provider for configuration resolution
     */
    public ConfigurationManager(ConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
        log.debug("ConfigurationManager initialized with provider: {}",
                configurationProvider.getClass().getSimpleName());
    }

    /**
     * Gets a configuration by ID, delegating to the configuration provider.
     *
     * @param configurationId the configuration ID (may be null or empty)
     * @return the configuration from the provider
     */
    public LSPConfiguration getConfiguration(String configurationId) {
        var effectiveId = (configurationId == null || configurationId.isEmpty()) ? DEFAULT_CONFIG_ID : configurationId;
        return configurationProvider.getConfiguration(effectiveId);
    }

    /**
     * Gets a configuration for a document URI, extracting the configurationId from
     * the URI's query parameters.
     *
     * @param documentUri the document URI (may contain ?configurationId=xxx)
     * @return the configuration
     */
    public LSPConfiguration getConfigurationForUri(String documentUri) {
        var configurationId = extractConfigurationIdFromUri(documentUri);
        return getConfiguration(configurationId);
    }

    /**
     * Extracts the configurationId from a document URI.
     * The URI may contain a query parameter 'configurationId', e.g.,
     * 'file:///policy.sapl?configurationId=production'.
     *
     * @param documentUri the document URI
     * @return the configurationId, or "default" if not present
     */
    public static String extractConfigurationIdFromUri(String documentUri) {
        if (documentUri == null || documentUri.isEmpty()) {
            return DEFAULT_CONFIG_ID;
        }

        try {
            var uri   = URI.create(documentUri);
            var query = uri.getRawQuery();
            if (query == null || query.isEmpty()) {
                return DEFAULT_CONFIG_ID;
            }

            for (var param : query.split("&")) {
                var keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "configurationId".equals(decodeQueryToken(keyValue[0]))) {
                    return decodeQueryToken(keyValue[1]);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse configurationId from URI: {}", documentUri);
        }

        return DEFAULT_CONFIG_ID;
    }

    /**
     * Decodes a single raw URI query token with RFC 3986 semantics. A literal
     * plus is preserved rather than being mapped to a space, which is the
     * form-encoding rule that {@link URLDecoder} applies by default.
     *
     * @param rawToken the raw, percent-encoded query token
     * @return the decoded token
     */
    private static String decodeQueryToken(String rawToken) {
        var plusProtected = rawToken.replace("+", "%2B");
        return URLDecoder.decode(plusProtected, StandardCharsets.UTF_8);
    }

}
