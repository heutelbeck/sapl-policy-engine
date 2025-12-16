/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages PDP configurations for the language server.
 * Supports multiple configurations identified by configuration IDs.
 */
@Slf4j
public class ConfigurationManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Gson         GSON          = new Gson();

    private final ConcurrentMap<String, LSPConfiguration> configurations = new ConcurrentHashMap<>();

    @Getter
    private String defaultConfigurationId = "";

    /**
     * Processes initialization options from the LSP initialize request.
     *
     * @param initializationOptions the initialization options (may be JsonElement
     * or other)
     */
    public void processInitializationOptions(Object initializationOptions) {
        if (initializationOptions == null) {
            return;
        }

        try {
            JsonNode options;
            if (initializationOptions instanceof JsonElement jsonElement) {
                // Convert Gson JsonElement to Jackson JsonNode
                var jsonString = GSON.toJson(jsonElement);
                options = OBJECT_MAPPER.readTree(jsonString);
            } else {
                options = OBJECT_MAPPER.valueToTree(initializationOptions);
            }

            // Extract configurationId if present
            if (options.has("configurationId")) {
                defaultConfigurationId = options.get("configurationId").asText("");
                log.info("Using configuration ID from init options: {}", defaultConfigurationId);
            }

            // Extract configuration data if present
            if (options.has("configuration")) {
                var configNode = options.get("configuration");
                processConfigurationNode(defaultConfigurationId, configNode);
            }
        } catch (Exception e) {
            log.warn("Failed to process initialization options: {}", e.getMessage());
        }
    }

    /**
     * Updates configuration from workspace/didChangeConfiguration.
     *
     * @param settings the new settings
     */
    public void updateConfiguration(Object settings) {
        if (settings == null) {
            return;
        }

        try {
            JsonNode settingsNode;
            if (settings instanceof JsonElement jsonElement) {
                var jsonString = GSON.toJson(jsonElement);
                settingsNode = OBJECT_MAPPER.readTree(jsonString);
            } else {
                settingsNode = OBJECT_MAPPER.valueToTree(settings);
            }

            // Look for SAPL-specific settings
            if (settingsNode.has("sapl")) {
                var saplSettings = settingsNode.get("sapl");
                if (saplSettings.has("configurationId")) {
                    defaultConfigurationId = saplSettings.get("configurationId").asText("");
                    log.info("Updated configuration ID: {}", defaultConfigurationId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update configuration: {}", e.getMessage());
        }
    }

    /**
     * Registers a configuration for a specific ID.
     *
     * @param configurationId the configuration ID
     * @param configuration the configuration
     */
    public void registerConfiguration(String configurationId, LSPConfiguration configuration) {
        configurations.put(configurationId, configuration);
        log.info("Registered configuration: {}", configurationId);
    }

    /**
     * Gets a configuration by ID.
     *
     * @param configurationId the configuration ID
     * @return the configuration, or empty if not found
     */
    public Optional<LSPConfiguration> getConfiguration(String configurationId) {
        return Optional.ofNullable(configurations.get(configurationId));
    }

    /**
     * Gets the default configuration.
     *
     * @return the default configuration, or empty if not set
     */
    public Optional<LSPConfiguration> getDefaultConfiguration() {
        return getConfiguration(defaultConfigurationId);
    }

    /**
     * Gets a configuration by ID, falling back to the default configuration.
     *
     * @param configurationId the configuration ID (may be null or empty)
     * @return the configuration, or a minimal default
     */
    public LSPConfiguration getConfigurationOrDefault(String configurationId) {
        if (configurationId != null && !configurationId.isEmpty()) {
            var config = configurations.get(configurationId);
            if (config != null) {
                return config;
            }
        }

        // Try default configuration
        var defaultConfig = configurations.get(defaultConfigurationId);
        if (defaultConfig != null) {
            return defaultConfig;
        }

        // Return minimal configuration
        return LSPConfiguration.minimal();
    }

    /**
     * Processes a configuration node and registers the configuration.
     *
     * @param configurationId the configuration ID
     * @param configNode the configuration JSON node
     */
    private void processConfigurationNode(String configurationId, JsonNode configNode) {
        try {
            var config = OBJECT_MAPPER.treeToValue(configNode, LSPConfiguration.class);
            registerConfiguration(configurationId, config);
        } catch (Exception e) {
            log.warn("Failed to parse configuration: {}", e.getMessage());
        }
    }

    /**
     * Creates a configuration from a documentation bundle, variables, and brokers.
     * This is useful for embedding the LSP server with a known PDP configuration.
     *
     * @param configurationId the configuration ID
     * @param documentationBundle the documentation bundle from the PDP
     * @param variables the environment variables
     * @param functionBroker the function broker for expression evaluation
     * @param attributeBroker the attribute broker for expression evaluation
     * @return the created configuration
     */
    public LSPConfiguration createConfiguration(String configurationId, DocumentationBundle documentationBundle,
            Map<String, Value> variables, FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        var config = new LSPConfiguration(configurationId, documentationBundle, variables, functionBroker,
                attributeBroker);
        registerConfiguration(configurationId, config);
        return config;
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
            return "default";
        }

        try {
            var uri   = URI.create(documentUri);
            var query = uri.getQuery();
            if (query == null || query.isEmpty()) {
                return "default";
            }

            for (var param : query.split("&")) {
                var keyValue = param.split("=", 2);
                if (keyValue.length == 2 && "configurationId".equals(keyValue[0])) {
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse configurationId from URI: {}", documentUri);
        }

        return "default";
    }

    /**
     * Gets a configuration for a document URI, extracting the configurationId from
     * the URI's query parameters and falling back to the default configuration.
     *
     * @param documentUri the document URI
     * @return the configuration
     */
    public LSPConfiguration getConfigurationForUri(String documentUri) {
        var configurationId = extractConfigurationIdFromUri(documentUri);
        return getConfigurationOrDefault(configurationId);
    }

}
