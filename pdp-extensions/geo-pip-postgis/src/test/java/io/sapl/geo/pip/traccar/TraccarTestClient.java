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

import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

public class TraccarTestClient {

    private static final String BASE_URL_TEMPLATE = "http://%s:%d";

    private final WebClient apiClient;
    private final WebClient positioningClient;
    private final String    basicAuthValue;

    public TraccarTestClient(String host, int apiPort, int positioningPort, String email, String password) {
        basicAuthValue    = "Basic " + Base64.getEncoder().encodeToString((email + ":" + password).getBytes());
        apiClient         = WebClient.builder().baseUrl(String.format(BASE_URL_TEMPLATE, host, apiPort))
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthValue).build();
        positioningClient = WebClient.builder().baseUrl(String.format(BASE_URL_TEMPLATE, host, positioningPort))
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthValue).build();
    }

    public String registerUser(String email, String password) {
        final var userJson = String.format("""
                {
                    "name": "testuser",
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);
        return apiClient.post().uri("/api/users").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(userJson).retrieve().bodyToMono(String.class).block();
    }

    public String createDevice(String uniqueId) throws Exception {
        final var body          = String.format("""
                {
                    "name": "Test Device",
                    "uniqueId": "%s"
                }
                """, uniqueId);
        final var createdDevice = apiClient.post().uri("/api/devices")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).bodyValue(body).retrieve()
                .bodyToMono(JsonNode.class).blockOptional();
        if (createdDevice.isPresent()) {
            return createdDevice.get().get("id").asText();
        }
        throw new IllegalStateException("Could not create device");
    }

    public String createGeofence(String geoFenceData) {
        return apiClient.post().uri("/api/geofences").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(geoFenceData).retrieve().bodyToMono(JsonNode.class).block().get("id").asText();
    }

    public String addTraccarPosition(String deviceId, Double lat, Double lon, Double altitude) {
        final var queryParams = Map.of("id", deviceId, "lat", lat.toString(), "lon", lon.toString(), "altitude",
                altitude.toString(), "speed", "0", "accuracy", "14.0", "timestamp", "2023-07-09 13:34:19");
        return positioningClient.get().uri(uriBuilder -> {
            queryParams.forEach(uriBuilder::queryParam);
            return uriBuilder.build();
        }).retrieve().bodyToMono(String.class).block();
    }
}
