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
import io.sapl.attributes.tls.PemTrust;
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
import java.util.function.Function;

/**
 * Policy Information Point for integrating with Traccar GPS tracking servers.
 * Provides attribute finders for fetching device positions, geofences, and
 * server metadata from Traccar servers.
 */
@RequiredArgsConstructor
@PolicyInformationPoint(name = TraccarPolicyInformationPoint.NAME, description = TraccarPolicyInformationPoint.DESCRIPTION, pipDocumentation = TraccarPolicyInformationPoint.DOCUMENTATION)
public class TraccarPolicyInformationPoint {

    public static final String TRACCAR_CONFIG = "traccar";
    public static final String NAME           = "traccar";
    public static final String DESCRIPTION    = "This policy information point can fetch device positions and geofences from a traccar server.";

    static final String ERROR_BAD_RESPONSE_EXPECTED_ARRAY = "Bad response. Expected a non-empty array, but got: %s.";
    static final String ERROR_CREDENTIAL_NOT_TEXT         = "Traccar credentials must be text values.";
    static final String ERROR_INSECURE_SCHEME             = "Traccar server '%s' baseUrl does not use https. Set 'allowInsecureHttp': true on that server entry to permit insecure transport.";
    static final String ERROR_REQUIRED_FIELD_MISSING      = "Required field '%s' missing from traccar configuration.";
    static final String ERROR_SERVER_NOT_FOUND            = "No Traccar server named '%s' is configured in variables.traccar.servers.";
    static final String ERROR_TRACCAR_CONFIG_NOT_OBJECT   = "variables.traccar must be an object, but was: %s";
    static final String ERROR_TRACCAR_CONFIG_UNDEFINED    = "Cannot connect to Traccar server. The variables.traccar configuration is missing.";
    static final String ERROR_TRACCAR_CREDENTIALS_MISSING = "No Traccar credentials found in pdpSecrets. Configure secrets.traccar.<serverName> with 'token' or 'userName'/'password'.";
    static final String ERROR_UNEXPECTED_RESPONSE         = "Unexpected Traccar response. Expected a JSON object.";

    private static final String CONFIG_ALLOW_INSECURE_HTTP = "allowInsecureHttp";
    private static final String CONFIG_DEFAULT_SERVER_NAME = "default";
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
            * Without parameter: uses the operator's default server from `variables.traccar.servers`.
            * With `serverName` parameter: selects the named operator server from `variables.traccar.servers`.

            A policy selects a server only by name. It can never supply a `baseUrl` or any
            connection object. The destination host, transport policy, and credentials are
            bound by the operator per server.

            ## Selecting a server

            A policy selects its default server with `<traccar.server>`, or a named server with `<traccar.devices("depot")>`. Set polling cadence through the broker's `pollIntervalMs` attribute option. The server entry may explicitly enable `allowInsecureHttp` for local development over HTTP. A trust profile requires HTTPS.

            ## Geofencing Example

            ```sapl
            policy "geofence_check"
            permit
              var position = subject.device.<traccar.position>;
              var fence    = subject.geofence.<traccar.geofenceGeometry>;
              geo.contains(fence, position);
            ```

            ## Configure TLS trust

            The Traccar PIP reads its endpoint from the public PDP configuration. For a private CA, start with the following `pdp.json` settings and install the referenced profile before deployment. Multiple endpoints and PIPs can reference the same profile.

            ```json
            {
              "variables": {
                "traccar": {
                  "default": "depot",
                  "servers": {
                    "depot": {
                      "baseUrl": "https://fleet.example.org",
                      "trustProfile": "site-ca"
                    }
                  }
                }
              }
            }
            ```

            Read the named server with `<traccar.devices("depot")>`. Without a name, the PIP selects the configured `default`, or the server named `default` if the setting is absent. An unknown server is an error.

            The operator supplies a token or a `userName` and `password` pair under the exact server name. Replace the sample token with a Traccar API token.

            ```json
            {
              "traccar": {
                "depot": {
                  "token": "REPLACE_WITH_TRACCAR_TOKEN"
                }
              }
            }
            ```

            The operator secrets file is `secrets.json` for explicitly enabled cleartext development or `secrets.sealed.json` for sealed deployment. Broker and server credentials are selected only by their configured name.

            ### Obtain and verify the CA certificate

