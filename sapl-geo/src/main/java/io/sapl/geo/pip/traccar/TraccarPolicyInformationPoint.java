/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.geo.pip.traccar;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import io.sapl.geo.library.GeographicFunctionLibrary;
import io.sapl.geo.schemata.GeoJsonSchemata;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TraccarPolicyInformationPoint.NAME, description = TraccarPolicyInformationPoint.DESCRIPTION, pipDocumentation = TraccarPolicyInformationPoint.DOCUMENTATION)
public class TraccarPolicyInformationPoint {

    public static final String TRACCAR_CONFIG = "TRACCAR_CONFIG";
    public static final String NAME           = "traccar";
    public static final String DESCRIPTION    = "PIP for geographical data from traccar.";
    public static final String DOCUMENTATION  = "PIP for geographical data from traccar.";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ReactiveWebClient webClient;

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA)
    public Flux<Val> server(Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return server(variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA)
    public Flux<Val> server(@JsonObject Val traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/server", traccarConfig))
                .distinct();
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA)
    public Flux<Val> devices(Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return devices(variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA)
    public Flux<Val> devices(@JsonObject Val traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/devices", traccarConfig))
                .distinct();
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA)
    public Flux<Val> device(@Text Val deviceEntityId, Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return device(deviceEntityId, variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA)
    private Flux<Val> device(Val deviceEntityId, Val traccarConfig) {
        return webClient
                .httpRequest(HttpMethod.GET,
                        requestSettingsFromTraccarConfig(
                                String.format("/api/devices/%s", deviceEntityId.get().asText()), traccarConfig))
                .distinct();
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA)
    public Flux<Val> geofences(Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return geofences(variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA)
    public Flux<Val> geofences(@JsonObject Val traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/geofences", traccarConfig))
                .distinct();
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA)
    public Flux<Val> traccarGeofence(@Text Val geofenceEntityId, Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return traccarGeofence(geofenceEntityId, variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA)
    public Flux<Val> traccarGeofence(@Text Val geofenceEntityId, @JsonObject Val traccarConfig) {
        final var config = requestSettingsFromTraccarConfig(
                String.format("/api/geofences/%s", geofenceEntityId.get().asText()), traccarConfig);
        return webClient.httpRequest(HttpMethod.GET, config).distinct();
    }

    @Attribute(schema = GeoJsonSchemata.POLYGON)
    public Flux<Val> geofenceGeometry(@Text Val geofenceEntityId, Map<String, Val> variables) {
        return traccarGeofence(geofenceEntityId, variables).map(GeographicFunctionLibrary::traccarGeofenceToGeoJson)
                .distinct();
    }

    @Attribute(schema = GeoJsonSchemata.POLYGON)
    public Flux<Val> geofenceGeometry(@Text Val geofenceEntityId, @JsonObject Val traccarConfig) {
        return traccarGeofence(geofenceEntityId, traccarConfig).map(GeographicFunctionLibrary::traccarGeofenceToGeoJson)
                .distinct();
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA)
    public Flux<Val> traccarPosition(@Text Val deviceEntityId, Map<String, Val> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return traccarPosition(deviceEntityId, variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA)
    public Flux<Val> traccarPosition(@Text Val deviceEntityId, @JsonObject Val traccarConfig) {
        final var config = requestSettingsFromTraccarConfig("/api/positions", traccarConfig,
                Map.of("deviceId", deviceEntityId.get()));
        return webClient.httpRequest(HttpMethod.GET, config)
                .map(TraccarPolicyInformationPoint::takeFirstElementFromArray).distinct();
    }

    private static Val takeFirstElementFromArray(Val maybeArray) {
        if (!maybeArray.isArray()) {
            return Val.error("Bad response");
        }
        final var elements = maybeArray.getArrayNode();
        if (elements.isEmpty()) {
            return Val.UNDEFINED;
        }
        return Val.of(elements.get(0));
    }

    @Attribute(schema = GeoJsonSchemata.POINT)
    public Flux<Val> position(@Text Val deviceEntityId, Map<String, Val> variables) {
        return traccarPosition(deviceEntityId, variables).map(GeographicFunctionLibrary::traccarPositionToGeoJSON)
                .distinct();
    }

    @Attribute(schema = GeoJsonSchemata.POINT)
    public Flux<Val> position(@Text Val deviceEntityId, @JsonObject Val traccarConfig) {
        return traccarPosition(deviceEntityId, traccarConfig).map(GeographicFunctionLibrary::traccarPositionToGeoJSON)
                .distinct();
    }

    private Val requestSettingsFromTraccarConfig(String path, Val traccarConfig) {
        return requestSettingsFromTraccarConfig(path, traccarConfig, Map.of());
    }

    private Val requestSettingsFromTraccarConfig(String path, Val traccarConfig,
            Map<String, JsonNode> queryParameters) {
        final var requestSettings = JSON.objectNode();
        requestSettings.set(ReactiveWebClient.BASE_URL, getRequiredProperty(ReactiveWebClient.BASE_URL, traccarConfig));
        requestSettings.set(ReactiveWebClient.PATH, JSON.textNode(path));
        requestSettings.set(ReactiveWebClient.ACCEPT_MEDIATYPE, JSON.textNode(MediaType.APPLICATION_JSON_VALUE));

        final var headersWithBasicAuth = JSON.objectNode();
        headersWithBasicAuth.set(HttpHeaders.AUTHORIZATION, createBasicAuthHeader(traccarConfig));

        requestSettings.set(ReactiveWebClient.HEADERS, headersWithBasicAuth);

        final var config = traccarConfig.get();
        if (config != null && config.has(ReactiveWebClient.POLLING_INTERVAL)) {
            requestSettings.set(ReactiveWebClient.POLLING_INTERVAL, config.get(ReactiveWebClient.POLLING_INTERVAL));
        }

        if (config != null && config.has(ReactiveWebClient.REPEAT_TIMES)) {
            requestSettings.set(ReactiveWebClient.REPEAT_TIMES, config.get(ReactiveWebClient.REPEAT_TIMES));
        }

        if (!queryParameters.isEmpty()) {
            final var queryParams = JSON.objectNode();
            for (final var parameter : queryParameters.entrySet()) {
                queryParams.set(parameter.getKey(), parameter.getValue());
            }
            requestSettings.set(ReactiveWebClient.URL_PARAMS, queryParams);
        }

        return Val.of(requestSettings);
    }

    public static TextNode createBasicAuthHeader(Val traccarConfig) {
        final var userName           = getRequiredProperty("userName", traccarConfig).asText();
        final var password           = getRequiredProperty("password", traccarConfig).asText();
        final var credentials        = userName + ':' + password;
        final var encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return JSON.textNode("Basic " + encodedCredentials);
    }

    private static JsonNode getRequiredProperty(String fieldName, Val traccarConfig) {
        final var jsonNode = traccarConfig.get();
        if (jsonNode == null || !jsonNode.has(fieldName)) {
            throw new PolicyEvaluationException(
                    String.format("Required field '%s' missing from traccar configuration.", fieldName));
        }
        return jsonNode.get(fieldName);
    }

    private void assertThatTraccarConfigurationIsPresentInEnvironmentVariables(Map<String, Val> variables) {
        if (!variables.containsKey(TRACCAR_CONFIG)) {
            throw new PolicyEvaluationException(
                    "Cannot connect to Traccar server. The environment variable TRACCAR_CONFIG is undefined.");
        }
    }

}
