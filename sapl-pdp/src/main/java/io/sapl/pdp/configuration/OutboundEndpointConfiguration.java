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
package io.sapl.pdp.configuration;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.tls.PemTrust;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@UtilityClass
class OutboundEndpointConfiguration {
    private static final String      ERROR_DEFAULT     = "The configured default must name an endpoint in %s.";
    private static final String      ERROR_ENDPOINT    = "Invalid variables.%s.%s.%s: %s";
    private static final String      ERROR_FIELD       = "Unsupported field in %s. Put endpoints in variables, TLS material in tlsTrustProfiles, and credentials in named secrets.";
    private static final String      ERROR_OBJECT      = "%s must be an object.";
    private static final String      ERROR_PREFIXES    = "Each HTTP service requires a nonempty allowedBaseUrls array.";
    private static final String      ERROR_TLS         = "A trustProfile requires TLS on every destination of its endpoint.";
    private static final String      ERROR_URI         = "An outbound endpoint requires a valid URL with a fixed host.";
    private static final Set<String> TRUST_FIELDS      = Set.of("trustedCaPem", "includeDefaultTrustRoots",
            "trustProfile", "tlsTrustStore", "tlsTrustStorePassword");
    private static final Set<String> CREDENTIAL_FIELDS = Set.of("username", "userName", "password", "token");
    private static final Set<String> HTTP_SCHEMES      = Set.of("http", "https", "ws", "wss");
    private static final Set<String> SECURE_SCHEMES    = Set.of("https", "wss");
    private static final Set<String> WEB_SCHEMES       = Set.of("http", "https");

    static void validate(PdpData data) {
        val variables = data.variables();
        val secrets   = data.secrets();
        PemTrust.validateProfiles(variables);
        rejectFields(variables, Set.of("TRACCAR_CONFIG"), "variables");
        rejectFields(secrets, Set.of("tlsTrustProfiles"), "secrets");
        validateEndpoints(variables, "mqtt", "brokers");
        validateEndpoints(variables, "traccar", "servers");
        validateEndpoints(variables, "http", "services");
        if (variables.containsKey("jwt")) {
            val jwt = object(variables.get("jwt"), "variables.jwt");
            if (jwt.containsKey("publicKeyServer")) {
                val server   = object(jwt.get("publicKeyServer"), "variables.jwt.publicKeyServer");
                val selected = PemTrust.resolve(variables, server);
                rejectFields(server, CREDENTIAL_FIELDS, "variables.jwt.publicKeyServer");
                requireUrl(server.get("uri"), WEB_SCHEMES, selected != null, true);
            }
        }
        validateNamedSecrets(secrets, "http");
        validateNamedSecrets(secrets, "mqtt");
        validateNamedSecrets(secrets, "traccar");
        if (secrets.get("jwt") instanceof ObjectValue jwt && jwt.get("publicKeyServer") instanceof ObjectValue server) {
            rejectFields(server, TRUST_FIELDS, "secrets.jwt.publicKeyServer");
        }
    }