            Ask the administrator of the Traccar server or its HTTPS reverse proxy for its public CA certificate and confirm its SHA-256 fingerprint through your organization's PKI or another authenticated channel. A PEM file contains a `-----BEGIN CERTIFICATE-----` block. If the administrator provides a binary DER certificate, convert it first.

            ```sh
            openssl x509 -inform DER -in issuer.cer -out ca.pem
            ```

            If the CA is already in an approved Java truststore, export its trusted-certificate alias. `keytool` prompts for the store password.

            ```sh
            keytool -exportcert -rfc -keystore truststore.p12 -alias issuing-ca -file ca.pem
            ```

            Check the identity and fingerprint of each certificate before installing it.

            ```sh
            openssl x509 -in ca.pem -noout -subject -issuer -fingerprint -sha256
            ```

            ### Install the named profile

            In the following command, `pdp.input.json` is the complete configuration with the endpoint settings above. `jq --rawfile` reads the public certificate including its PEM delimiters and line breaks. The resulting `pdp.json` keeps the other configuration fields.

            ```sh
            jq --rawfile ca ca.pem '.variables.tlsTrustProfiles["site-ca"] = {trustedCaPem: $ca, includeDefaultTrustRoots: false}' pdp.input.json > pdp.json
            ```

            | Endpoint setting | Trusted roots |
            | --- | --- |
            | Omit `trustProfile` | Runtime defaults. |
            | Select a profile with `includeDefaultTrustRoots` absent or `false` | Only the CA certificates in that profile. |
            | Select a profile with `includeDefaultTrustRoots: true` | Profile certificates and runtime defaults. |

            A named profile requires a nonempty `trustedCaPem` string. Each PEM block must contain a public X.509 certificate. The Java client uses the JVM trust configuration by default. The certificate must match the server's hostname. An invalid bundle, an unknown profile, or a profile on a plaintext endpoint prevents the new PDP configuration from taking effect.

