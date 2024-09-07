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
package io.sapl.server;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.geo.common.TestBase;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class TraccarPolicyInformationPointTestsIT extends TestBase {

    private WebClient webClient = WebClient.builder().build();
    private String    path      = "src/test/resources/policies/%s";
    private String    server;
    private String    email;
    private String    password;
    private String    deviceId;
    private Subject   subject;

    @Container
    public static GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082, 5055).withReuse(false);

    @BeforeAll
    void setUp() throws IOException {

        email    = "test@fake.de";
        password = "1234";
        registerUser(email, password);
        var sessionCookie = establishSession(email, password);
        deviceId = createDevice(sessionCookie);

        subject = new Subject(email, password, traccarContainer.getHost() + ":" + traccarContainer.getMappedPort(8082),
                deviceId);

        var body  = """
                {"name":"fence1","description": "description for fence1","area":"POLYGON ((48.25767 11.54370, 48.25767 11.54422, 48.25747 11.54422, 48.25747 11.54370, 48.25767 11.54370))"}
                """;
        var body2 = """
                {"name":"lmu","description": "description for lmu","area":"POLYGON ((48.150402911178844 11.566792870984045, 48.1483205765966 11.56544925428264, 48.147576865197465 11.56800995875841, 48.14969540929175 11.56935357546081, 48.150402911178844 11.566792870984045))"}
                """;

        var traccarGeofences = new String[] { body, body2 };

        for (var fence : traccarGeofences) {

            var fenceId = postTraccarGeofence(sessionCookie, fence).block().get("id").asInt();
            ;
            linkGeofenceToDevice(deviceId, fenceId, sessionCookie);
        }

        addTraccarPosition("1234567890", 51.34533, 7.40575).block();

        var template = """
                      {
                "algorithm": "DENY_OVERRIDES",
                "variables":
                	{
                		"TRACCAR_DEFAULT_CONFIG":
                		{
                		    "user":"%s",
                                  "password":"%s",
                			"server":"%s",
                			"protocol": "%s"
                		}
                	}
                }
                  """;

        server = String.format("%s:%s", traccarContainer.getHost(), traccarContainer.getMappedPort(8082));
        var pdp = String.format(template, email, password, server, "http");
        writePdp(pdp, String.format(path, "/traccarGeofencesTestEnvironmentVariable/pdp.json"));
        writePdp(pdp, String.format(path, "/traccarPositionTestEnvironmentVariable/pdp.json"));

    }

    @ParameterizedTest
    @Execution(ExecutionMode.CONCURRENT)
    @CsvSource({ "traccarPositionTestEnvironmentVariable", "traccarPositionTest",
            "traccarGeofencesTestEnvironmentVariable", "traccarGeofencesTest" })
    void TraccarPipTest(String pdpPath) throws InitializationException {

        var pdp               = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(String.format(path, pdpPath),
                () -> List.of(new TraccarPolicyInformationPoint(new ObjectMapper())), List::of, List::of, List::of);
        var authzSubscription = AuthorizationSubscription.of(subject, "action", "resource");
        var pdpDecisionFlux   = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    private void registerUser(String email, String password) {
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

    private String establishSession(String email, String password) {

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
                var sessionCookie = Arrays.stream(setCookieHeader.split(";")).filter(s -> s.startsWith("JSESSIONID"))
                        .findFirst().orElse(null);
                return sessionCookie;
            }
        }
        return null;

    }

    private String createDevice(String sessionCookie) {
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

        return result.get("id").asText();

    }

    private Mono<JsonNode> postTraccarGeofence(String sessionCookie, String body) {

        var createGeofenceUrl = String.format("http://%s:%d/api/geofences", traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082));

        return webClient.post().uri(createGeofenceUrl).headers(headers -> {
            headers.add("Cookie", sessionCookie);
            headers.setContentType(MediaType.APPLICATION_JSON);
        }).bodyValue(body).retrieve().bodyToMono(JsonNode.class);

    }

    private void linkGeofenceToDevice(String deviceId, int geofenceId, String sessionCookie) {

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

    public Mono<String> addTraccarPosition(String deviceId, Double lat, Double lon) {

        var webClient = WebClient.builder().build();

        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        var timeStamp = LocalDateTime.now().format(formatter);

        var url            = """
                http://%s:%d/?id=%s&lat=%s&lon=%s&timestamp=%s&hdop=0&altitude=990&speed=0
                           """;
        var addPositionUrl = String.format(url, traccarContainer.getHost(), traccarContainer.getMappedPort(5055),
                deviceId, lat.toString(), lon.toString(), timeStamp);

        return exchange(webClient.get().uri(addPositionUrl));
    }

    private Mono<String> exchange(RequestHeadersSpec<?> client) {

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

    @Getter
    @RequiredArgsConstructor
    class Subject {
        private final String user;
        private final String password;
        private final String server;
        private final String deviceId;

    }
}
