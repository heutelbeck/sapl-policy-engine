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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.*;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.functions.geo.GeoJSONSchemata;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Policy Information Point for integrating with Traccar GPS tracking servers.
 * Provides attribute finders for fetching device positions, geofences, and
 * server metadata from Traccar servers.
 */
@RequiredArgsConstructor
@PolicyInformationPoint(name = TraccarPolicyInformationPoint.NAME, description = TraccarPolicyInformationPoint.DESCRIPTION, pipDocumentation = TraccarPolicyInformationPoint.DOCUMENTATION)
public class TraccarPolicyInformationPoint {

    public static final String TRACCAR_CONFIG = "TRACCAR_CONFIG";
    public static final String NAME           = "traccar";
    public static final String DESCRIPTION    = "This policy information point can fetch device positions and geofences from a traccar server.";
    public static final String DOCUMENTATION  = """
             This policy information point allows interaction with Traccar servers.
             [Traccar](https://www.traccar.org/) is a GPS tracking platform for monitoring the location of devices and
             managing geofences.

             This library enables fetching device positions as device attributes and geofence geometries as fence attributes.
             By integrating with the geographical function library (`geo`), this allows for policies that enforce geographical
             access control and geofencing. This library also allows direct access to Traccar-specific data within its schema,
             allowing to retrieve positions and geofences as GeoJSON objects for use with the operators of the `geo`
             function library.

             **Traccar Server Configuration**

             This library uses email and password authentication.
             A Traccar server configuration is a JSON object named `traccarConfiguration` containing the following attributes:
              - `baseUrl`: The base URL for constructing API requests. Example: `https://demo.traccar.org`.
              - `userName`: The email address used to authenticate to the Traccar Server.
              - `password`: The password to authenticate to the Traccar Server.
              - `pollingIntervalMs`: The interval, in milliseconds, between polling the Traccar server endpoint. Defaults to 1000ms.
              - `repetitions`: The maximum number of repeated requests. Defaults to `0x7fffffffffffffffL`.

             *** Example: ***

             ```json
             {
                 "baseUrl": "https://demo.traccar.org",
                 "userName": "email@address.org",
                 "password": "password",
                 "pollingIntervalMs": 250
             }
             ```

             All attribute finders of this library offer a variation with or without the `traccarConfiguration` as a parameter.
             If the parameter is not used in a policy, the attribute finder will default to the value of the environment variable
             `TRACCAR_CONFIG`.

             *** Examples: ***

              - `subject.device.<traccar.position>` will use the value of the environment variable `TRACCAR_CONFIG`
               to connect to the Traccar server.

              - Alternatively, a policy-specific set of settings can be used:
               ```sapl
               subject.device.<{
                                 "baseUrl": "https://demo.traccar.org",
                                 "userName": "email@address.org",
                                 "password": "password"
                               }>
               ```

              - `subject.device.<traccar.position(TRACCAR_SERVER_1)>` will use the value of the environment variable
               `TRACCAR_SERVER_1` to connect to the Traccar server.

            As a best practice, credentials should be stored in an environment variable and marked as secret to minimize
            the risk of exposing credentials.
            """;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ReactiveWebClient webClient;

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA, docs = """
            ```<traccar.server>``` is an environment attribute that retrieves server metadata from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Server/paths/~1server/get).
            It uses the value of the environment variable `TRACCAR_CONFIG` to connect to the server.

            **Example:**

            ```
            <traccar.server>
            ```

            This may return a value like:

            ```json
            {
              "id": 0,
              "registration": true,
              "readonly": true,
              "deviceReadonly": true,
              "limitCommands": true,
              "map": "string",
              "bingKey": "string",
              "mapUrl": "string",
              "poiLayer": "string",
              "latitude": 0,
              "longitude": 0,
              "zoom": 0,
              "version": "string",
              "forceSettings": true,
              "coordinateFormat": "string",
              "openIdEnabled": true,
              "openIdForce": true,
              "attributes": {}
            }
            ```
            """)
    public Flux<Value> server(Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return server((ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA, docs = """
            ```<traccar.server(traccarConfig)>``` is an environment attribute that retrieves server metadata from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Server/paths/~1server/get).
            It uses the settings provided in the `traccarConfig` parameter to connect to the server.

             **Parameters:**

            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            <traccar.server({
                              "baseUrl": "https://demo.traccar.org",
                              "userName": "email@address.org",
                              "password": "password"
                            })>
            ```

            This attribute may return a value like:

            ```json
            {
              "id": 0,
              "registration": true,
              "readonly": true,
              "deviceReadonly": true,
              "limitCommands": true,
              "map": "string",
              "bingKey": "string",
              "mapUrl": "string",
              "poiLayer": "string",
              "latitude": 0,
              "longitude": 0,
              "zoom": 0,
              "version": "string",
              "forceSettings": true,
              "coordinateFormat": "string",
              "openIdEnabled": true,
              "openIdForce": true,
              "attributes": {}
            }
            ```
            """)
    public Flux<Value> server(ObjectValue traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/server", traccarConfig))
                .distinct();
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA, docs = """
            ```<traccar.devices>``` is an environment attribute that retrieves a list of devices from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Devices/paths/~1devices/get).
            It uses the value of the environment variable `TRACCAR_CONFIG` to connect to the server.

             **Example:**

            ```
            <traccar.devices>
            ```

            This attribute may return a value like:
            ```json
            [
              {
                "id": 0,
                "name": "string",
                "uniqueId": "string",
                "status": "string",
                "disabled": true,
                "lastUpdate": "2019-08-24T14:15:22Z",
                "positionId": 0,
                "groupId": 0,
                "phone": "string",
                "model": "string",
                "contact": "string",
                "category": "string",
                "attributes": {}
              }
            ]
            ```
            """)
    public Flux<Value> devices(Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return devices((ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA, docs = """
            ```<traccar.devices(traccarConfig)>``` is an environment attribute that retrieves a list of devices from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Devices/paths/~1devices/get).
            It uses the settings provided in the `traccarConfig` parameter to connect to the server.

            **Parameters:**

             - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            <traccar.devices({
                              "baseUrl": "https://demo.traccar.org",
                              "userName": "email@address.org",
                              "password": "password"
                            })>
            ```

            This attribute may return a value like:
            ```json
            [
              {
                "id": 0,
                "name": "string",
                "uniqueId": "string",
                "status": "string",
                "disabled": true,
                "lastUpdate": "2019-08-24T14:15:22Z",
                "positionId": 0,
                "groupId": 0,
                "phone": "string",
                "model": "string",
                "contact": "string",
                "category": "string",
                "attributes": {}
              }
            ]
            ```
            """)
    public Flux<Value> devices(ObjectValue traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/devices", traccarConfig))
                .distinct();
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA, docs = """
            ```deviceEntityId.<traccar.device>``` is an attribute that fetches detailed metadata for a specific device from
            the Traccar server.
            The device is identified using the `deviceEntityId` parameter, which is the identifier of the device in Traccar,
            not the device's `uniqueId` in the database.
            This method uses the environment variable `TRACCAR_CONFIG` to retrieve the server connection configuration.

             **Parameters:**
             - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.

            **Example:**

            ```
            "12345".<traccar.device>
            ```

            This may return a value like:
            ```json
            {
                "id": 0,
                "name": "string",
                "uniqueId": "string",
                "status": "string",
                "disabled": true,
                "lastUpdate": "2019-08-24T14:15:22Z",
                "positionId": 0,
                "groupId": 0,
                "phone": "string",
                "model": "string",
                "contact": "string",
                "category": "string",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> device(TextValue deviceEntityId, Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return device(deviceEntityId, (ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA, docs = """
            ```deviceEntityId.<traccar.device(traccarConfig)>``` is an attribute that fetches detailed metadata for a specific device from
            the Traccar server.
            The device is identified using the `deviceEntityId` parameter, which is the identifier of the device in Traccar,
            not the device's `uniqueId` in the database.
            It uses the provided `traccarConfig` parameter to connect to the server.

             **Parameters:**
             - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
             - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            "12345".<traccar.device({
                              "baseUrl": "https://demo.traccar.org",
                              "userName": "email@address.org",
                              "password": "password"
                            })>
            ```

            This may return a value like:
            ```json
            {
                "id": 0,
                "name": "string",
                "uniqueId": "string",
                "status": "string",
                "disabled": true,
                "lastUpdate": "2019-08-24T14:15:22Z",
                "positionId": 0,
                "groupId": 0,
                "phone": "string",
                "model": "string",
                "contact": "string",
                "category": "string",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> device(TextValue deviceEntityId, ObjectValue traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET,
                requestSettingsFromTraccarConfig("/api/devices/%s".formatted(deviceEntityId.value()), traccarConfig))
                .distinct();
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA, docs = """
            ```<traccar.geofences>``` is an environment attribute that retrieves a list of all geofences from the Traccar server.
            It uses the value of the environment variable `TRACCAR_CONFIG` to connect to the server.

            **Example:**

            ```
            <traccar.geofences>
            ```

            This may return a value like:
            ```json
            [
                {
                    "id": 0,
                    "name": "string",
                    "description": "string",
                    "area": "string",
                    "attributes": {}
                }
            ]
            ```
            """)
    public Flux<Value> geofences(Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return geofences((ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA, docs = """
            ```<traccar.geofences(traccarConfig)>``` is an environment attribute that retrieves a list of all geofences from
            the Traccar server. It uses the provided `traccarConfig` parameter to connect to the server.

            **Parameters:**

            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            <traccar.geofences({
                "baseUrl": "https://demo.traccar.org",
                "userName": "email@address.org",
                "password": "password"
            })>
            ```

            This may return a value like:
            ```json
            [
                {
                    "id": 0,
                    "name": "string",
                    "description": "string",
                    "area": "string",
                    "attributes": {}
                }
            ]
            ```
            """)
    public Flux<Value> geofences(ObjectValue traccarConfig) {
        return webClient.httpRequest(HttpMethod.GET, requestSettingsFromTraccarConfig("/api/geofences", traccarConfig))
                .distinct();
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA, docs = """
            ```geofenceEntityId.<traccar.traccarGeofence>``` is an attribute that retrieves metadata for a specific geofence from
            the Traccar server using the provided geofence identifier. This method uses the environment variable
            `TRACCAR_CONFIG` to retrieve the server connection configuration.

            **Parameters:**
            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.

            **Example:**

            ```
            "12345".<traccar.traccarGeofence>
            ```

            This may return a value like:
            ```json
            {
                "id": 12345,
                "name": "Geofence A",
                "area": "Polygon",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> traccarGeofence(TextValue geofenceEntityId, Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return traccarGeofence(geofenceEntityId, (ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA, docs = """
            ```geofenceEntityId.<traccar.traccarGeofence(traccarConfig)>``` is an attribute that retrieves metadata for a specific
            geofence from the Traccar server using the provided geofence identifier and configuration.

            **Parameters:**
            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.
            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            "12345".<traccar.traccarGeofence({
                "baseUrl": "https://demo.traccar.org",
                "userName": "email@address.org",
                "password": "password"
            })>
            ```

            This may return a value like:
            ```json
            {
                "id": 12345,
                "name": "Geofence A",
                "area": "Polygon",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> traccarGeofence(TextValue geofenceEntityId, ObjectValue traccarConfig) {
        val config = requestSettingsFromTraccarConfig("/api/geofences/%s".formatted(geofenceEntityId.value()),
                traccarConfig);
        return webClient.httpRequest(HttpMethod.GET, config).distinct();
    }

    @Attribute(schema = GeoJSONSchemata.POLYGON, docs = """
            ```geofenceEntityId.<traccar.geofenceGeometry>``` is an attribute that converts geofence metadata into GeoJSON format
            for geometric representation. This method uses the environment variable `TRACCAR_CONFIG` to retrieve the
            server connection configuration.

            **Parameters:**
            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.

            **Example:**

            ```
            "12345".<traccar.geofenceGeometry>
            ```

            This may return a value like:
            ```json
            {
                "type": "Polygon",
                "coordinates": [
                    [
                        [102.0, 2.0],
                        [103.0, 2.0],
                        [103.0, 3.0],
                        [102.0, 3.0],
                        [102.0, 2.0]
                    ]
                ]
            }
            ```
            """)
    public Flux<Value> geofenceGeometry(TextValue geofenceEntityId, Map<String, Value> variables) {
        return traccarGeofence(geofenceEntityId, variables)
                .map(value -> TraccarFunctionLibrary.traccarGeofenceToGeoJson((ObjectValue) value)).distinct();
    }

    @Attribute(schema = GeoJSONSchemata.POLYGON, docs = """
            ```geofenceEntityId.<traccar.geofenceGeometry(traccarConfig)>``` is an attribute that converts geofence metadata into
            GeoJSON format for geometric representation. It uses the provided `traccarConfig` parameter to connect to the
            Traccar server.

            **Parameters:**

            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.
            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            "12345".<traccar.geofenceGeometry({
                "baseUrl": "https://demo.traccar.org",
                "userName": "email@address.org",
                "password": "password"
            })>
            ```

            This may return a value like:
            ```json
            {
                "type": "Polygon",
                "coordinates": [
                    [
                        [102.0, 2.0],
                        [103.0, 2.0],
                        [103.0, 3.0],
                        [102.0, 3.0],
                        [102.0, 2.0]
                    ]
                ]
            }
            ```
            """)
    public Flux<Value> geofenceGeometry(TextValue geofenceEntityId, ObjectValue traccarConfig) {
        return traccarGeofence(geofenceEntityId, traccarConfig)
                .map(value -> TraccarFunctionLibrary.traccarGeofenceToGeoJson((ObjectValue) value)).distinct();
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA, docs = """
            ```deviceEntityId.<traccar.traccarPosition>``` is an attribute that retrieves the most recent position of a specific
            device from the Traccar server. This method uses the environment variable `TRACCAR_CONFIG` to retrieve the
            server connection configuration.

            **Parameters:**
            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.

            **Example:**

            ```
            "12345".<traccar.traccarPosition>
            ```

            This may return a value like:
            ```json
            {
                "id": 0,
                "protocol": "string",
                "deviceId": 12345,
                "serverTime": "2019-08-24T14:15:22Z",
                "deviceTime": "2019-08-24T14:15:22Z",
                "fixTime": "2019-08-24T14:15:22Z",
                "valid": true,
                "latitude": 0,
                "longitude": 0,
                "altitude": 0,
                "speed": 0,
                "course": 0,
                "address": "string",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> traccarPosition(TextValue deviceEntityId, Map<String, Value> variables) {
        assertThatTraccarConfigurationIsPresentInEnvironmentVariables(variables);
        return traccarPosition(deviceEntityId, (ObjectValue) variables.get(TRACCAR_CONFIG));
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA, docs = """
            ```deviceEntityId.<traccar.traccarPosition(traccarConfig)>``` is an attribute that retrieves the most recent position of
            a specific device from the Traccar server using the provided `traccarConfig` parameter.

            **Parameters:**

            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            "12345".<traccar.traccarPosition({
                "baseUrl": "https://demo.traccar.org",
                "userName": "email@address.org",
                "password": "password"
            })>
            ```

            This may return a value like:
            ```json
            {
                "id": 0,
                "protocol": "string",
                "deviceId": 12345,
                "serverTime": "2019-08-24T14:15:22Z",
                "deviceTime": "2019-08-24T14:15:22Z",
                "fixTime": "2019-08-24T14:15:22Z",
                "valid": true,
                "latitude": 0,
                "longitude": 0,
                "altitude": 0,
                "speed": 0,
                "course": 0,
                "address": "string",
                "attributes": {}
            }
            ```
            """)
    public Flux<Value> traccarPosition(TextValue deviceEntityId, ObjectValue traccarConfig) {
        val deviceId = ValueJsonMarshaller.toJsonNode(deviceEntityId);
        val config   = requestSettingsFromTraccarConfig("/api/positions", traccarConfig, Map.of("deviceId", deviceId));
        return webClient.httpRequest(HttpMethod.GET, config)
                .map(TraccarPolicyInformationPoint::takeFirstElementFromArray).distinct();
    }

    private static Value takeFirstElementFromArray(Value maybeArray) {
        if (!(maybeArray instanceof ArrayValue array) || array.isEmpty()) {
            return Value.error("Bad response. Expected a non-empty array. but got: %s".formatted(maybeArray));
        }
        return array.getFirst();
    }

    @Attribute(schema = GeoJSONSchemata.POINT, docs = """
            ```deviceEntityId.<traccar.position>``` is an attribute that converts the most recent position of a specific device
            from the Traccar server into GeoJSON format. This method uses the environment variable `TRACCAR_CONFIG`
            to retrieve the server connection configuration.

            **Parameters:**
            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.

            **Example:**

            ```
            "12345".<traccar.position>
            ```

            This may return a value like:
            ```json
            {
                "type": "Point",
                "coordinates": [102.0, 0.5]
            }
            ```
            """)
    public Flux<Value> position(TextValue deviceEntityId, Map<String, Value> variables) {
        return traccarPosition(deviceEntityId, variables)
                .map(value -> TraccarFunctionLibrary.traccarPositionToGeoJSON((ObjectValue) value)).distinct();
    }

    @Attribute(schema = GeoJSONSchemata.POINT, docs = """
            ```deviceEntityId.<traccar.position(traccarConfig)>``` is an attribute that converts the most recent position of a
            specific device from the Traccar server into GeoJSON format using the provided `traccarConfig` parameter.

            **Parameters:**
            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
            - `traccarConfig` *(Object)*: A JSON object containing the configuration to connect to the Traccar server.

            **Example:**

            ```
            "12345".<traccar.position({
                "baseUrl": "https://demo.traccar.org",
                "userName": "email@address.org",
                "password": "password"
            })>
            ```

            This may return a value like:
            ```json
            {
                "type": "Point",
                "coordinates": [102.0, 0.5]
            }
            ```
            """)
    public Flux<Value> position(TextValue deviceEntityId, ObjectValue traccarConfig) {
        return traccarPosition(deviceEntityId, traccarConfig)
                .map(value -> TraccarFunctionLibrary.traccarPositionToGeoJSON((ObjectValue) value)).distinct();
    }

    private ObjectValue requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig) {
        return requestSettingsFromTraccarConfig(path, traccarConfig, Map.of());
    }

    private ObjectValue requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig,
            Map<String, JsonNode> queryParameters) {
        val requestSettings = JSON.objectNode();
        requestSettings.set(ReactiveWebClient.BASE_URL, getRequiredProperty(ReactiveWebClient.BASE_URL, traccarConfig));
        requestSettings.set(ReactiveWebClient.PATH, JSON.textNode(path));
        requestSettings.set(ReactiveWebClient.ACCEPT_MEDIATYPE, JSON.textNode(MediaType.APPLICATION_JSON_VALUE));

        val headersWithBasicAuth = JSON.objectNode();
        headersWithBasicAuth.set(HttpHeaders.AUTHORIZATION, createBasicAuthHeader(traccarConfig));

        requestSettings.set(ReactiveWebClient.HEADERS, headersWithBasicAuth);

        val config = ValueJsonMarshaller.toJsonNode(traccarConfig);
        if (config.has(ReactiveWebClient.POLLING_INTERVAL)) {
            requestSettings.set(ReactiveWebClient.POLLING_INTERVAL, config.get(ReactiveWebClient.POLLING_INTERVAL));
        }

        if (config.has(ReactiveWebClient.REPEAT_TIMES)) {
            requestSettings.set(ReactiveWebClient.REPEAT_TIMES, config.get(ReactiveWebClient.REPEAT_TIMES));
        }

        if (!queryParameters.isEmpty()) {
            val queryParams = JSON.objectNode();
            for (val parameter : queryParameters.entrySet()) {
                queryParams.set(parameter.getKey(), parameter.getValue());
            }
            requestSettings.set(ReactiveWebClient.URL_PARAMS, queryParams);
        }

        return (ObjectValue) ValueJsonMarshaller.fromJsonNode(requestSettings);
    }

    /**
     * Creates a Basic Authentication header value from the traccar configuration.
     *
     * @param traccarConfig the traccar configuration containing userName and
     * password
     * @return the Base64-encoded Basic Auth header value
     */
    public static TextNode createBasicAuthHeader(ObjectValue traccarConfig) {
        val userName           = getRequiredProperty("userName", traccarConfig).asText();
        val password           = getRequiredProperty("password", traccarConfig).asText();
        val credentials        = userName + ':' + password;
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return JSON.textNode("Basic " + encodedCredentials);
    }

    private static JsonNode getRequiredProperty(String fieldName, ObjectValue traccarConfig) {
        if (!traccarConfig.containsKey(fieldName)) {
            throw new IllegalArgumentException(
                    "Required field '%s' missing from traccar configuration.".formatted(fieldName));
        }
        val value = traccarConfig.get(fieldName);
        if (value instanceof TextValue textValue) {
            return JSON.textNode(textValue.value());
        } else if (value instanceof NumberValue numberValue) {
            return JSON.numberNode(numberValue.value());
        }
        return ValueJsonMarshaller.toJsonNode(value);
    }

    private void assertThatTraccarConfigurationIsPresentInEnvironmentVariables(Map<String, Value> variables) {
        if (!variables.containsKey(TRACCAR_CONFIG)) {
            throw new IllegalArgumentException(
                    "Cannot connect to Traccar server. The environment variable TRACCAR_CONFIG is undefined.");
        }
    }

}
