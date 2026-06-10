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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.BlockingWebClient;
import io.sapl.api.stream.Streams;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.api.test.stream.TestTimeScheduler;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraccarPolicyInformationPointIT {
    private static final Instant                       REFERENCE   = Instant.parse("2025-01-01T00:00:00Z");
    private static final JsonMapper                    MAPPER      = JsonMapper.builder().build();
    private static final BlockingWebClient             CLIENT      = new BlockingWebClient(MAPPER,
            HttpClient.newHttpClient(), new MutableClock(REFERENCE), new TestTimeScheduler(REFERENCE));
    private static final TraccarPolicyInformationPoint TRACCAR_PIP = new TraccarPolicyInformationPoint(CLIENT);

    private static TextValue   deviceId;
    private static TextValue   geofenceId1;
    private static ObjectValue config;
    private static ObjectValue badConfig;
    private static ObjectValue secrets;
    private static String      email;
    private static String      password;
    private static String      host;
    private static int         port;

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> traccarContainer = new GenericContainer<>(
            DockerImageName.parse("traccar/traccar:6.7")).withExposedPorts(8082, 5055).withReuse(false)
            .waitingFor(Wait.forHttp("/").forPort(8082).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2L)));

    private static ObjectValue config(String host, int port) {
        return (ObjectValue) json("""
                {
                    "baseUrl": "http://%s:%d",
                    "pollingIntervalMs": 250
                }""".formatted(host, port));
    }

    private static ObjectValue secrets(String email, String password) {
        return ObjectValue.builder().put("traccar",
                ObjectValue.builder().put("userName", Value.of(email)).put("password", Value.of(password)).build())
                .build();
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
        config    = config(host, port);
        badConfig = config("some-bad-server.local", 8082);
        secrets   = secrets(email, password);
    }

    @Nested
    @DisplayName("server endpoint")
    class ServerTests {

        static java.util.stream.Stream<Arguments> serverSettingsVariations() {
            return Stream.of(arguments("with polling interval", """
                    {
                        "baseUrl": "http://%s:%d",
                        "pollingIntervalMs": 250
                    }"""), arguments("without polling interval", """
                    {
                        "baseUrl": "http://%s:%d"
                    }"""), arguments("with repetitions", """
                    {
                        "baseUrl": "http://%s:%d",
                        "repetitions": 250
                    }"""));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("serverSettingsVariations")
        @DisplayName("returns server info")
        void whenValidSettingsThenReturnsServerInfo(String description, String settingsTemplate) {
            val testConfig = (ObjectValue) json(settingsTemplate.formatted(host, port));
            try (val stream = TRACCAR_PIP.server(testConfig, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(((ObjectValue) v).containsKey(TraccarSchemata.MAP_URL)).isTrue());
            }
        }

        @Test
        @DisplayName("returns error for invalid config")
        void whenInvalidConfigThenReturnsError() {
            try (val stream = TRACCAR_PIP.server(badConfig, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("returns error for missing baseUrl")
        void whenMissingBaseUrlThenReturnsError() {
            try (val stream = TRACCAR_PIP.server(Value.EMPTY_OBJECT, secrets)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                        .awaitsCompletion();
            }
        }

    }

    @Nested
    @DisplayName("devices endpoint")
    class DevicesTests {

        @Test
        @DisplayName("returns array of devices")
        void whenValidSettingsThenReturnsDevices() {
            try (val stream = TRACCAR_PIP.devices(config, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ArrayValue.class));
            }
        }

        @Test
        @DisplayName("returns error for invalid config")
        void whenInvalidConfigThenReturnsError() {
            try (val stream = TRACCAR_PIP.devices(badConfig, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("returns error for empty config")
        void whenEmptyConfigThenReturnsError() {
            try (val stream = TRACCAR_PIP.devices(Value.EMPTY_OBJECT, secrets)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                        .awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("geofences endpoint")
    class GeofencesTests {

        @Test
        @DisplayName("returns array of geofences")
        void whenValidSettingsThenReturnsGeofences() {
            try (val stream = TRACCAR_PIP.geofences(config, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ArrayValue.class));
            }
        }

        @Test
        @DisplayName("returns error for invalid config")
        void whenInvalidConfigThenReturnsError() {
            try (val stream = TRACCAR_PIP.geofences(badConfig, secrets)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @Test
        @DisplayName("returns error for empty config")
        void whenEmptyConfigThenReturnsError() {
            try (val stream = TRACCAR_PIP.geofences(Value.EMPTY_OBJECT, secrets)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                        .awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("single-resource endpoints with deviceId")
    class DeviceResourceTests {

        static java.util.stream.Stream<Arguments> deviceEndpoints() {
            BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> device          = (id, cfg) -> TRACCAR_PIP
                    .device((TextValue) id, cfg, secrets);
            BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> traccarPosition = (id, cfg) -> TRACCAR_PIP
                    .traccarPosition((TextValue) id, cfg, secrets);
            BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> position        = (id, cfg) -> TRACCAR_PIP
                    .position((TextValue) id, cfg, secrets);
            return Stream.of(arguments("device", device, TraccarSchemata.NAME),
                    arguments("traccarPosition", traccarPosition, TraccarSchemata.LONGITUDE),
                    arguments("position", position, "coordinates"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deviceEndpoints")
        @DisplayName("returns data for valid device")
        void whenValidDeviceIdThenReturnsData(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(deviceId, config)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(((ObjectValue) v).containsKey(expectedKey)).isTrue());
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deviceEndpoints")
        @DisplayName("returns error for invalid device")
        void whenInvalidDeviceIdThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(Value.of("invalid"), config)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deviceEndpoints")
        @DisplayName("returns error for invalid config")
        void whenInvalidConfigThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(deviceId, badConfig)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("deviceEndpoints")
        @DisplayName("returns error for empty config")
        void whenEmptyConfigThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(deviceId, Value.EMPTY_OBJECT)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                        .awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("single-resource endpoints with geofenceId")
    class GeofenceResourceTests {

        static java.util.stream.Stream<Arguments> geofenceEndpoints() {
            BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> traccarGeofence  = (id, cfg) -> TRACCAR_PIP
                    .traccarGeofence((TextValue) id, cfg, secrets);
            BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> geofenceGeometry = (id, cfg) -> TRACCAR_PIP
                    .geofenceGeometry((TextValue) id, cfg, secrets);
            return Stream.of(arguments("traccarGeofence", traccarGeofence, TraccarSchemata.AREA),
                    arguments("geofenceGeometry", geofenceGeometry, "coordinates"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("geofenceEndpoints")
        @DisplayName("returns data for valid geofence")
        void whenValidGeofenceIdThenReturnsData(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(geofenceId1, config)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(((ObjectValue) v).containsKey(expectedKey)).isTrue());
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("geofenceEndpoints")
        @DisplayName("returns error for invalid geofence")
        void whenInvalidGeofenceIdThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(Value.of("invalid"), config)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("geofenceEndpoints")
        @DisplayName("returns error for invalid config")
        void whenInvalidConfigThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(geofenceId1, badConfig)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(15))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("geofenceEndpoints")
        @DisplayName("returns error for empty config")
        void whenEmptyConfigThenReturnsError(String name,
                BiFunction<Value, ObjectValue, io.sapl.api.stream.Stream<Value>> method, String expectedKey) {
            try (val stream = method.apply(geofenceId1, Value.EMPTY_OBJECT)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                        .awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("geographic containment")
    class ContainmentTests {

        @Test
        @DisplayName("position inside geofence returns true")
        void whenPositionInsideFenceThenContainsReturnsTrue() throws InterruptedException {
            Value position;
            try (val s = TRACCAR_PIP.position(deviceId, config, secrets)) {
                position = s.awaitNext();
            }
            Value fence;
            try (val s = TRACCAR_PIP.geofenceGeometry(geofenceId1, config, secrets)) {
                fence = s.awaitNext();
            }
            assertThat(position).isNotNull();
            assertThat(fence).isNotNull();
            val result = GeographicFunctionLibrary.contains((ObjectValue) fence, (ObjectValue) position);
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("position outside geofence returns false")
        void whenPositionOutsideFenceThenContainsReturnsFalse() throws InterruptedException {
            Value position;
            try (val s = TRACCAR_PIP.position(deviceId, config, secrets)) {
                position = s.awaitNext();
            }
            Value outsideFence;
            try (val s = TRACCAR_PIP.geofenceGeometry(Value.of("2"), config, secrets)) {
                outsideFence = s.awaitNext();
            }
            assertThat(position).isNotNull();
            assertThat(outsideFence).isNotNull();
            val result = GeographicFunctionLibrary.contains((ObjectValue) outsideFence, (ObjectValue) position);
            assertThat(result).isEqualTo(Value.FALSE);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCaseTests {

        @Mock
        private BlockingWebClient mockWebClient;

        @Test
        @DisplayName("traccarPosition returns error for empty array response")
        void whenEmptyArrayResponseThenReturnsError() {
            when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("[]")));
            val testPip      = new TraccarPolicyInformationPoint(mockWebClient);
            val testConfig   = (ObjectValue) json("""
                    {
                        "baseUrl": "http://test.de:8082"
                    }
                    """);
            val testSecrets  = secrets("email@address.org", "password");
            val someDeviceId = Value.of("someDeviceId");

            try (val stream = testPip.traccarPosition(someDeviceId, testConfig, testSecrets)) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
        }
    }
}