    private static void validateEndpoints(ObjectValue variables, String pip, String collection) {
        if (!variables.containsKey(pip)) {
            return;
        }
        val section = object(variables.get(pip), "variables." + pip);
        rejectFields(section, TRUST_FIELDS, pip);
        if ("mqtt".equals(pip)) {
            rejectFields(section, Set.of("defaultBrokerConfigName", "brokerConfig", "brokerAddress", "brokerPort",
                    "clientId", "tls", "username", "password"), pip);
        } else if ("traccar".equals(pip)) {
            rejectFields(section, Set.of("defaultServerName", "baseUrl", "userName", "password", "token"), pip);
        }
        if (!section.containsKey(collection) && !section.containsKey("default") && "http".equals(pip)) {
            return;
        }
        val entries = object(section.get(collection), "variables." + pip + "." + collection);
        if (!"http".equals(pip) && section.containsKey("default")
                && (!(section.get("default") instanceof TextValue(var name)) || name.isBlank()
                        || !entries.containsKey(name))) {
            throw new IllegalArgumentException(ERROR_DEFAULT.formatted(collection));
        }
        for (val item : entries.entrySet()) {
            if (item.getKey().isBlank()) {
                throw new IllegalArgumentException(ERROR_OBJECT.formatted(collection + " entry"));
            }
            val endpoint = object(item.getValue(), collection + " entry");
            try {
                validateEndpoint(variables, pip, collection, endpoint);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        ERROR_ENDPOINT.formatted(pip, collection, item.getKey(), e.getMessage()), e);
            }
        }
    }

    private static void validateEndpoint(ObjectValue variables, String pip, String collection, ObjectValue endpoint) {
        rejectFields(endpoint, CREDENTIAL_FIELDS, collection);
        val trusted = PemTrust.resolve(variables, endpoint) != null;
        switch (pip) {
        case "mqtt"    -> {
            rejectFields(endpoint, Set.of("name", "tlsTrustStore", "tlsTrustStorePassword"), collection);
            if (trusted && !(endpoint.get("tls") instanceof BooleanValue(var enabled) && enabled)) {
                throw new IllegalArgumentException(ERROR_TLS);
            }
        }
        case "traccar" -> {
            rejectFields(endpoint, Set.of("name"), collection);
            val uri = requireUrl(endpoint.get("baseUrl"), WEB_SCHEMES, trusted, false);
            if (uri.getRawQuery() != null && Arrays.stream(uri.getRawQuery().split("&"))
                    .map(parameter -> URLDecoder.decode(parameter.split("=", 2)[0], StandardCharsets.UTF_8))
                    .anyMatch("token"::equalsIgnoreCase)) {
                throw new IllegalArgumentException(ERROR_FIELD.formatted(collection));
            }
        }
        case "http"    -> validateService(endpoint, trusted);
        default        -> throw new IllegalStateException(pip);
        }
    }

    private static void validateService(ObjectValue service, boolean trusted) {
        if (!(service.get("allowedBaseUrls") instanceof ArrayValue prefixes) || prefixes.isEmpty()) {
            throw new IllegalArgumentException(ERROR_PREFIXES);
        }
        for (val prefix : prefixes) {
            val uri = requireUrl(prefix, HTTP_SCHEMES, trusted, false);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(ERROR_URI);
            }
        }
    }

    private static URI requireUrl(Value value, Set<String> schemes, boolean trusted, boolean keyTemplate) {
        if (!(value instanceof TextValue(var url))) {
            throw new IllegalArgumentException(ERROR_URI);
        }
        val uri = parseUri(keyTemplate ? url.replace("{kid}", "%7Bkid%7D") : url);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null || uri.getPort() > 65535
                || uri.getRawUserInfo() != null || !schemes.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(ERROR_URI);
        }
        if (trusted && !SECURE_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(ERROR_TLS);
        }
        return uri;
    }

    private static @Nullable URI parseUri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            // A URI syntax exception includes its input, which may contain credentials.
            return null;
        }
    }

    private static void validateNamedSecrets(ObjectValue secrets, String pip) {
        if (!secrets.containsKey(pip)) {
            return;
        }
        val entries = object(secrets.get(pip), "secrets." + pip);
        for (val item : entries.entrySet()) {
            if (item.getKey().isBlank()) {
                throw new IllegalArgumentException(ERROR_OBJECT.formatted("secrets." + pip + " entry"));
            }
            val entry = object(item.getValue(), "secrets." + pip + " entry");
            rejectFields(entry, TRUST_FIELDS, "secrets." + pip);
            if (!"http".equals(pip)) {
                rejectFields(entry, Set.of("allowedBaseUrls"), "secrets." + pip);
            }
        }
    }

    private static void rejectFields(ObjectValue object, Set<String> fields, String section) {
        if (fields.stream().anyMatch(object::containsKey)) {
            throw new IllegalArgumentException(ERROR_FIELD.formatted(section));
        }
    }

    private static ObjectValue object(Value value, String section) {
        if (value instanceof ObjectValue object) {
            return object;
        }
        throw new IllegalArgumentException(ERROR_OBJECT.formatted(section));
    }
}
