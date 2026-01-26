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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.*;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Slf4j
class TraccarPolicyInformationPointIT {
    private static final ObjectValue                   EMPTY_VARIABLES = Value.EMPTY_OBJECT;
    private static final ObjectMapper                  MAPPER          = new ObjectMapper();
    private static final ReactiveWebClient             CLIENT          = new ReactiveWebClient(MAPPER);
    private static final TraccarPolicyInformationPoint TRACCAR_PIP     = new TraccarPolicyInformationPoint(CLIENT);

    private static TextValue   deviceId;
    private static TextValue   geofenceId1;
    private static ObjectValue settings;
    private static ObjectValue badSettings;
    private static String      email;
    private static String      password;
    private static String      host;
    private static int         port;

    @Container
    @SuppressWarnings("resource") // Common test pattern
    private static final GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:6.7")).withExposedPorts(8082, 5055).withReuse(false)
            .waitingFor(Wait.forHttp("/").forPort(8082).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2L)));

    private static ObjectValue settings(String email, String password, String host, int port) {
        return (ObjectValue) json("""
                {
                    "baseUrl": "http://%s:%d",
                    "userName": "%s",
                    "password": "%s",
                    "pollingIntervalMs": 250
                }""".formatted(host, port, email, password));
    }

    @BeforeAll
    @SneakyThrows
    static void setUp() {
        traccarContainer.start();
        email    = "test@fake.de";
        password = "1234";
        host     = traccarContainer.getHost();
        port     = traccarContainer.getMappedPort(8082);
        val uniqueDeviceId = "12345689";
        val traccarClient  = new TraccarTestClient(host, port, traccarContainer.getMappedPort(5055), email, password);
        traccarClient.registerUser(email, password);
        deviceId = Value.of(traccarClient.createDevice(uniqueDeviceId));
        val geofence1 = """
                {
                 "name":"fence1",
                 "description": "description for fence1",
                 "area":"POLYGON ((51.46488171048915 7.5781140235940825, 51.464847977218824 7.578980375357105, 51.46309381279673 7.579215012292622, 51.4629251395873 7.578835983396743, 51.46488171048915 7.5781140235940825))"
                }
                """;
        geofenceId1 = Value.of(traccarClient.createGeofence(geofence1));

        val geofence2 = """
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
        val attributeStream = TRACCAR_PIP.server(settings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.MAP_URL)).verifyComplete();
    }

    @Test
    void serverTest_noPollInterval() {
        val noPollSettings  = (ObjectValue) json("""
                {
                    "baseUrl": "http://%s:%d",
                    "userName": "%s",
                    "password": "%s",
                    "pollingIntervalMs": 250
                }""".formatted(host, port, email, password));
        val attributeStream = TRACCAR_PIP.server(noPollSettings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.MAP_URL)).verifyComplete();
    }

    @Test
    void serverTest_withRepetitions() {
        val noPollSettings  = (ObjectValue) json("""
                {
                    "baseUrl": "http://%s:%d",
                    "userName": "%s",
                    "password": "%s",
                    "repetitions": 250
                }""".formatted(host, port, email, password));
        val attributeStream = TRACCAR_PIP.server(noPollSettings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.MAP_URL)).verifyComplete();
    }

    @Test
    void serverTest_withConfig() {
        val attributeStream = TRACCAR_PIP.server(settings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.MAP_URL)).verifyComplete();
    }

    @Test
    void serverTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.server(badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void devicesTest() {
        val attributeStream = TRACCAR_PIP.devices(settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ArrayValue.class::isInstance).verifyComplete();
    }

    @Test
    void devicesTest_withConfig() {
        val attributeStream = TRACCAR_PIP.devices(settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ArrayValue.class::isInstance).verifyComplete();
    }

    @Test
    void devicesTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.devices(badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void deviceTest() {
        val attributeStream = TRACCAR_PIP.device(deviceId, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.NAME))
                .verifyComplete();
    }

    @Test
    void deviceTest_withConfig() {
        val attributeStream = TRACCAR_PIP.device(deviceId, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.NAME))
                .verifyComplete();
    }

    @Test
    void deviceTest_invalidInput() {
        val attributeStream = TRACCAR_PIP.device(Value.of("invalid"), settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void deviceTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.device(deviceId, badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void traccarPositionTest() {
        val attributeStream = TRACCAR_PIP.traccarPosition(deviceId, settings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.LONGITUDE)).verifyComplete();
    }

    @Test
    void traccarPositionTest_withConfig() {
        val attributeStream = TRACCAR_PIP.traccarPosition(deviceId, settings).next();
        StepVerifier.create(attributeStream)
                .expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.LONGITUDE)).verifyComplete();
    }

    @Test
    void traccarPositionTest_invalidInput() {
        val attributeStream = TRACCAR_PIP.traccarPosition(Value.of("invalid"), settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void traccarPositionTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.traccarPosition(deviceId, badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void positionTest() {
        val attributeStream = TRACCAR_PIP.position(deviceId, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey("coordinates"))
                .verifyComplete();
    }

    @Test
    void positionTest_withConfig() {
        val attributeStream = TRACCAR_PIP.position(deviceId, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey("coordinates"))
                .verifyComplete();
    }

    @Test
    void positionTest_invalidInput() {
        val attributeStream = TRACCAR_PIP.position(Value.of("invalid"), settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void positionTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.position(deviceId, badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void geofencesTest() {
        val attributeStream = TRACCAR_PIP.geofences(settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ArrayValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofencesTest_withConfig() {
        val attributeStream = TRACCAR_PIP.geofences(settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ArrayValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofencesTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.geofences(badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofenceTest() {
        val attributeStream = TRACCAR_PIP.traccarGeofence(geofenceId1, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.AREA))
                .verifyComplete();
    }

    @Test
    void geofenceTest_withConfig() {
        val attributeStream = TRACCAR_PIP.traccarGeofence(geofenceId1, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey(TraccarSchemata.AREA))
                .verifyComplete();
    }

    @Test
    void geofenceTest_invalidInput() {
        val attributeStream = TRACCAR_PIP.traccarGeofence(Value.of("invalid"), settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofenceTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.traccarGeofence(geofenceId1, badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofenceGeometryTest() {
        val attributeStream = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey("coordinates"))
                .verifyComplete();
    }

    @Test
    void geofenceGeometryTest_withConfig() {
        val attributeStream = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(a -> ((ObjectValue) a).containsKey("coordinates"))
                .verifyComplete();
    }

    @Test
    void geofenceGeometryTest_invalidInput() {
        val attributeStream = TRACCAR_PIP.geofenceGeometry(Value.of("invalid"), settings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void geofenceGeometryTest_invalidConfig() {
        val attributeStream = TRACCAR_PIP.geofenceGeometry(geofenceId1, badSettings).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences() {
        val position = TRACCAR_PIP.position(deviceId, settings).blockFirst();
        val fence    = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).blockFirst();
        assertThat(position).isNotNull();
        assertThat(fence).isNotNull();
        var result = GeographicFunctionLibrary.contains((ObjectValue) fence, (ObjectValue) position);
        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_withConfig() {
        val position = TRACCAR_PIP.position(deviceId, settings).blockFirst();
        val fence    = TRACCAR_PIP.geofenceGeometry(geofenceId1, settings).blockFirst();
        assertThat(position).isNotNull();
        assertThat(fence).isNotNull();
        var result = GeographicFunctionLibrary.contains((ObjectValue) fence, (ObjectValue) position);
        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_notContains() {
        val position     = TRACCAR_PIP.position(deviceId, settings).blockFirst();
        val outsideFence = TRACCAR_PIP.geofenceGeometry(Value.of("2"), settings).blockFirst();
        assertThat(position).isNotNull();
        assertThat(outsideFence).isNotNull();
        var result = GeographicFunctionLibrary.contains((ObjectValue) outsideFence, (ObjectValue) position);
        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_invalidInput() {
        val attributeStream = TRACCAR_PIP.position(Value.of("invalid"), settings);
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences_invalidConfig() {
        val attributeStream = TRACCAR_PIP.position(deviceId, badSettings);
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).thenCancel().verify();
    }

    @Test
    void serverTest_missingBaseUrl() {
        val config = (ObjectValue) json("""
                {
                    "userName": "email@address.org",
                    "password": "password"
                  }
                """);
        assertThatThrownBy(() -> TRACCAR_PIP.server(config)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serverTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.server(EMPTY_VARIABLES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void devicesTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.devices(EMPTY_VARIABLES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deviceTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.device(deviceId, EMPTY_VARIABLES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traccarPositionTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.traccarPosition(deviceId, EMPTY_VARIABLES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.position(deviceId, EMPTY_VARIABLES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geofencesTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.geofences(EMPTY_VARIABLES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geofenceTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.traccarGeofence(geofenceId1, EMPTY_VARIABLES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geofenceGeometryTest_missingConfig() {
        assertThatThrownBy(() -> TRACCAR_PIP.geofenceGeometry(geofenceId1, EMPTY_VARIABLES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traccarPositionTest_emptyArrayResponse() {
        // Arrange
        val mockWebClient = Mockito.mock(ReactiveWebClient.class);
        val testPip       = new TraccarPolicyInformationPoint(mockWebClient);
        val config        = (ObjectValue) json("""
                {
                    "baseUrl": "http://test.de:8082",
                    "userName": "email@address.org",
                    "password": "password"
                  }
                """);
        val someDeviceId  = Value.of("someDeviceId");
        when(mockWebClient.httpRequest(Mockito.any(), Mockito.any())).thenReturn(Flux.just(json("[]")));
        // Act
        val attributeStream = testPip.traccarPosition(someDeviceId, config).next();

        // Assert
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }
}
