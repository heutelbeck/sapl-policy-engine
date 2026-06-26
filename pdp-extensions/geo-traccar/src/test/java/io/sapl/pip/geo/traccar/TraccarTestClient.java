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
package io.sapl.pip.geo.traccar;

import lombok.SneakyThrows;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

class TraccarTestClient {

    private static final String BASE_URL_TEMPLATE = "http://%s:%d";
    private static final String CONTENT_TYPE      = "Content-Type";
    private static final String AUTHORIZATION     = "Authorization";
    private static final String APPLICATION_JSON  = "application/json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client;
    private final String     apiBaseUrl;
    private final String     positioningBaseUrl;
    private final String     basicAuth;

    TraccarTestClient(String host, int apiPort, int positioningPort, String email, String password) {
        this.basicAuth          = "Basic "
                + Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.apiBaseUrl         = String.format(BASE_URL_TEMPLATE, host, apiPort);
        this.positioningBaseUrl = String.format(BASE_URL_TEMPLATE, host, positioningPort);
        this.client             = HttpClient.newHttpClient();
    }

    @SneakyThrows
    String registerUser(String email, String password) {
        val body = String.format("""
                {\
                    "name": "testuser",\
                    "email": "%s",\
                    "password": "%s"\
                }""", email, password);
        return postJson("/api/users", body);
    }

    @SneakyThrows
    String createDevice(String uniqueId) {
        val body     = String.format("""
                {\
                    "name": "Test Device",\
                    "uniqueId": "%s"\
                }""", uniqueId);
        val response = postJson("/api/devices", body);
        val node     = MAPPER.readTree(response);
        if (node == null || !node.has("id")) {
            throw new IllegalStateException("Could not create device");
        }
        return node.get("id").asString();
    }

    @SneakyThrows
    String createGeofence(String geoFenceData) {
        val response = postJson("/api/geofences", geoFenceData);
        val node     = MAPPER.readTree(response);
        if (node == null || !node.has("id")) {
            return "";
        }
        return node.get("id").asString();
    }

    @SneakyThrows
    String addTraccarPosition(String deviceId, Double lat, Double lon, Double altitude) {
        val queryParams = Map.of("id", deviceId, "lat", lat.toString(), "lon", lon.toString(), "altitude",
                altitude.toString(), "speed", "0", "accuracy", "14.0", "timestamp", "2023-07-09 13:34:19");
        val sb          = new StringBuilder(positioningBaseUrl).append('?');
        var first       = true;
        for (val entry : queryParams.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        val request = HttpRequest.newBuilder().uri(URI.create(sb.toString())).header(AUTHORIZATION, basicAuth).GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String postJson(String path, String body) throws Exception {
        val request = HttpRequest.newBuilder().uri(URI.create(apiBaseUrl + path)).header(AUTHORIZATION, basicAuth)
                .header(CONTENT_TYPE, APPLICATION_JSON).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
