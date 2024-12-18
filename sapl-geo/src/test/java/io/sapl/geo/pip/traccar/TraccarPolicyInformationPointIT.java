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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.ParseException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.library.GeographicFunctionLibrary;
import io.sapl.geo.schemata.TraccarSchemata;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.test.StepVerifier;

@Slf4j
class TraccarPolicyInformationPointIT {
    private static final ObjectMapper                  MAPPER      = new ObjectMapper();
    private static final ReactiveWebClient             CLIENT      = new ReactiveWebClient(MAPPER);
    private static final TraccarPolicyInformationPoint TRACCAR_PIP = new TraccarPolicyInformationPoint(CLIENT);

    private static String deviceId;
    private static String geofenceId1;
    private static String geofenceId2;
    private static Val    settings;

    @Container
    @SuppressWarnings("resource") // Common test pattern
    private static final GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:latest")).withExposedPorts(8082, 5055).withReuse(false)
            .waitingFor(Wait.forHttp("/").forPort(8082).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2L)));

    @SneakyThrows
    private static Val settings(String email, String password, String host, int port) {
        return Val.ofJson(String.format("""
                {
                    "baseUrl": "http://%s:%d",
                    "userName": "%s",
                    "password": "%s",
                    "pollingIntervalMs": 250
                }
                """, host, port, email, password));
    }

    @BeforeAll
    @SneakyThrows
    static void setUp() {
        traccarContainer.start();
        final var email          = "test@fake.de";
        final var password       = "1234";
        final var uniqueDeviceId = "12345689";
        final var traccarClient  = new TraccarTestClient(traccarContainer.getHost(),
                traccarContainer.getMappedPort(8082), traccarContainer.getMappedPort(5055), email, password);
        traccarClient.registerUser(email, password);
        deviceId = traccarClient.createDevice(uniqueDeviceId);
        final var geofence1 = """
                {
                 "name":"fence1",
                 "description": "description for fence1",
                 "area":"POLYGON ((51.46488171048915 7.5781140235940825, 51.464847977218824 7.578980375357105, 51.46309381279673 7.579215012292622, 51.4629251395873 7.578835983396743, 51.46488171048915 7.5781140235940825))"
                }
                """;
        geofenceId1 = traccarClient.createGeofence(geofence1);

        final var geofence2 = """
                {
                 "name":"lmu",
                 "description": "description for lmu",
                 "area":"POLYGON ((48.150402911178844 11.566792870984045, 48.1483205765966 11.56544925428264, 48.147576865197465 11.56800995875841, 48.14969540929175 11.56935357546081, 48.150402911178844 11.566792870984045))"
                }
                """;
        geofenceId2 = traccarClient.createGeofence(geofence2);
        traccarClient.addTraccarPosition(uniqueDeviceId, 51.4642414, 7.5789155, 198.8);
        settings = settings(email, password, traccarContainer.getHost(), traccarContainer.getMappedPort(8082));
    }

    @Test
    void serverTest() {
        final var attributestream = TRACCAR_PIP.server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void devicesTest() {
        final var attributestream = TRACCAR_PIP.devices(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void deviceTest() {
        final var attributestream = TRACCAR_PIP
                .device(Val.of(deviceId), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.NAME)).verifyComplete();
    }

    @Test
    void traccarPositionTest() {
        final var attributestream = TRACCAR_PIP
                .traccarPosition(Val.of(deviceId), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).log()
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.LONGITUDE))
                .verifyComplete();
    }

    @Test
    void positionTest() {
        final var attributestream = TRACCAR_PIP
                .position(Val.of(deviceId), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void geofencesTest() {
        final var attributestream = TRACCAR_PIP
                .geofences(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void geofenceTest() {
        final var attributestream = TRACCAR_PIP
                .traccarGeofence(Val.of(geofenceId1), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.AREA)).verifyComplete();
    }

    @Test
    void geofenceGeometryTest() {
        final var attributestream = TRACCAR_PIP
                .geofenceGeometry(Val.of(geofenceId1), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences() throws ParseException {
        final var position = TRACCAR_PIP
                .position(Val.of(deviceId), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .blockFirst();
        final var fence    = TRACCAR_PIP
                .geofenceGeometry(Val.of(geofenceId1), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, settings))
                .blockFirst();
        assertTrue(GeographicFunctionLibrary.contains(fence, position).getBoolean());
    }
}
