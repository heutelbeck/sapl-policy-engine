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
package io.sapl.attributeapigui.client;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.sapl.attributeapigui.connection.ConnectionSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@VaadinSessionScope
public class AttributeApiClient {

    private final ConnectionSettings settings;
    private final RestClient         client = RestClient.create();

    public AttributeApiClient(ConnectionSettings settings) {
        this.settings = settings;
    }

    public Optional<Object> getAttribute(String entity, String name, List<String> arguments) {
        checkConfiguration();

        try {
            var builder = (entity == null || entity.isBlank())
                    ? UriComponentsBuilder.fromUriString(settings.getBaseUrl() + "/api/attributes/{name}")
                    : UriComponentsBuilder.fromUriString(settings.getBaseUrl() + "/api/attributes/{entity}/{name}");

            if (arguments != null && !arguments.isEmpty()) {
                builder.queryParam("arg", arguments.toArray());
            }

            var uri = (entity == null || entity.isBlank()) ? builder.buildAndExpand(name).toUri()
                    : builder.buildAndExpand(entity, name).toUri();

            var value = client.get().uri(uri).headers(this::addAuthorization).retrieve().body(Object.class);

            return Optional.ofNullable(value);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public boolean deleteAttribute(String entity, String name, List<String> arguments) {
        checkConfiguration();
        try {
            var builder = (entity == null || entity.isBlank())
                    ? UriComponentsBuilder.fromUriString(settings.getBaseUrl() + "/api/attributes/{name}")
                    : UriComponentsBuilder.fromUriString(settings.getBaseUrl() + "/api/attributes/{entity}/{name}");

            if (arguments != null && !arguments.isEmpty()) {
                builder.queryParam("arg", arguments.toArray());
            }

            var uri = (entity == null || entity.isBlank()) ? builder.buildAndExpand(name).toUri()
                    : builder.buildAndExpand(entity, name).toUri();

            client.delete().uri(uri).headers(this::addAuthorization).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public void publishAttribute(String entity, String name, JsonNode value, Long ttl, List<JsonNode> arguments) {
        checkConfiguration();

        var body = Map.of("value", value, "ttl", ttl == null ? 0L : ttl, "arguments",
                arguments == null ? List.of() : arguments);

        var request = (entity == null || entity.isBlank())
                ? client.post().uri(settings.getBaseUrl() + "/api/attributes/{name}", name)
                : client.post().uri(settings.getBaseUrl() + "/api/attributes/{entity}/{name}", entity, name);

        request.headers(this::addAuthorization).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .toBodilessEntity();
    }

    public List<Map<String, Object>> getAllAttributes(int limit, int offset) {
        checkConfiguration();

        return client.get().uri(settings.getBaseUrl() + "/api/attributes?limit={limit}&offset={offset}", limit, offset)
                .headers(this::addAuthorization).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public Long getAttributeCount() {
        checkConfiguration();

        return client.get().uri(settings.getBaseUrl() + "/api/attributes/_count").headers(this::addAuthorization)
                .retrieve().body(Long.class);
    }

    private void checkConfiguration() {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Not connected - configure a connection on the Settings view first.");
        }
    }

    private void addAuthorization(HttpHeaders headers) {
        switch (settings.getMode()) {
        case NONE -> {
            // keine Authentifizierung
        }

        case BASIC -> headers.setBasicAuth(settings.getUsername(), settings.getPassword());

        case API -> headers.setBearerAuth(settings.getApiKey());

        default -> throw new IllegalStateException("Unsupported authentication mode: " + settings.getMode());
        }
    }
}
