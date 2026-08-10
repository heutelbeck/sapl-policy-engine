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
import io.sapl.attributeapigui.connection.ConnectionSettingsHolder;
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
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@VaadinSessionScope
public class AttributeApiClient {
    private static final String ERROR_NO_ATTRIBUTE_TO_DELTE = "There was no such attribute to delete from the repository.";
    private static final String API_GLOBAL_ATTRIBUTE        = "/api/attributes/{name}";
    private static final String API_ATTRIBUTE_WITH_ENTITY   = "/api/attributes/{entity}/{name}";

    private final ConnectionSettingsHolder settingsHolder;
    private final RestClient               client = RestClient.create();

    public enum DeleteOutput {
        DELETED,
        NOT_FOUND
    }

    public AttributeApiClient(ConnectionSettingsHolder settingsHolder) {
        this.settingsHolder = settingsHolder;
    }

    public Optional<Object> getAttribute(String entity, String name, List<String> arguments) {
        var settings = settingsHolder.get();
        checkConfiguration(settings);

        try {
            var builder = (entity == null || entity.isBlank())
                    ? UriComponentsBuilder.fromUriString(settings.baseUrl() + API_GLOBAL_ATTRIBUTE)
                    : UriComponentsBuilder.fromUriString(settings.baseUrl() + API_ATTRIBUTE_WITH_ENTITY);

            if (arguments != null && !arguments.isEmpty()) {
                builder.queryParam("arg", arguments.toArray());
            }

            var uri = (entity == null || entity.isBlank()) ? builder.buildAndExpand(name).toUri()
                    : builder.buildAndExpand(entity, name).toUri();

            var value = client.get().uri(uri).headers(h -> addAuthorization(h, settings)).retrieve().body(Object.class);

            return Optional.ofNullable(value);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public DeleteOutput deleteAttribute(String entity, String name, List<String> arguments) {
        var settings = settingsHolder.get();
        checkConfiguration(settings);
        try {
            var builder = (entity == null || entity.isBlank())
                    ? UriComponentsBuilder.fromUriString(settings.baseUrl() + API_GLOBAL_ATTRIBUTE)
                    : UriComponentsBuilder.fromUriString(settings.baseUrl() + API_ATTRIBUTE_WITH_ENTITY);

            if (arguments != null && !arguments.isEmpty()) {
                builder.queryParam("arg", arguments.toArray());
            }

            var uri = (entity == null || entity.isBlank()) ? builder.buildAndExpand(name).toUri()
                    : builder.buildAndExpand(entity, name).toUri();

            client.delete().uri(uri).headers(h -> addAuthorization(h, settings)).retrieve().toBodilessEntity();
            return DeleteOutput.DELETED;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug(ERROR_NO_ATTRIBUTE_TO_DELTE);
            return DeleteOutput.NOT_FOUND;
        }
    }

    public void publishAttribute(String entity, String name, JsonNode value, Long ttl, List<JsonNode> arguments) {
        var settings = settingsHolder.get();
        checkConfiguration(settings);

        var body = Map.of("value", value, "ttl", Objects.requireNonNullElse(ttl, 0L), "arguments",
                arguments == null ? List.of() : arguments);

        var request = (entity == null || entity.isBlank())
                ? client.put().uri(settings.baseUrl() + API_GLOBAL_ATTRIBUTE, name)
                : client.put().uri(settings.baseUrl() + API_ATTRIBUTE_WITH_ENTITY, entity, name);

        request.headers(h -> addAuthorization(h, settings)).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
    }

    public List<Map<String, Object>> getAllAttributes(int limit, int offset) {
        var settings = settingsHolder.get();
        checkConfiguration(settings);

        return client.get().uri(settings.baseUrl() + "/api/attributes?limit={limit}&offset={offset}", limit, offset)
                .headers(h -> addAuthorization(h, settings)).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    public Long getAttributeCount() {
        var settings = settingsHolder.get();
        checkConfiguration(settings);

        return client.get().uri(settings.baseUrl() + "/api/attributes?count=true")
                .headers(h -> addAuthorization(h, settings)).retrieve().body(Long.class);
    }

    private void checkConfiguration(ConnectionSettings settings) {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Not connected - configure a connection on the Settings view first.");
        }
    }

    private void addAuthorization(HttpHeaders headers, ConnectionSettings settings) {
        switch (settings.mode()) {
        case NONE -> {
            // keine Authentifizierung
        }

        case BASIC -> headers.setBasicAuth(settings.username(), settings.password());

        case API -> headers.setBearerAuth(settings.apiKey());

        default -> throw new IllegalStateException("Unsupported authentication mode: " + settings.mode());
        }
    }
}