            For a CA rollover, deploy the old and new CA certificates together as consecutive PEM blocks in the profile. After the service switches certificates, remove the retired CA and deploy the configuration again.
            """;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final BlockingWebClient webClient;

    @EnvironmentAttribute(schema = TraccarSchemata.SERVER_SCHEMA, docs = """
            ```<traccar.server>``` is an environment attribute that retrieves server metadata from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Server/paths/~1server/get).
            It uses `variables.traccar.servers` to connect to the server.

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
            It selects the named operator server from `variables.traccar.servers`.

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

    private Stream<Value> server(Server server) {
        return Streams.distinctUntilChanged(get("/api/server", server, Map.of()));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.DEVICES_SCHEMA, docs = """
            ```<traccar.devices>``` is an environment attribute that retrieves a list of devices from the
            [Traccar server endpoint](https://www.traccar.org/api-reference/#tag/Devices/paths/~1devices/get).
            It uses `variables.traccar.servers` to connect to the server.

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
            It selects the named operator server from `variables.traccar.servers`.

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

    private Stream<Value> devices(Server server) {
        return Streams.distinctUntilChanged(get("/api/devices", server, Map.of()));
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA, docs = """
            ```deviceEntityId.<traccar.device>``` is an attribute that fetches detailed metadata for a specific device from
            the Traccar server.
            The device is identified using the `deviceEntityId` parameter, which is the identifier of the device in Traccar,
            not the device's `uniqueId` in the database.
            This method uses `variables.traccar.servers` to retrieve the server connection configuration.

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
        return withCtx(ctx, server -> device(deviceEntityId, server));
    }

    @Attribute(schema = TraccarSchemata.DEVICE_SCHEMA, docs = """
            ```deviceEntityId.<traccar.device(serverName)>``` is an attribute that fetches detailed metadata for a specific device from
            the Traccar server.
            The device is identified using the `deviceEntityId` parameter, which is the identifier of the device in Traccar,
            not the device's `uniqueId` in the database.
            It selects the named operator server from `variables.traccar.servers`.

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
        return withCtx(ctx, serverName, server -> device(deviceEntityId, server));
    }

    private Stream<Value> device(TextValue deviceEntityId, Server server) {
        return Streams.distinctUntilChanged(
                get("/api/devices/%s".formatted(encodePathSegment(deviceEntityId.value())), server, Map.of()));
    }

    @EnvironmentAttribute(schema = TraccarSchemata.GEOFENCES_SCHEMA, docs = """
            ```<traccar.geofences>``` is an environment attribute that retrieves a list of all geofences from the Traccar server.
            It uses `variables.traccar.servers` to connect to the server.

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
            the Traccar server. It selects the named operator server from `variables.traccar.servers`.

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

    private Stream<Value> geofences(Server server) {
        return Streams.distinctUntilChanged(get("/api/geofences", server, Map.of()));
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA, docs = """
            ```geofenceEntityId.<traccar.traccarGeofence>``` is an attribute that retrieves metadata for a specific geofence from
            the Traccar server using the provided geofence identifier. This method uses the configured servers in
            `variables.traccar.servers` to retrieve the server connection configuration.

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
        return withCtx(ctx, server -> traccarGeofence(geofenceEntityId, server));
    }

    @Attribute(schema = TraccarSchemata.GEOFENCE_SCHEMA, docs = """
            ```geofenceEntityId.<traccar.traccarGeofence(serverName)>``` is an attribute that retrieves metadata for a specific
            geofence from the Traccar server using the provided geofence identifier.
            It selects the named operator server from `variables.traccar.servers`.

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
        return withCtx(ctx, serverName, server -> traccarGeofence(geofenceEntityId, server));
    }

    private Stream<Value> traccarGeofence(TextValue geofenceEntityId, Server server) {
        return Streams.distinctUntilChanged(
                get("/api/geofences/%s".formatted(encodePathSegment(geofenceEntityId.value())), server, Map.of()));
    }

    @Attribute(schema = GeoJSONSchemata.POLYGON, docs = """
            ```geofenceEntityId.<traccar.geofenceGeometry>``` is an attribute that converts geofence metadata into GeoJSON format
            for geometric representation. This method uses `variables.traccar.servers` to retrieve the
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
            It selects the named operator server from `variables.traccar.servers`.

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
        return withCtx(ctx, serverName, server -> geofenceGeometry(geofenceEntityId, server));
    }

    private Stream<Value> geofenceGeometry(TextValue geofenceEntityId, Server server) {
        return Streams.distinctUntilChanged(Streams.map(traccarGeofence(geofenceEntityId, server),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarGeofenceToGeoJson)));
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA, docs = """
            ```deviceEntityId.<traccar.traccarPosition>``` is an attribute that retrieves the most recent position of a specific
            device from the Traccar server. This method uses `variables.traccar.servers` to retrieve the
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
        return withCtx(ctx, server -> traccarPosition(deviceEntityId, server));
    }

    @Attribute(schema = TraccarSchemata.POSITION_SCHEMA, docs = """
            ```deviceEntityId.<traccar.traccarPosition(serverName)>``` is an attribute that retrieves the most recent position of
            a specific device from the Traccar server.
            It selects the named operator server from `variables.traccar.servers`.

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
        return withCtx(ctx, serverName, server -> traccarPosition(deviceEntityId, server));
    }

    private Stream<Value> traccarPosition(TextValue deviceEntityId, Server server) {
        val deviceId  = ValueJsonMarshaller.toJsonNode(deviceEntityId);
        val positions = get("/api/positions", server, Map.of("deviceId", deviceId));
        return Streams
                .distinctUntilChanged(Streams.map(positions, TraccarPolicyInformationPoint::takeFirstElementFromArray));
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
            from the Traccar server into GeoJSON format. This method uses `variables.traccar.servers`
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
            It selects the named operator server from `variables.traccar.servers`.

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
        return withCtx(ctx, serverName, server -> position(deviceEntityId, server));
    }

    private Stream<Value> position(TextValue deviceEntityId, Server server) {
        return Streams.distinctUntilChanged(Streams.map(traccarPosition(deviceEntityId, server),
                value -> toGeoJsonOrError(value, TraccarFunctionLibrary::traccarPositionToGeoJSON)));
    }

    private record Server(String name, ObjectValue endpoint, ObjectValue credentials, BlockingWebClient client) {}

    private Stream<Value> get(String path, Server server, Map<String, JsonNode> query) {
        val settings = requestSettingsFromTraccarConfig(path, server, query);
        return settings instanceof ErrorValue ? Streams.just(settings)
                : server.client().httpRequest("GET", (ObjectValue) settings);
    }

    private Value requestSettingsFromTraccarConfig(String path, Server server, Map<String, JsonNode> queryParameters) {
        val traccarSecrets = server.credentials();
        val baseUrl        = getRequiredProperty(BlockingWebClient.BASE_URL, server.endpoint());
        if (baseUrl instanceof ErrorValue) {
            return baseUrl;
        }

        val schemeGate = enforceTransportSecurity(baseUrl, server);
        if (schemeGate instanceof ErrorValue) {
            return schemeGate;
        }

        val requestSettings = JSON.objectNode();
        requestSettings.set(BlockingWebClient.BASE_URL, toJsonNode(baseUrl));
        val apiPath = baseUrl instanceof TextValue(var url) && url.endsWith("/") ? path.substring(1) : path;
        requestSettings.set(BlockingWebClient.PATH, JSON.stringNode(apiPath));
        requestSettings.set(BlockingWebClient.ACCEPT_MEDIATYPE, JSON.stringNode(MEDIATYPE_JSON));

        val effectiveQueryParams = JSON.objectNode();
        for (val parameter : queryParameters.entrySet()) {
            effectiveQueryParams.set(parameter.getKey(), parameter.getValue());
        }

        if (traccarSecrets.get(SECRETS_TOKEN) instanceof TextValue(var token)) {
            effectiveQueryParams.set(SECRETS_TOKEN, JSON.stringNode(token));
        } else if (traccarSecrets.containsKey(SECRETS_USERNAME)) {
            val authHeaderOrError = createBasicAuthHeader(traccarSecrets);
            if (authHeaderOrError instanceof ErrorValue) {
                return authHeaderOrError;
            }
            val headers = JSON.objectNode();
            headers.set(HEADER_AUTHORIZATION, toJsonNode(authHeaderOrError));
            requestSettings.set(BlockingWebClient.HEADERS, headers);
        } else {
            return Value.error(ERROR_TRACCAR_CREDENTIALS_MISSING);
        }

        if (!effectiveQueryParams.isEmpty()) {
            requestSettings.set(BlockingWebClient.URL_PARAMS, effectiveQueryParams);
        }

        return ValueJsonMarshaller.fromJsonNode(requestSettings);
    }

    private static Value enforceTransportSecurity(Value baseUrl, Server server) {
        // Fail closed: only a secure-scheme text baseUrl passes without the opt-in.
        if (baseUrl instanceof TextValue(var baseUrlText) && isSecureScheme(baseUrlText)) {
            return Value.UNDEFINED;
        }
        if (server.endpoint().get(CONFIG_ALLOW_INSECURE_HTTP) instanceof BooleanValue(var allow) && allow) {
            return Value.UNDEFINED;
        }
        return Value.error(ERROR_INSECURE_SCHEME.formatted(server.name()));
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

    private Stream<Value> withCtx(AttributeAccessContext ctx, Function<Server, Stream<Value>> action) {
        return withCtx(ctx, null, action);
    }

    private Stream<Value> withCtx(AttributeAccessContext ctx, TextValue requestedName,
            Function<Server, Stream<Value>> action) {
        val config = ctx.variables().get(TRACCAR_CONFIG);
        if (config == null || config instanceof UndefinedValue) {
            return Streams.error(ERROR_TRACCAR_CONFIG_UNDEFINED);
        }
        if (!(config instanceof ObjectValue traccar)) {
            return Streams.error(ERROR_TRACCAR_CONFIG_NOT_OBJECT.formatted(config.getClass().getSimpleName()));
        }
        val fallback = traccar.get(CONFIG_DEFAULT_SERVER_NAME) instanceof TextValue(var name) ? name
                : DEFAULT_SERVER_NAME;
        val name     = requestedName == null ? fallback : requestedName.value();
        if (!(traccar.get(CONFIG_SERVERS) instanceof ObjectValue servers)
                || !(servers.get(name) instanceof ObjectValue endpoint)) {
            return Streams.error(ERROR_SERVER_NOT_FOUND.formatted(name));
        }
        val credentials = ctx.pdpSecrets().get(SECRETS_TRACCAR) instanceof ObjectValue entries
                && entries.get(name) instanceof ObjectValue selected ? selected : Value.EMPTY_OBJECT;
        try {
            val trust  = PemTrust.resolve(ctx.variables(), endpoint);
            val client = trust == null ? webClient : webClient.withTrust("traccar." + name, trust);
            return action.apply(new Server(name, endpoint, credentials, client));
        } catch (IllegalArgumentException e) {
            return Streams.error(e.getMessage());
        }
    }
}
