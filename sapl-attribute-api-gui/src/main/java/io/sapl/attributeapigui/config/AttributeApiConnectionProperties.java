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
package io.sapl.attributeapigui.config;

import io.sapl.attributeapigui.connection.ConnectionMode;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api-gui")
public class AttributeApiConnectionProperties implements InitializingBean {

    private static final String ERROR_BASE_URL_NOT_SET = "The base url is not set for connection '%s' (index %d). Please set io.sapl.attribute-api-gui.connections[%d].base-url";
    private static final String ERROR_MALFORMED_URL    = "The given url is not a valid url.";
    private static final String ERROR_NO_CONNECTIONS   = "At least one connection must be defined within this block or remove it to start without configure connections.";

    private List<ConnectionEntry> connections;

    @Override
    public void afterPropertiesSet() {
        // Case: No connection block is given. That's a valid configuration and can be done later
        if (connections == null) {
            connections = List.of();
            return;
        }

        // Case: The connection block is given but contains no valid connection. That's an error.
        if (connections.isEmpty()) {
            throw new IllegalStateException(ERROR_NO_CONNECTIONS);
        }

        for (int i = 0; i < connections.size(); i++) {
            validate(connections.get(i), i);
        }
    }

    private void validate(ConnectionEntry entry, int index) {
        if (entry.getBaseUrl() == null || entry.getBaseUrl().isBlank()) {
            throw new IllegalStateException(ERROR_BASE_URL_NOT_SET.formatted(entry.getName(), index, index));
        }

        try {
            URI uri = URI.create(entry.getBaseUrl());

            if (!isValidBaseUrl(uri)) {
                throw new IllegalStateException(ERROR_MALFORMED_URL + entry.getBaseUrl());
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ERROR_MALFORMED_URL + entry.getBaseUrl(), e);
        }
    }

    private boolean isValidBaseUrl(URI uri) {
        return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }

    @Data
    public static class ConnectionEntry {
        private String         name   = "Default";
        private String         baseUrl;
        private ConnectionMode method = ConnectionMode.NONE;
        private String         username;
        private String         password;
        private String         apiKey;
    }
}
