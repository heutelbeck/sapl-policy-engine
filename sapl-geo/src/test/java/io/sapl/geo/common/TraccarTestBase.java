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
package io.sapl.geo.common;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

public abstract class TraccarTestBase extends TestBase {

    private WebClient webClient = WebClient.builder().build();
    protected String  server;
    protected String  email;
    protected String  password;
    protected String  deviceId;

    @Container
    public static final GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082, 5055).withReuse(false);

    protected void registerUser(String email, String password) {
        String registerUserUrl = String.format("http://%s:%d/api/users", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        String userJson = String.format("""
                    {
                    "name": "testuser",
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);

        webClient.post().uri(registerUserUrl).header("Content-Type", "application/json").bodyValue(userJson).retrieve()
                .bodyToMono(String.class).block();
    }

    protected String establishSession(String email, String password) {

        var sessionUrl = String.format("http://%s:%d/api/session", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        var bodyProperties = new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;

        };

        bodyProperties.put("email", email);
        bodyProperties.put("password", password);

        var body = bodyProperties.entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)))
                .collect(Collectors.joining("&"));

        var client = WebClient.builder().build();

        var response = client.post().uri(sessionUrl).header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(body).retrieve().toEntity(String.class).block();

        if (response != null) {
            var setCookieHeader = response.getHeaders().getFirst("Set-Cookie");
            if (setCookieHeader != null) {
                return Arrays.stream(setCookieHeader.split(";")).filter(s -> s.startsWith("JSESSIONID")).findFirst()
                        .orElse(null);
            }
        }
        return null;

    }

    protected String createDevice(String sessionCookie) throws Exception {
        var createDeviceUrl = String.format("http://%s:%d/api/devices", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        String body = """
                {
                    "name": "Test Device",
                    "uniqueId": "1234567890"
                }
                """;

        var result = webClient.post().uri(createDeviceUrl).headers(headers -> {
            headers.add("Cookie", sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);
        }).bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();

        var id = result.get("id");
        if (id != null) {
            return id.asText();
        } else {
            throw new Exception("Id of device was null");
        }
    }

    protected Mono<JsonNode> postTraccarGeofence(String sessionCookie, String body) {

        var createGeofenceUrl = String.format("http://%s:%d/api/geofences", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        return webClient.post().uri(createGeofenceUrl).headers(headers -> {
            headers.add("Cookie", sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);
        }).bodyValue(body).retrieve().bodyToMono(JsonNode.class);

    }

    protected void linkGeofenceToDevice(String deviceId, int geofenceId, String sessionCookie) {

        var linkGeofenceUrl = String.format("http://%s:%d/api/permissions", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        String linkJson = """
                {"deviceId":"%s","geofenceId": %d}
                """;

        String body = String.format(linkJson, deviceId, geofenceId);

        webClient.post().uri(linkGeofenceUrl).headers(headers -> {
            headers.add("Cookie", sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);
        }).bodyValue(body).retrieve().bodyToMono(String.class).block();

    }

    protected Mono<String> addTraccarPosition(String deviceId, Double lat, Double lon)
            throws UnsupportedEncodingException {

        var timeStamp      = "2023-07-09 13:34:19";
        var url            = """
                http://%s:%d/?id=%s&lat=%s&lon=%s&timestamp=%s&hdop=0&altitude=100&speed=0&accuracy=14.0
                           """;
        var addPositionUrl = String.format(url, traccarContainer.getHost(), traccarContainer.getMappedPort(5055),
                deviceId, lat.toString(), lon.toString(), timeStamp);

        return exchange(webClient.get().uri(addPositionUrl));
    }

    protected Mono<String> exchange(RequestHeadersSpec<?> client) {

        return client.exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return response.bodyToMono(String.class).flatMap(body -> {
                    return Mono.error(new RuntimeException("Error adding position: " + response.statusCode()));
                });
            }
        });
    }
}
