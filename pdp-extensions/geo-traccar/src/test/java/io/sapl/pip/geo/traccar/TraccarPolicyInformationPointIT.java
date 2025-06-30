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
package io.sapl.pip.geo.traccar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Slf4j
class TraccarPolicyInformationPointIT {
    private static final Map<String, Val>              EMPTY_MAP   = Map.of();
    private static final ObjectMapper                  MAPPER      = new ObjectMapper();
    private static final ReactiveWebClient             CLIENT      = new ReactiveWebClient(MAPPER);
    private static final TraccarPolicyInformationPoint TRACCAR_PIP = new TraccarPolicyInformationPoint(CLIENT);

    private static Val    deviceId;
    private static Val    geofenceId1;
    private static Val    settings;
    private static Val    badSettings;
    private static String email;
    private static String password;
    private static String host;
    private static int    port;

    @Container
    @SuppressWarnings("resource") // Common test pattern
    private static final GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:6.7")).withExposedPorts(8082, 5055).withReuse(false)
            .waitingFor(Wait.forHttp("/").forPort(8082).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2L)));

    @SneakyThrows
    private static Val settings(String email, String password, String host, int port) {
        return Val.ofJson(String.format("""
                {\
                    "baseUrl": "http://%s:%d",\
                    "userName": "%s",\
                    "password": "%s",\
                    "pollingIntervalMs": 250\
                }""", host, port, email, password));
    }

    @BeforeAll
    @SneakyThrows
    static void setUp() {
        traccarContainer.start();
        email    = "test@fake.de";
        password = "1234";
        host     = traccarContainer.getHost();
        port     = traccarContainer.getMappedPort(8082);
        final var uniqueDeviceId = "12345689";
        final var traccarClient  = new TraccarTestClient(host, port, traccarContainer.getMappedPort(5055), email,
                password);
        traccarClient.registerUser(email, password);
        deviceId = Val.of(traccarClient.createDevice(uniqueDeviceId));
        final var geofence1 = """
                {
                 "name":"fence1",
                 "description": "description for fence1",
                 "area":"POLYGON ((51.46488171048915 7.5781140235940825, 51.464847977218824 7.578980375357105, 51.46309381279673 7.579215012292622, 51.4629251395873 7.578835983396743, 51.46488171048915 7.5781140235940825))"
                }
                """;
        geofenceId1 = Val.of(traccarClient.createGeofence(geofence1));

        final var geofence2 = """
                {
                 "name":"lmu",
                 "description": "description for lmu",
                 "area":"POLYGON ((48.150402911178844 11.566792870984045, 48.1483205765966 11.56544925428264, 48.147576865197465 11.56800995875841, 48.14969540929175 11.56935357546081, 48.150402911178844 11.566792870984045))"
                }
                """;
        traccarClient.createGeofence(geofence2);
        traccarClient.addTraccarPosition(uniqueDeviceId, 51.4642414, 7.5789155, 198.8);
        settings    = settings(email, password, host, port);
        badSettings = settings(email, password, "https://some-bad-server.local", 8082);
    }

    @Test
    void serverTest() {
        final var attributestream = TRACCAR_PIP.server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void serverTest_noPollIntervall() throws JsonProcessingException {
        final var noPollSettings  = Val.ofJson(String.format("""
                {\
                    "baseUrl": "http://%s:%d",\
                    "userName": "%s",\
                    "password": "%s",\
                    "pollingIntervalMs": 250\
                }""", host, port, email, password));
        final var attributestream = TRACCAR_PIP
                .server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, noPollSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void serverTest_withRepetitions() throws JsonProcessingException {
        final var noPollSettings  = Val.ofJson(String.format("""
                {\
                    "baseUrl": "http://%s:%d",\
                    "userName": "%s",\
                    "password": "%s",\
                    "repetitions": 250\
                }""", host, port, email, password));
        final var attributestream = TRACCAR_PIP
                .server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, noPollSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void serverTest_withConfig() {
        final var attributestream = TRACCAR_PIP.server(settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void serverTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void devicesTest() {
        final var attributestream = TRACCAR_PIP.devices(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isArray).verifyComplete();
    }

    @Test
    void devicesTest_withConfig() {
        final var attributestream = TRACCAR_PIP.devices(settings).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isArray).verifyComplete();
    }

    @Test
    void devicesTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .devices(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void deviceTest() {
        final var attributestream = TRACCAR_PIP
                .device(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.NAME)).verifyComplete();
    }

    @Test
    void deviceTest_withConfig() {
        final var attributestream = TRACCAR_PIP.device(deviceId, settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.NAME)).verifyComplete();
    }

    @Test
    void deviceTest_invalidInput() {
        final var attributestream = TRACCAR_PIP
                .device(Val.of("invalid"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void deviceTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .device(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void traccarPositionTest() {
        final var attributestream = TRACCAR_PIP
                .traccarPosition(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.LONGITUDE))
                .verifyComplete();
    }

    @Test
    void traccarPositionTest_withConfig() {
        final var attributestream = TRACCAR_PIP.traccarPosition(deviceId, settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.LONGITUDE))
                .verifyComplete();
    }

    @Test
    void traccarPositionTest_invalidInput() {
        final var attributestream = TRACCAR_PIP
                .traccarPosition(Val.of("invalid"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void traccarPositionTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .traccarPosition(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).log()
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void positionTest() {
        final var attributestream = TRACCAR_PIP
                .position(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void positionTest_withConfig() {
        final var attributestream = TRACCAR_PIP.position(deviceId, settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void positionTest_invalidInput() {
        final var attributestream = TRACCAR_PIP
                .position(Val.of("invalid"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void positionTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .position(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).thenCancel().verify();
    }

    @Test
    void geofencesTest() {
        final var attributestream = TRACCAR_PIP
                .geofences(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void geofencesTest_withConfig() {
        final var attributestream = TRACCAR_PIP.geofences(settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void geofencesTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .geofences(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void geofenceTest() {
        final var attributestream = TRACCAR_PIP
                .traccarGeofence(geofenceId1, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.AREA)).verifyComplete();
    }

    @Test
    void geofenceTest_withConfig() {
        final var attributestream = TRACCAR_PIP.traccarGeofence(geofenceId1, settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.AREA)).verifyComplete();
    }

    @Test
    void geofenceTest_invalidInput() {
        final var attributestream = TRACCAR_PIP
                .traccarGeofence(Val.of("invalid"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void geofenceTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .traccarGeofence(geofenceId1, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings)).next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void geofenceGeometryTest() {
        final var attributestream = TRACCAR_PIP
                .geofenceGeometry(geofenceId1, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void geofenceGeometryTest_withConfig() {
        final var attributestream = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void geofenceGeometryTest_invalidInput() {
        final var attributestream = TRACCAR_PIP
                .geofenceGeometry(Val.of("invalid"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void geofenceGeometryTest_invalidConfig() {
        final var attributestream = TRACCAR_PIP
                .geofenceGeometry(geofenceId1, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences() {
        final var position = TRACCAR_PIP
                .position(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).blockFirst();
        final var fence    = TRACCAR_PIP
                .geofenceGeometry(geofenceId1, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .blockFirst();
        assertNotNull(position);
        assertNotNull(fence);
        assertTrue(GeographicFunctionLibrary.contains(fence, position).getBoolean());
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_withConfig() {
        final var position = TRACCAR_PIP.position(deviceId, settings).blockFirst();
        final var fence    = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).blockFirst();
        assertNotNull(position);
        assertNotNull(fence);
        assertTrue(GeographicFunctionLibrary.contains(fence, position).getBoolean());
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_notContains() {
        final var position     = TRACCAR_PIP
                .position(deviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).blockFirst();
        final var outsideFence = TRACCAR_PIP
                .geofenceGeometry(Val.of("2"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .blockFirst();
        assertNotNull(position);
        assertNotNull(outsideFence);
        assertFalse(GeographicFunctionLibrary.contains(outsideFence, position).getBoolean());
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_invalidInput() {
        final var attributeStream = TRACCAR_PIP.position(Val.of("invalid"),
                Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings));
        StepVerifier.create(attributeStream).expectNextMatches(Val::isError).thenCancel().verify();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_invalidConfig() {
        final var attributeStream = TRACCAR_PIP.position(deviceId,
                Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, badSettings));
        StepVerifier.create(attributeStream).expectNextMatches(Val::isError).thenCancel().verify();
    }

    @Test
    void serverTest_missingBaseUrl() throws JsonProcessingException {
        final var config = Val.ofJson("""
                {
                    "userName": "email@address.org",
                    "password": "password"
                  }
                   """);
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.server(config));
    }

    @Test
    void serverTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.server(EMPTY_MAP));
    }

    @Test
    void devicesTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.devices(EMPTY_MAP));
    }

    @Test
    void deviceTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.device(deviceId, EMPTY_MAP));
    }

    @Test
    void traccarPositionTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.traccarPosition(deviceId, EMPTY_MAP));
    }

    @Test
    void positionTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.position(deviceId, EMPTY_MAP));
    }

    @Test
    void geofencesTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.geofences(EMPTY_MAP));
    }

    @Test
    void geofenceTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.traccarGeofence(geofenceId1, EMPTY_MAP));
    }

    @Test
    void geofenceGeometryTest_missingConfig() {
        assertThrows(PolicyEvaluationException.class, () -> TRACCAR_PIP.geofenceGeometry(geofenceId1, EMPTY_MAP));
    }

    @Test
    void traccarPositionTest_emptyArrayResponse() throws JsonProcessingException {
        // Arrange
        final var mockWebClient = Mockito.mock(ReactiveWebClient.class);
        final var testPip       = new TraccarPolicyInformationPoint(mockWebClient);
        final var config        = Val.ofJson("""
                {
                    "baseUrl": "http://test.de:8082",
                    "userName": "email@address.org",
                    "password": "password"
                  }
                """);
        final var someDeviceId  = Val.of("someDeviceId");
        when(mockWebClient.httpRequest(Mockito.any(), Mockito.any())).thenReturn(Flux.just(Val.ofJson("[]")));
        // Act
        final var attributeStream = testPip
                .traccarPosition(someDeviceId, Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, config)).next();

        // Assert
        StepVerifier.create(attributeStream).expectNextMatches(Val::isError).verifyComplete();
    }
}
