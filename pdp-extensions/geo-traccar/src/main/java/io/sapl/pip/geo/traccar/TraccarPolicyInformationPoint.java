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
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.functions.geo.GeoJSONSchemata;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    static final String ERROR_CREDENTIAL_NOT_TEXT         = "Traccar credentials must be text values.";
    static final String ERROR_INSECURE_SCHEME             = "Traccar server '%s' baseUrl does not use https. Set 'allowInsecureHttp': true on that server entry to permit insecure transport.";
    static final String ERROR_REQUIRED_FIELD_MISSING      = "Required field '%s' missing from traccar configuration.";
    static final String ERROR_SERVER_NOT_FOUND            = "No Traccar server named '%s' is configured in TRACCAR_CONFIG.";
    static final String ERROR_TRACCAR_CONFIG_NOT_OBJECT   = "TRACCAR_CONFIG must be an object, but was: %s";
    static final String ERROR_TRACCAR_CONFIG_UNDEFINED    = "Cannot connect to Traccar server. The environment variable TRACCAR_CONFIG is undefined.";
    static final String ERROR_TRACCAR_CREDENTIALS_MISSING = "No Traccar credentials found in pdpSecrets. Configure secrets.traccar.<serverName> (or flat secrets.traccar) with 'token' or 'userName'/'password'.";
    static final String ERROR_UNEXPECTED_RESPONSE         = "Unexpected Traccar response. Expected a JSON object.";

    private static final String CONFIG_ALLOW_INSECURE_HTTP = "allowInsecureHttp";
    private static final String CONFIG_DEFAULT_SERVER_NAME = "defaultServerName";
    private static final String CONFIG_NAME                = "name";
    private static final String CONFIG_SERVERS             = "servers";
    private static final String DEFAULT_SERVER_NAME        = "default";
    private static final String HTTPS_SCHEME               = "https";

    private static final String SECRETS_TRACCAR  = "traccar";
    private static final String SECRETS_TOKEN    = "token";
    private static final String SECRETS_USERNAME = "userName";
    private static final String SECRETS_PASSWORD = "password";

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String MEDIATYPE_JSON       = "application/json";
    public static final String  DOCUMENTATION        = """
             This policy information point fetches device positions, geofences, and server
             metadata from operator-defined [Traccar](https://www.traccar.org/) GPS tracking
             servers.

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
             * Without parameter: uses the operator's default server from `TRACCAR_CONFIG`.
             * With `serverName` parameter: selects the named operator server from `TRACCAR_CONFIG`.

             A policy selects a server only by name. It can never supply a `baseUrl` or any
             connection object. The destination host, transport policy, and credentials are
             bound by the operator per server.

             ## Server Configuration

             The Traccar server configuration is a JSON object provided via the `TRACCAR_CONFIG`
             environment variable in `pdp.json`. It holds an operator-defined dictionary of
             named servers and never contains credentials.

             New form (named servers):
             ```json
             {
               "variables": {
                 "TRACCAR_CONFIG": {
                   "defaultServerName": "prod",
                   "servers": [
                     { "name": "prod", "baseUrl": "https://traccar.example.com" },
                     { "name": "lab",  "baseUrl": "http://localhost:8082", "allowInsecureHttp": true }
                   ]
                 }
               }
             }
             ```

             Per-server fields:
             * `name` (required): server identifier used for selection and secrets matching.
             * `baseUrl` (required): the base URL of that Traccar server.
             * `allowInsecureHttp` (optional, default `false`): when the `baseUrl` scheme is not
               `https`, the attribute returns an error unless this is `true` for that server.

             Back-compatible single-server form (treated as the implicit default server):
             ```json
             {
               "variables": {
                 "TRACCAR_CONFIG": { "baseUrl": "https://traccar.example.com" }
               }
             }
             ```

             Refresh cadence is not a configuration field. Like every streaming attribute, the
             engine re-evaluates this attribute on its own schedule via the `pollIntervalMs`
             attribute option (see Functions and Attributes).

             ## Secrets Configuration

             Credentials are sourced exclusively from the `secrets` section in `pdp.json`. They
             are never read from `TRACCAR_CONFIG` or any policy-visible source.

             The server `name` is the join key. For a server named `prod`, the PDP looks up
             `secrets.traccar.prod`. A flat `secrets.traccar` (no per-server nesting) is used as
             the default server's credentials for back-compatibility.

             Two authentication methods are supported per server:

             **API token authentication (recommended for Traccar 6.x):**
             The token is passed as a `?token=` query parameter on every API request.
             ```json
             { "secrets": { "traccar": { "prod": { "token": "YOUR_API_TOKEN" } } } }
             ```

             **Basic authentication with email and password:**
             An `Authorization: Basic ...` header is added to every API request.
             ```json
             { "secrets": { "traccar": { "prod": { "userName": "email@address.org", "password": "password" } } } }
             ```

             Per-server credential resolution:
             1. If `secrets.traccar.<name>.token` is present, use token authentication.
             2. Otherwise, if `secrets.traccar.<name>.userName` is present, use basic authentication.
             3. If no per-server entry matches, fall back to flat `secrets.traccar`.
             4. If neither is present, the attribute returns an error.

             If both `token` and `userName`/`password` are present, token authentication takes
             precedence.

             ## Attribute Invocation and Resolution

             **Without parameter** (uses the default server from `TRACCAR_CONFIG`):
             ```sapl
             policy "check_server"
             permit
               <traccar.server>.version == "6.7";
             ```

             **With server name** (selects a named operator server):
             ```sapl
             policy "check_position"
             permit
               subject.device.<traccar.position("lab")[{pollIntervalMs: 250}]>;
             ```
             Resolution: looks up the `lab` server in `TRACCAR_CONFIG.servers`, reads credentials
             from `secrets.traccar.lab`, makes the API call. The `[{pollIntervalMs: 250}]`
             attribute option sets how often the engine re-evaluates the attribute and is optional.

             ## Complete pdp.json Example

             ```json
             {
               "variables": {
                 "TRACCAR_CONFIG": {
                   "defaultServerName": "prod",
                   "servers": [
                     { "name": "prod", "baseUrl": "https://traccar.example.com" }
                   ]
                 }
               },
               "secrets": {
                 "traccar": { "prod": { "token": "YOUR_API_TOKEN" } }
               }
             }
             ```

             With this configuration:
             * `<traccar.devices>` fetches devices from `traccar.example.com` using the `prod` token.
             * `"42".<traccar.position>` fetches the position of device 42 from the same server.
             * `"42".<traccar.position("prod")>` selects the `prod` server explicitly.

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

    private final BlockingWebClient webClient;

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
    public Stream<Value> server(AttributeAccessContext ctx) {
        return withCtx(ctx, this::server);
    }

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA, docs = """
            ```<traccar.server(serverName)>``` is an environment attribute that retrieves server metadata from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Server/paths/~1server/get).
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

             **Parameters:**

            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            <traccar.server("prod")>
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
    public Stream<Value> server(AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, this::server);
    }

    Stream<Value> server(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/server", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(webClient.httpRequest("GET", (ObjectValue) settingsOrError));
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
    public Stream<Value> devices(AttributeAccessContext ctx) {
        return withCtx(ctx, this::devices);
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA, docs = """
            ```<traccar.devices(serverName)>``` is an environment attribute that retrieves a list of devices from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Devices/paths/~1devices/get).
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**

             - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            <traccar.devices("prod")>
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
    public Stream<Value> devices(AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, this::devices);
    }

    Stream<Value> devices(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/devices", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(webClient.httpRequest("GET", (ObjectValue) settingsOrError));
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
    public Stream<Value> device(TextValue deviceEntityId, AttributeAccessContext ctx) {
        return withCtx(ctx, (config, secrets) -> device(deviceEntityId, config, secrets));
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA, docs = """
            ```deviceEntityId.<traccar.device(serverName)>``` is an attribute that fetches detailed metadata for a specific device from
            the Traccar server.
            The device is identified using the `deviceEntityId` parameter, which is the identifier of the device in Traccar,
            not the device's `uniqueId` in the database.
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

             **Parameters:**
             - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
             - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            "12345".<traccar.device("prod")>
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
    public Stream<Value> device(TextValue deviceEntityId, AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, (config, secrets) -> device(deviceEntityId, config, secrets));
    }

    Stream<Value> device(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig(
                "/api/devices/%s".formatted(encodePathSegment(deviceEntityId.value())), traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(webClient.httpRequest("GET", (ObjectValue) settingsOrError));
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
    public Stream<Value> geofences(AttributeAccessContext ctx) {
        return withCtx(ctx, this::geofences);
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA, docs = """
            ```<traccar.geofences(serverName)>``` is an environment attribute that retrieves a list of all geofences from
            the Traccar server. It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**

            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            <traccar.geofences("prod")>
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
    public Stream<Value> geofences(AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, this::geofences);
    }

    Stream<Value> geofences(ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig("/api/geofences", traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(webClient.httpRequest("GET", (ObjectValue) settingsOrError));
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
    public Stream<Value> traccarGeofence(TextValue geofenceEntityId, AttributeAccessContext ctx) {
        return withCtx(ctx, (config, secrets) -> traccarGeofence(geofenceEntityId, config, secrets));
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA, docs = """
            ```geofenceEntityId.<traccar.traccarGeofence(serverName)>``` is an attribute that retrieves metadata for a specific
            geofence from the Traccar server using the provided geofence identifier.
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**
            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.
            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            "12345".<traccar.traccarGeofence("prod")>
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
    public Stream<Value> traccarGeofence(TextValue geofenceEntityId, AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, (config, secrets) -> traccarGeofence(geofenceEntityId, config, secrets));
    }

    Stream<Value> traccarGeofence(TextValue geofenceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val settingsOrError = requestSettingsFromTraccarConfig(
                "/api/geofences/%s".formatted(encodePathSegment(geofenceEntityId.value())), traccarConfig, pdpSecrets);
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(webClient.httpRequest("GET", (ObjectValue) settingsOrError));
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
    public Stream<Value> geofenceGeometry(TextValue geofenceEntityId, AttributeAccessContext ctx) {
        return Streams.distinctUntilChanged(Streams.map(traccarGeofence(geofenceEntityId, ctx),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarGeofenceToGeoJson)));
    }

    @Attribute(schema = GeoJSONSchemata.POLYGON, docs = """
            ```geofenceEntityId.<traccar.geofenceGeometry(serverName)>``` is an attribute that converts geofence metadata into
            GeoJSON format for geometric representation.
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**

            - `geofenceEntityId` *(Text)*: The identifier of the geofence in the Traccar system.
            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            "12345".<traccar.geofenceGeometry("prod")>
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
    public Stream<Value> geofenceGeometry(TextValue geofenceEntityId, AttributeAccessContext ctx,
            TextValue serverName) {
        return withCtx(ctx, serverName, (config, secrets) -> geofenceGeometry(geofenceEntityId, config, secrets));
    }

    Stream<Value> geofenceGeometry(TextValue geofenceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return Streams.distinctUntilChanged(Streams.map(traccarGeofence(geofenceEntityId, traccarConfig, pdpSecrets),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarGeofenceToGeoJson)));
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
    public Stream<Value> traccarPosition(TextValue deviceEntityId, AttributeAccessContext ctx) {
        return withCtx(ctx, (config, secrets) -> traccarPosition(deviceEntityId, config, secrets));
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA, docs = """
            ```deviceEntityId.<traccar.traccarPosition(serverName)>``` is an attribute that retrieves the most recent position of
            a specific device from the Traccar server.
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**

            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            "12345".<traccar.traccarPosition("prod")>
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
    public Stream<Value> traccarPosition(TextValue deviceEntityId, AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, (config, secrets) -> traccarPosition(deviceEntityId, config, secrets));
    }

    Stream<Value> traccarPosition(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        val deviceId        = ValueJsonMarshaller.toJsonNode(deviceEntityId);
        val settingsOrError = requestSettingsFromTraccarConfig("/api/positions", traccarConfig, pdpSecrets,
                Map.of("deviceId", deviceId));
        if (settingsOrError instanceof ErrorValue) {
            return Streams.just(settingsOrError);
        }
        return Streams.distinctUntilChanged(Streams.map(webClient.httpRequest("GET", (ObjectValue) settingsOrError),
                TraccarPolicyInformationPoint::takeFirstElementFromArray));
    }

    private static Value takeFirstElementFromArray(Value maybeArray) {
        if (maybeArray instanceof ErrorValue) {
            return maybeArray;
        }
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
    public Stream<Value> position(TextValue deviceEntityId, AttributeAccessContext ctx) {
        return Streams.distinctUntilChanged(Streams.map(traccarPosition(deviceEntityId, ctx),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarPositionToGeoJSON)));
    }

    @Attribute(schema = GeoJSONSchemata.POINT, docs = """
            ```deviceEntityId.<traccar.position(serverName)>``` is an attribute that converts the most recent position of a
            specific device from the Traccar server into GeoJSON format.
            It selects the named operator server from the `TRACCAR_CONFIG` environment variable.

            **Parameters:**
            - `deviceEntityId` *(Text)*: The identifier of the device in the Traccar system.
            - `serverName` *(Text)*: The name of the operator-configured Traccar server.

            **Example:**

            ```
            "12345".<traccar.position("prod")>
            ```

            This may return a value like:
            ```json
            {
                "type": "Point",
                "coordinates": [102.0, 0.5]
            }
            ```
            """)
    public Stream<Value> position(TextValue deviceEntityId, AttributeAccessContext ctx, TextValue serverName) {
        return withCtx(ctx, serverName, (config, secrets) -> position(deviceEntityId, config, secrets));
    }

    Stream<Value> position(TextValue deviceEntityId, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return Streams.distinctUntilChanged(Streams.map(traccarPosition(deviceEntityId, traccarConfig, pdpSecrets),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarPositionToGeoJSON)));
    }

    private Value requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig, ObjectValue pdpSecrets) {
        return requestSettingsFromTraccarConfig(path, traccarConfig, pdpSecrets, Map.of());
    }

    private Value requestSettingsFromTraccarConfig(String path, ObjectValue traccarConfig, ObjectValue pdpSecrets,
            Map<String, JsonNode> queryParameters) {
        val baseUrl = getRequiredProperty(BlockingWebClient.BASE_URL, traccarConfig);
        if (baseUrl instanceof ErrorValue) {
            return baseUrl;
        }

        val schemeGate = enforceTransportSecurity(baseUrl, traccarConfig);
        if (schemeGate instanceof ErrorValue) {
            return schemeGate;
        }

        val traccarSecrets = resolveTraccarSecrets(traccarConfig, pdpSecrets);

        val requestSettings = JSON.objectNode();
        requestSettings.set(BlockingWebClient.BASE_URL, toJsonNode(baseUrl));
        requestSettings.set(BlockingWebClient.ACCEPT_MEDIATYPE, JSON.stringNode(MEDIATYPE_JSON));

        val effectiveQueryParams = JSON.objectNode();
        for (val parameter : queryParameters.entrySet()) {
            effectiveQueryParams.set(parameter.getKey(), parameter.getValue());
        }

        if (traccarSecrets.get(SECRETS_TOKEN) instanceof TextValue(var token)) {
            effectiveQueryParams.set(SECRETS_TOKEN, JSON.stringNode(token));
            requestSettings.set(BlockingWebClient.PATH, JSON.stringNode(path));
        } else if (traccarSecrets.containsKey(SECRETS_USERNAME)) {
            val authHeaderOrError = createBasicAuthHeader(traccarSecrets);
            if (authHeaderOrError instanceof ErrorValue) {
                return authHeaderOrError;
            }
            val headers = JSON.objectNode();
            headers.set(HEADER_AUTHORIZATION, toJsonNode(authHeaderOrError));
            requestSettings.set(BlockingWebClient.HEADERS, headers);
            requestSettings.set(BlockingWebClient.PATH, JSON.stringNode(path));
        } else {
            return Value.error(ERROR_TRACCAR_CREDENTIALS_MISSING);
        }

        if (!effectiveQueryParams.isEmpty()) {
            requestSettings.set(BlockingWebClient.URL_PARAMS, effectiveQueryParams);
        }

        return ValueJsonMarshaller.fromJsonNode(requestSettings);
    }

    private static ObjectValue resolveTraccarSecrets(ObjectValue serverConfig, ObjectValue pdpSecrets) {
        if (pdpSecrets == null || pdpSecrets.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        if (!(pdpSecrets.get(SECRETS_TRACCAR) instanceof ObjectValue traccarSecrets)) {
            return Value.EMPTY_OBJECT;
        }
        if (serverConfig.get(CONFIG_NAME) instanceof TextValue(var serverName)
                && traccarSecrets.get(serverName) instanceof ObjectValue perServerSecrets) {
            return perServerSecrets;
        }
        if (traccarSecrets.containsKey(SECRETS_TOKEN) || traccarSecrets.containsKey(SECRETS_USERNAME)) {
            return traccarSecrets;
        }
        return Value.EMPTY_OBJECT;
    }

    private static Value enforceTransportSecurity(Value baseUrl, ObjectValue serverConfig) {
        // Fail closed: only a secure-scheme text baseUrl passes without the opt-in.
        if (baseUrl instanceof TextValue(var baseUrlText) && isSecureScheme(baseUrlText)) {
            return Value.UNDEFINED;
        }
        if (serverConfig.get(CONFIG_ALLOW_INSECURE_HTTP) instanceof BooleanValue(var allow) && allow) {
            return Value.UNDEFINED;
        }
        val serverName = serverConfig.get(CONFIG_NAME) instanceof TextValue(var name) ? name : DEFAULT_SERVER_NAME;
        return Value.error(ERROR_INSECURE_SCHEME.formatted(serverName));
    }

    private static boolean isSecureScheme(String uri) {
        try {
            return HTTPS_SCHEME.equalsIgnoreCase(URI.create(uri).getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Creates a Basic Authentication header value from the traccar secrets.
     *
     * @param traccarSecrets the traccar secrets containing userName and password
     * @return the Base64-encoded Basic Auth header as TextValue, or ErrorValue if
     * required fields are missing
     */
    static Value createBasicAuthHeader(ObjectValue traccarSecrets) {
        val userName = getRequiredProperty(SECRETS_USERNAME, traccarSecrets);
        if (userName instanceof ErrorValue) {
            return userName;
        }
        val password = getRequiredProperty(SECRETS_PASSWORD, traccarSecrets);
        if (password instanceof ErrorValue) {
            return password;
        }
        if (!(userName instanceof TextValue userNameText) || !(password instanceof TextValue passwordText)) {
            return Value.error(ERROR_CREDENTIAL_NOT_TEXT);
        }
        val credentials        = userNameText.value() + ':' + passwordText.value();
        val encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return Value.of("Basic " + encodedCredentials);
    }

    private static Value toGeoJsonOrError(Value value, Function<ObjectValue, Value> converter) {
        if (value instanceof ObjectValue object) {
            return converter.apply(object);
        }
        return value instanceof ErrorValue ? value : Value.error(ERROR_UNEXPECTED_RESPONSE);
    }

    /**
     * Percent-encodes a single path segment so that a policy-controlled entity id
     * cannot manipulate the request URL structure (query-parameter injection,
     * fragment truncation, or path traversal) against the Traccar server. Spaces
     * are encoded as {@code %20} rather than {@code +} because the value is a path
     * segment, not a query value.
     *
     * @param segment the raw entity id
     * @return the percent-encoded path segment
     */
    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
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

    private Stream<Value> withCtx(AttributeAccessContext ctx,
            BiFunction<ObjectValue, ObjectValue, Stream<Value>> withResolvedServer) {
        return withCtx(ctx, null, withResolvedServer);
    }

    private Stream<Value> withCtx(AttributeAccessContext ctx, TextValue serverName,
            BiFunction<ObjectValue, ObjectValue, Stream<Value>> withResolvedServer) {
        val serverOrError = resolveServer(ctx.variables(), serverName);
        if (serverOrError instanceof ErrorValue) {
            return Streams.just(serverOrError);
        }
        return withResolvedServer.apply((ObjectValue) serverOrError, ctx.pdpSecrets());
    }

    private static Value resolveServer(ObjectValue variables, TextValue requestedName) {
        val config = variables.get(TRACCAR_CONFIG);
        if (config == null || config instanceof UndefinedValue) {
            return Value.error(ERROR_TRACCAR_CONFIG_UNDEFINED);
        }
        if (!(config instanceof ObjectValue traccarConfig)) {
            return Value.error(ERROR_TRACCAR_CONFIG_NOT_OBJECT.formatted(config.getClass().getSimpleName()));
        }
        val targetName = requestedName == null ? null : requestedName.value();
        if (traccarConfig.get(CONFIG_SERVERS) instanceof ArrayValue servers) {
            return findNamedServer(traccarConfig, servers, targetName);
        }
        return resolveSingleObjectServer(traccarConfig, targetName);
    }

    private static Value findNamedServer(ObjectValue traccarConfig, ArrayValue servers, String requestedName) {
        val targetName = requestedName != null ? requestedName : defaultServerName(traccarConfig);
        for (val entry : servers) {
            if (entry instanceof ObjectValue server && server.get(CONFIG_NAME) instanceof TextValue(var name)
                    && name.equals(targetName)) {
                return server;
            }
        }
        return Value.error(ERROR_SERVER_NOT_FOUND.formatted(targetName));
    }

    // Back-compat: a flat single-object config is the implicit default server.
    private static Value resolveSingleObjectServer(ObjectValue traccarConfig, String requestedName) {
        if (requestedName != null && !requestedName.equals(defaultServerName(traccarConfig))) {
            return Value.error(ERROR_SERVER_NOT_FOUND.formatted(requestedName));
        }
        return traccarConfig;
    }

    private static String defaultServerName(ObjectValue traccarConfig) {
        return traccarConfig.get(CONFIG_DEFAULT_SERVER_NAME) instanceof TextValue(var name) ? name
                : DEFAULT_SERVER_NAME;
    }

}
