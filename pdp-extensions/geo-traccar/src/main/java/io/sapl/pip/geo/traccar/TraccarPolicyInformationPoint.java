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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeAccessContext;
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

import java.math.BigDecimal;
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

    static final String ERROR_BAD_RESPONSE_EXPECTED_ARRAY = "Bad response. Expected a non-empty array, but got: %s.";
    static final String ERROR_REQUIRED_FIELD_MISSING      = "Required field '%s' missing from traccar configuration.";
    static final String ERROR_TRACCAR_CONFIG_NOT_OBJECT   = "TRACCAR_CONFIG must be an object, but was: %s";
    static final String ERROR_TRACCAR_CONFIG_UNDEFINED    = "Cannot connect to Traccar server. The environment variable TRACCAR_CONFIG is undefined.";
    static final String ERROR_TRACCAR_CREDENTIALS_MISSING = "No Traccar credentials found in pdpSecrets. Configure secrets.traccar with 'token' or 'userName'/'password'.";

    private static final String SECRETS_TRACCAR  = "traccar";
    private static final String SECRETS_TOKEN    = "token";
    private static final String SECRETS_USERNAME = "userName";
    private static final String SECRETS_PASSWORD = "password";
    public static final String  DOCUMENTATION    = """
             This policy information point allows interaction with a single
             [Traccar](https://www.traccar.org/) GPS tracking server, fetching device positions,
             geofences, and server metadata.

             By integrating with the geographical function library (`geo`), this allows for
             policies that enforce geographical access control and geofencing. The library
             provides both Traccar-native data (positions, geofences in Traccar schema) and
             GeoJSON-converted data for use with the `geo` function library operators.

             ## Available Attributes

             Environment attributes (no left-hand operand):

             | Attribute | Description |
             |---|---|
             | `<traccar.server>` | Traccar server metadata |
             | `<traccar.devices>` | List of all devices |
             | `<traccar.geofences>` | List of all geofences |

             Left-hand operand attributes (entity ID on the left):

             | Attribute | Description |
             |---|---|
             | `deviceId.<traccar.device>` | Single device metadata |
             | `deviceId.<traccar.traccarPosition>` | Most recent position (Traccar schema) |
             | `deviceId.<traccar.position>` | Most recent position (GeoJSON) |
             | `geofenceId.<traccar.traccarGeofence>` | Single geofence metadata (Traccar schema) |
             | `geofenceId.<traccar.geofenceGeometry>` | Single geofence geometry (GeoJSON) |

             Every attribute has two variations:
             * Without parameter: uses the `TRACCAR_CONFIG` environment variable.
             * With `traccarConfig` parameter: uses the inline configuration object.

             Both variations use the same `secrets.traccar` credentials (see below).

             ## Server Configuration

             The Traccar server configuration is a JSON object with non-sensitive connection
             settings. It does not contain any credentials.

             Configuration fields:
             * `baseUrl` (required): The base URL of the Traccar server.
             * `pollingIntervalMs` (optional): Interval in milliseconds between polling requests.
               Defaults to 1000.
             * `repetitions` (optional): Maximum number of repeated polling requests. Defaults to
               `Long.MAX_VALUE`.

             The configuration is provided via the `TRACCAR_CONFIG` environment variable in
             `pdp.json`:
             ```json
             {
               "variables": {
                 "TRACCAR_CONFIG": {
                   "baseUrl": "https://demo.traccar.org",
                   "pollingIntervalMs": 250
                 }
               }
             }
             ```

             This PIP connects to a single Traccar server. There is no multi-server support.
             All attribute finders -- whether invoked with or without the inline `traccarConfig`
             parameter -- authenticate against the same `secrets.traccar` credentials.

             ## Secrets Configuration

             Credentials are sourced exclusively from the `secrets` section in `pdp.json`. They
             are never read from the `TRACCAR_CONFIG` variable, inline configuration parameters,
             or any other policy-visible source. Even if a `traccarConfig` object contains
             `userName` or `password` fields, they are ignored for authentication.

             There is a single set of Traccar credentials per PDP. All attribute finders use
             the same `secrets.traccar` entry regardless of whether they use `TRACCAR_CONFIG`
             or an inline configuration parameter.

             Two authentication methods are supported:

             **API token authentication (recommended for Traccar 6.x):**

             The token is passed as a `?token=` query parameter on every API request.
             ```json
             { "secrets": { "traccar": { "token": "YOUR_API_TOKEN" } } }
             ```

             **Basic authentication with email and password:**

             An `Authorization: Basic ...` header is added to every API request.
             ```json
             { "secrets": { "traccar": { "userName": "email@address.org", "password": "password" } } }
             ```

             Credential resolution:
             1. If `secrets.traccar.token` is present, use token authentication.
             2. Otherwise, if `secrets.traccar.userName` is present, use basic authentication.
             3. If neither is present, the attribute returns an error.

             If both `token` and `userName`/`password` are present, token authentication takes
             precedence.

             ## Attribute Invocation and Resolution

             **Without parameter** (uses `TRACCAR_CONFIG` environment variable):
             ```sapl
             policy "check_server"
             permit
               <traccar.server>.version == "6.7";
             ```
             Resolution: reads `TRACCAR_CONFIG` from `ctx.variables()`, reads credentials from
             `ctx.pdpSecrets().traccar`, makes the API call.

             **With inline config** (overrides connection settings only):
             ```sapl
             policy "check_position"
             permit
               subject.device.<traccar.position({
                                "baseUrl": "https://other.traccar.org",
                                "pollingIntervalMs": 500
                              })>;
             ```
             Resolution: uses the inline object for `baseUrl` and `pollingIntervalMs`, but
             credentials still come from `ctx.pdpSecrets().traccar` -- the same single set of
             secrets. The inline object cannot override authentication.

             ## Complete pdp.json Example

             ```json
             {
               "variables": {
                 "TRACCAR_CONFIG": {
                   "baseUrl": "https://traccar.example.com",
                   "pollingIntervalMs": 1000
                 }
               },
               "secrets": {
                 "traccar": { "token": "YOUR_API_TOKEN" }
               }
             }
             ```

             With this configuration:
             * `<traccar.devices>` fetches devices from `traccar.example.com` using the API token.
             * `"42".<traccar.position>` fetches the position of device 42 from the same server.
             * `"42".<traccar.position({ "baseUrl": "https://other.traccar.org" })>` fetches
               from a different server, but still authenticates with the same API token from
               `secrets.traccar`.

             ## Geofencing Example

             ```sapl
             policy "geofence_check"
             permit
               var position = subject.device.<traccar.position>;
               var fence    = subject.geofence.<traccar.geofenceGeometry>;
               geo.contains(fence, position);
             ```
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
    public Flux<Value> server(AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return server(getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                              "pollingIntervalMs": 500
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
        return server(traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> server(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/server", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError).distinct();
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
    public Flux<Value> devices(AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return devices(getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                              "pollingIntervalMs": 500
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
        return devices(traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> devices(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/devices", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError).distinct();
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
    public Flux<Value> device(TextValue deviceEntityId, AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return device(deviceEntityId, getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                              "pollingIntervalMs": 500
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
        return device(deviceEntityId, traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> device(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/devices/%s".formatted(deviceEntityId.value()),
                traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError).distinct();
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
    public Flux<Value> geofences(AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return geofences(getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                "pollingIntervalMs": 250
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
        return geofences(traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> geofences(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/geofences", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError).distinct();
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
    public Flux<Value> traccarGeofence(TextValue geofenceEntityId, AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return traccarGeofence(geofenceEntityId, getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                "pollingIntervalMs": 250
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
        return traccarGeofence(geofenceEntityId, traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> traccarGeofence(TextValue geofenceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/geofences/%s".formatted(geofenceEntityId.value()),
                traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError).distinct();
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
    public Flux<Value> geofenceGeometry(TextValue geofenceEntityId, AttributeAccessContext ctx) {
        return traccarGeofence(geofenceEntityId, ctx).map(value -> value instanceof ErrorValue ? value
                : TraccarFunctionLibrary.traccarGeofenceToGeoJson((ObjectValue) value)).distinct();
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
                "pollingIntervalMs": 250
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
        return geofenceGeometry(geofenceEntityId, traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> geofenceGeometry(TextValue geofenceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return traccarGeofence(geofenceEntityId, traccarConfig, pdpSecrets)
                .map(value -> value instanceof ErrorValue ? value
                        : TraccarFunctionLibrary.traccarGeofenceToGeoJson((ObjectValue) value))
                .distinct();
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
    public Flux<Value> traccarPosition(TextValue deviceEntityId, AttributeAccessContext ctx) {
        val errorFlux = getErrorFluxIfTraccarConfigInvalid(ctx.variables());
        if (errorFlux != null) {
            return errorFlux;
        }
        return traccarPosition(deviceEntityId, getTraccarConfig(ctx.variables()), ctx.pdpSecrets());
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
                "pollingIntervalMs": 250
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
        return traccarPosition(deviceEntityId, traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> traccarPosition(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val deviceId        = ValueJsonMarshaller.toJsonNode(deviceEntityId);
        val settingsOrError = requestSettingsFromTraccarConfig("/api/positions", traccarConfig, pdpSecrets,
                Map.of("deviceId", deviceId));
        if (settingsOrError instanceof ErrorValue) {
            return Flux.just(settingsOrError);
        }
        return webClient.httpRequest(HttpMethod.GET, (ObjectValue) settingsOrError)
                .map(TraccarPolicyInformationPoint::takeFirstElementFromArray).distinct();
    }

    private static Value takeFirstElementFromArray(Value maybeArray) {
        if (!(maybeArray instanceof ArrayValue array) || array.isEmpty()) {
            return Value.error(ERROR_BAD_RESPONSE_EXPECTED_ARRAY.formatted(maybeArray));
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
    public Flux<Value> position(TextValue deviceEntityId, AttributeAccessContext ctx) {
        return traccarPosition(deviceEntityId, ctx).map(value -> value instanceof ErrorValue ? value
                : TraccarFunctionLibrary.traccarPositionToGeoJSON((ObjectValue) value)).distinct();
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
                "pollingIntervalMs": 250
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
        return position(deviceEntityId, traccarConfig, Value.EMPTY_OBJECT);
    }

    Flux<Value> position(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return traccarPosition(deviceEntityId, traccarConfig, pdpSecrets)
                .map(value -> value instanceof ErrorValue ? value
                        : TraccarFunctionLibrary.traccarPositionToGeoJSON((ObjectValue) value))
                .distinct();
    }

    private Value requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return requestSettingsFromTraccarConfig(path, traccarConfig, pdpSecrets, Map.of());
    }

    private Value requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig, ObjectValue pdpSecrets,
            Map<String, JsonNode> queryParameters) {
        val baseUrl = getRequiredProperty(ReactiveWebClient.BASE_URL, traccarConfig);
        if (baseUrl instanceof ErrorValue) {
            return baseUrl;
        }

        val traccarSecrets = resolveTraccarSecrets(pdpSecrets);

        val requestSettings = JSON.objectNode();
        requestSettings.set(ReactiveWebClient.BASE_URL, toJsonNode(baseUrl));
        requestSettings.set(ReactiveWebClient.ACCEPT_MEDIATYPE, JSON.stringNode(MediaType.APPLICATION_JSON_VALUE));

        val effectiveQueryParams = JSON.objectNode();
        for (val parameter : queryParameters.entrySet()) {
            effectiveQueryParams.set(parameter.getKey(), parameter.getValue());
        }

        if (traccarSecrets != null && traccarSecrets.containsKey(SECRETS_TOKEN)) {
            val token = ((TextValue) traccarSecrets.get(SECRETS_TOKEN)).value();
            effectiveQueryParams.set(SECRETS_TOKEN, JSON.stringNode(token));
            requestSettings.set(ReactiveWebClient.PATH, JSON.stringNode(path));
        } else if (traccarSecrets != null && traccarSecrets.containsKey(SECRETS_USERNAME)) {
            val authHeaderOrError = createBasicAuthHeader(traccarSecrets);
            if (authHeaderOrError instanceof ErrorValue) {
                return authHeaderOrError;
            }
            val headers = JSON.objectNode();
            headers.set(HttpHeaders.AUTHORIZATION, toJsonNode(authHeaderOrError));
            requestSettings.set(ReactiveWebClient.HEADERS, headers);
            requestSettings.set(ReactiveWebClient.PATH, JSON.stringNode(path));
        } else {
            return Value.error(ERROR_TRACCAR_CREDENTIALS_MISSING);
        }

        if (!effectiveQueryParams.isEmpty()) {
            requestSettings.set(ReactiveWebClient.URL_PARAMS, effectiveQueryParams);
        }

        val config = ValueJsonMarshaller.toJsonNode(traccarConfig);
        if (config.has(ReactiveWebClient.POLLING_INTERVAL)) {
            requestSettings.set(ReactiveWebClient.POLLING_INTERVAL, config.get(ReactiveWebClient.POLLING_INTERVAL));
        }

        if (config.has(ReactiveWebClient.REPEAT_TIMES)) {
            requestSettings.set(ReactiveWebClient.REPEAT_TIMES, config.get(ReactiveWebClient.REPEAT_TIMES));
        }

        return ValueJsonMarshaller.fromJsonNode(requestSettings);
    }

    private static ObjectValue resolveTraccarSecrets(ObjectValue pdpSecrets) {
        if (pdpSecrets == null || pdpSecrets.isEmpty()) {
            return null;
        }
        val traccarSecretsValue = pdpSecrets.get(SECRETS_TRACCAR);
        if (traccarSecretsValue instanceof ObjectValue traccarSecrets) {
            return traccarSecrets;
        }
        return null;
    }

    /**
     * Creates a Basic Authentication header value from the traccar secrets.
     *
     * @param traccarSecrets the traccar secrets containing userName and password
     * @return the Base64-encoded Basic Auth header as TextValue, or ErrorValue if
     * required fields are missing
     */
    public static Value createBasicAuthHeader(ObjectValue traccarSecrets) {
        val userName = getRequiredProperty("userName", traccarSecrets);
        if (userName instanceof ErrorValue) {
            return userName;
        }
        val password = getRequiredProperty("password", traccarSecrets);
        if (password instanceof ErrorValue) {
            return password;
        }
        val credentials        = ((TextValue) userName).value() + ':' + ((TextValue) password).value();
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return Value.of("Basic " + encodedCredentials);
    }

    private static Value getRequiredProperty(String fieldName, ObjectValue traccarConfig) {
        if (!traccarConfig.containsKey(fieldName)) {
            return Value.error(ERROR_REQUIRED_FIELD_MISSING.formatted(fieldName));
        }
        return traccarConfig.get(fieldName);
    }

    private static JsonNode toJsonNode(Value value) {
        if (value instanceof TextValue(String textValue)) {
            return JSON.stringNode(textValue);
        } else if (value instanceof NumberValue(BigDecimal numberValue)) {
            return JSON.numberNode(numberValue);
        }
        return ValueJsonMarshaller.toJsonNode(value);
    }

    private static Flux<Value> getErrorFluxIfTraccarConfigInvalid(ObjectValue variables) {
        val config = variables.get(TRACCAR_CONFIG);
        if (config == null || config instanceof UndefinedValue) {
            return Flux.just(Value.error(ERROR_TRACCAR_CONFIG_UNDEFINED));
        }
        if (!(config instanceof ObjectValue)) {
            return Flux.just(Value.error(ERROR_TRACCAR_CONFIG_NOT_OBJECT.formatted(config.getClass().getSimpleName())));
        }
        return null;
    }

    private static ObjectValue getTraccarConfig(ObjectValue variables) {
        return (ObjectValue) variables.get(TRACCAR_CONFIG);
    }

}
