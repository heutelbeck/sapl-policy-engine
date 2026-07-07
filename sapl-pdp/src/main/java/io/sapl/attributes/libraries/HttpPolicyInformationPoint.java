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
package io.sapl.attributes.libraries;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import lombok.RequiredArgsConstructor;
import lombok.val;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@RequiredArgsConstructor
@PolicyInformationPoint(name = "http", description = HttpPolicyInformationPoint.DESCRIPTION, pipDocumentation = HttpPolicyInformationPoint.DOCUMENTATION)
public class HttpPolicyInformationPoint {

    public static final String DESCRIPTION   = "This Policy Information Point to get and monitor HTTP based information.";
    public static final String DOCUMENTATION = """
            This Policy Information Point provides means to source attribute data by consuming
            HTTP-based APIs and WebSockets.

            ## Attribute Invocation

            Attributes are named after the HTTP verb: `get`, `post`, `put`, `patch`, `delete`,
            and `websocket`. Each is available as an environment attribute or as an attribute of
            a resource URL.

            | Policy syntax | Meaning |
            |---|---|
            | `<http.get(request)>` | Environment attribute, HTTP GET with request settings. |
            | `"https://api.example.com".<http.get>` | Entity attribute, HTTP GET with default settings. |
            | `"https://api.example.com".<http.get(request)>` | Entity attribute, URL used as `baseUrl`, custom settings. |
            | `<http.post(request)>` | Environment attribute, HTTP POST. |
            | `<http.websocket(request)>` | Environment attribute, WebSocket connection. |

            ## Request Settings

            All attributes take a `requestSettings` object parameter with the following fields:

            | Field | Type | Default | Description |
            |---|---|---|---|
            | `baseUrl` | text | (required) | The base URL for the HTTP request. |
            | `path` | text | `""` | Path appended to the base URL. |
            | `urlParameters` | object | `{}` | Key-value pairs for HTTP query parameters. |
            | `headers` | object | `{}` | Key-value pairs for HTTP request headers. |
            | `body` | any | (none) | The request body. |
            | `accept` | text | `"application/json"` | Accepted response media type. |
            | `contentType` | text | `"application/json"` | Media type of the request body. |
            | `maxResponseBytes` | number | `1048576` | Maximum response body, SSE event, or WebSocket message size in bytes; an oversized payload fails closed to an error value. |
            | `secretsKey` | text | (none) | Selects a named credential set from secrets (see below). |

            The `secretsKey` field is request metadata and is stripped before the HTTP request is
            sent.

            Polling cadence is not a request setting. Each call issues one request and emits one
            value; the engine re-evaluates the attribute on its own schedule via the
            `pollIntervalMs` attribute option (see Functions and Attributes), uniformly with every
            streaming attribute.

            ## Secrets Configuration

            HTTP credentials (API keys, bearer tokens, custom headers) are sourced from the
            `secrets` section in `pdp.json` and/or from subscription secrets. They are never
            embedded directly in policies.

            Every credential is a **named** entry that declares both its headers and the
            destinations it may be sent to. A policy selects an entry with `secretsKey`; it can
            never supply the credential itself, and the entry can never be sent to a URL outside
            its `allowedBaseUrls`. There is no unnamed default credential.

            ### Named Credentials

            Each entry lives at `secrets.http.<name>` and has two fields:

            | Field | Description |
            |---|---|
            | `headers` | The credential headers to attach. |
            | `allowedBaseUrls` | The destinations the entry may be sent to. Required. An entry that declares none permits nothing. |

            For a request with `"secretsKey": "weather-api"`, the PDP resolves
            `secrets.http.weather-api` from each secrets source, checks the request `baseUrl`
            against that entry's `allowedBaseUrls`, and attaches its `headers` only if the
            destination is permitted.

            An `allowedBaseUrls` prefix matches by scheme, host, and port, then by path prefix at
            a segment boundary. It is a structural match, not a string prefix, so
            `https://api.example.com` does not match `https://api.example.com.attacker.com`. The
            scheme is part of the match, so plaintext transport is permitted only when the operator
            lists an `http`/`ws` prefix explicitly.

            ### Header Precedence

            Header precedence (highest to lowest):
            1. **pdpSecrets** -- operator-configured secrets always win
            2. **Policy headers** -- non-credential headers specified in the `requestSettings` object
            3. **subscriptionSecrets** -- headers from the authorization subscription

            When headers from multiple sources use the same header name, the higher-priority
            source overwrites the lower-priority value. Each secrets source authorizes the
            destination through its own entry, so a credential can only reach a host that source
            bound it to.

            ### Multi-Service Secrets Example

            ```json
            {
              "variables": { },
              "secrets": {
                "http": {
                  "weather-api": {
                    "allowedBaseUrls": [ "https://api.weather.example" ],
                    "headers": { "X-API-Key": "abc123" }
                  },
                  "internal-api": {
                    "allowedBaseUrls": [ "https://api.internal.corp" ],
                    "headers": { "Authorization": "Bearer infra-token" }
                  }
                }
              }
            }
            ```

            With this configuration:
            * `{ "secretsKey": "weather-api", "baseUrl": "https://api.weather.example/v1" }` gets
              header `X-API-Key: abc123`.
            * `{ "secretsKey": "internal-api", "baseUrl": "https://attacker.example.com" }` is
              rejected. The secret does not permit that host.
            * `{ "secretsKey": "internal-api", "baseUrl": "http://api.internal.corp" }` is
              rejected. The allowlist pins `https`.

            ### Subscription Secrets

            Subscription secrets follow the same named structure and can be supplied per
            authorization subscription. They have the lowest priority and are overridden by both
            policy headers and pdpSecrets headers.

            ## Security

            Credentials never travel in policy text, and a secret only travels to a destination the
            operator bound it to. Both rules are enforced fail closed, so a violating request
            returns an error instead of leaking the secret.

            * A `requestSettings.headers` object that carries a credential header
              (`Authorization` or `Proxy-Authorization`) is rejected. Supply credentials through
              the `secrets` channels instead and select them with `secretsKey`. The `secretsKey`
              field itself is non-sensitive metadata and is safe to use in policies.
            * A named secret is attached only when the request `baseUrl` matches that secret's
              `allowedBaseUrls`. A secret with no matching entry, including one that declares none,
              is never sent. Because the match includes the scheme, cleartext transport is an
              explicit operator choice per destination, not a policy decision.

            ## Media Type Handling

            * `application/json`: Response body is parsed and mapped to a SAPL value.
            * `text/event-stream`: The PIP subscribes to server-sent events (SSEs) instead
              of polling.
            * Other types: Response body is returned as a text value.

            ## Timeouts

            Connection timeout is 10 seconds, read timeout is 30 seconds. Unresponsive
            endpoints result in an error value.
            """;

    private static final String SECRETS_ALLOWED_BASE_URLS = "allowedBaseUrls";
    private static final String SECRETS_HEADERS           = "headers";
    private static final String SECRETS_HTTP              = "http";
    private static final String SECRETS_KEY               = "secretsKey";

    // Header names that carry authentication material. A policy must never supply
    // these itself. Credentials come only from the operator or subscription
    // secrets channels. Compared case-insensitively.
    private static final Set<String> CREDENTIAL_HEADER_NAMES = Set.of("authorization", "proxy-authorization");

    private static final String ERROR_POLICY_CREDENTIAL_HEADER         = "Policy-supplied requestSettings must not carry the credential header '%s'. Configure credentials in the secrets section and select them with 'secretsKey'.";
    private static final String ERROR_SECRET_DESTINATION_NOT_PERMITTED = "The secret '%s' does not permit the destination '%s'. Add a matching prefix to that secret's allowedBaseUrls, or correct the request URL.";

    /**
     * The credential selection requested by {@code requestSettings.secretsKey}.
     * Distinguishes an absent key (flat default fallback) from a
     * present-but-not-text
     * key (specified-but-unresolvable, fail closed) from a named key.
     */
    private sealed interface SecretsKeySelection {
        record Absent() implements SecretsKeySelection {}

        record Malformed() implements SecretsKeySelection {}

        record Named(String name) implements SecretsKeySelection {}
    }

    private final BlockingWebClient webClient;

    /**
     * Performs an HTTP GET against {@code requestSettings.baseUrl} and
     * polls it according to the request settings.
     *
     * @param ctx the attribute access context (variables, secrets sources)
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<get(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP GET request and
            polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status"
                            };
              <http.get(request)[{pollIntervalMs: 1000}]>.status == "OK";
            ```
            The `[{pollIntervalMs: 1000}]` attribute option sets how often the engine
            re-evaluates the attribute (re-issues the request); it is optional and
            defaults to the engine-wide attribute poll interval.
            """)
    public Stream<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("GET", ctx, requestSettings);
    }

    /**
     * Performs an HTTP POST against {@code requestSettings.baseUrl}.
     *
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<post(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST request and
            polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            };
              <http.post(request)>.status == "off";
            ```
            """)
    public Stream<Value> post(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("POST", ctx, requestSettings);
    }

    /**
     * Performs an HTTP PUT against {@code requestSettings.baseUrl}.
     *
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<put(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT request and
            polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            };
              <http.put(request)>.status == "off";
            ```
            """)
    public Stream<Value> put(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("PUT", ctx, requestSettings);
    }

    /**
     * Performs an HTTP PATCH against {@code requestSettings.baseUrl}.
     *
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<patch(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH request and
            polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": { "action": "turnOff", "resource": "heater" }
                            };
              <http.patch(request)>.status == "off";
            ```
            """)
    public Stream<Value> patch(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("PATCH", ctx, requestSettings);
    }

    /**
     * Performs an HTTP DELETE against {@code requestSettings.baseUrl}.
     *
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<delete(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status"
                            };
              <http.delete(request)> != undefined;
            ```
            """)
    public Stream<Value> delete(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("DELETE", ctx, requestSettings);
    }

    /**
     * Opens a WebSocket against {@code requestSettings.baseUrl} and
     * emits each server message as a value. The configured {@code body}
     * is sent on connect.
     *
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of WebSocket message values
     */
    @Attribute
    @EnvironmentAttribute(docs = """
            ```<websocket(OBJECT requestSettings)>``` is an environment attribute stream and takes no left-hand arguments.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = {
                                "baseUrl": "https://example.com",
                                "path": "/status",
                                "body": "message"
                            };
              <http.websocket(request)>.health == "GOOD";
            ```
            """)
    public Stream<Value> websocket(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchWebSocket(ctx, requestSettings);
    }

    /**
     * Entity-form HTTP GET on {@code resourceUrl} with default
     * request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<get>``` is an attribute of the resource identified by the ```resourceUrl```.
            Performs an HTTP GET request with default settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.get>.status == "HEALTHY";
            ```
            """)
    public Stream<Value> get(TextValue resourceUrl, AttributeAccessContext ctx) {
        return get(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form HTTP GET on {@code resourceUrl} merged with
     * {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<get(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP GET
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.get({ })>.status == "HEALTHY";
            ```
            """)
    public Stream<Value> get(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("GET", ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    /**
     * Entity-form HTTP POST on {@code resourceUrl} with default
     * request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<post>``` is an attribute of the resource identified by the ```resourceUrl```.
            Performs an HTTP POST request with default settings.
            """)
    public Stream<Value> post(TextValue resourceUrl, AttributeAccessContext ctx) {
        return post(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form HTTP POST on {@code resourceUrl} merged with
     * {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<post(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP POST
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.post({ "body": "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Stream<Value> post(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("POST", ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    /**
     * Entity-form HTTP PUT on {@code resourceUrl} with default
     * request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<put>``` is an attribute of the resource identified by the ```resourceUrl```.
            Performs an HTTP PUT request with default settings.
            """)
    public Stream<Value> put(TextValue resourceUrl, AttributeAccessContext ctx) {
        return put(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form HTTP PUT on {@code resourceUrl} merged with
     * {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<put(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PUT
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.put({ "body": "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Stream<Value> put(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("PUT", ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    /**
     * Entity-form HTTP PATCH on {@code resourceUrl} with default
     * request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<patch>``` is an attribute of the resource identified by the ```resourceUrl```.
            Performs an HTTP PATCH request with default settings.
            """)
    public Stream<Value> patch(TextValue resourceUrl, AttributeAccessContext ctx) {
        return patch(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form HTTP PATCH on {@code resourceUrl} merged with
     * {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<patch(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP PATCH
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.patch({ "body": "\\"test\\"" })>.status == "OK";
            ```
            """)
    public Stream<Value> patch(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("PATCH", ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    /**
     * Entity-form HTTP DELETE on {@code resourceUrl} with default
     * request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<delete>``` is an attribute of the resource identified by the ```resourceUrl```.
            Performs an HTTP DELETE request with default settings.
            """)
    public Stream<Value> delete(TextValue resourceUrl, AttributeAccessContext ctx) {
        return delete(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form HTTP DELETE on {@code resourceUrl} merged with
     * {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of response values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<delete(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and performs the matching HTTP DELETE
            request and polls it according the the settings.

            Example:
            ```sapl
            policy "http example"
            permit
              "https://example.com/resources/123".<http.delete({})> != undefined;
            ```
            """)
    public Stream<Value> delete(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchHttp("DELETE", ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    /**
     * Entity-form WebSocket connection to {@code resourceUrl} with
     * default request settings.
     *
     * @param resourceUrl the resource URL acting as {@code baseUrl}
     * @param ctx the attribute access context
     * @return a stream of WebSocket message values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<websocket>``` is an attribute of the resource identified by the ```resourceUrl```.
            Connects to a WebSocket with default settings.
            """)
    public Stream<Value> websocket(TextValue resourceUrl, AttributeAccessContext ctx) {
        return websocket(resourceUrl, ctx, Value.EMPTY_OBJECT);
    }

    /**
     * Entity-form WebSocket connection to {@code resourceUrl} merged
     * with {@code requestSettings}.
     *
     * @param resourceUrl the resource URL, supplied as {@code baseUrl}
     * @param ctx the attribute access context
     * @param requestSettings request configuration object
     * @return a stream of WebSocket message values
     */
    @Attribute(docs = """
            ```(TEXT resourceUrl).<websocket(OBJECT requestSettings)>``` is an attribute of the resource identified by
            the ```resourceUrl```.
            This attribute takes a ```requestSettings``` object as a parameter and connects to a Websocket and emits events
            as sent by the server. Upon connection, the ```body``` of the settings is sent to the server.

            Example:
            ```sapl
            policy "http example"
            permit
              var request = { "body": "message" };
              "https://example.com/status".<http.websocket(request)>.health == "GOOD";
            ```
            """)
    public Stream<Value> websocket(TextValue resourceUrl, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return dispatchWebSocket(ctx, withBaseUrl(resourceUrl, requestSettings));
    }

    private static ObjectValue withBaseUrl(TextValue baseUrl, ObjectValue requestSettings) {
        val builder = ObjectValue.builder();
        builder.putAll(requestSettings);
        builder.put(BlockingWebClient.BASE_URL, baseUrl);
        return builder.build();
    }

    private Stream<Value> dispatchHttp(String method, AttributeAccessContext ctx, ObjectValue requestSettings) {
        return guardAndSend(ctx, requestSettings, settings -> webClient.httpRequest(method, settings));
    }

    private Stream<Value> dispatchWebSocket(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return guardAndSend(ctx, requestSettings, webClient::consumeWebSocket);
    }

    /**
     * Enforces the two credential invariants before the request is sent, then
     * dispatches through {@code sender}. First, a policy must not supply a
     * credential header itself. Second, every secret that contributes headers
     * must permit the request destination through its own {@code allowedBaseUrls}
     * contract. Either violation returns an error value and no request is made.
     */
    private static Stream<Value> guardAndSend(AttributeAccessContext ctx, ObjectValue requestSettings,
            Function<ObjectValue, Stream<Value>> sender) {
        val offendingHeader = policyCredentialHeader(requestSettings);
        if (offendingHeader != null) {
            return Streams.just(Value.error(ERROR_POLICY_CREDENTIAL_HEADER, offendingHeader));
        }
        val destinationViolation = secretDestinationViolation(ctx, requestSettings);
        if (destinationViolation != null) {
            return Streams.just(destinationViolation);
        }
        return sender.apply(mergeHeaders(ctx, requestSettings));
    }

    private static @Nullable String policyCredentialHeader(ObjectValue requestSettings) {
        for (val name : extractPolicyHeaders(requestSettings).keySet()) {
            if (CREDENTIAL_HEADER_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        return null;
    }

    /**
     * Fails closed when a selected secret contributes headers but does not permit
     * the request destination. Each secrets source authorizes the destination
     * independently through its own entry, so a credential can only reach a host
     * the operator (or the subscription) bound it to. A secret with no matching
     * {@code allowedBaseUrls} entry, including one that declares none, permits
     * nothing.
     */
    private static @Nullable Value secretDestinationViolation(AttributeAccessContext ctx, ObjectValue requestSettings) {
        val secretsKey = extractSecretsKey(requestSettings);
        if (!(secretsKey instanceof SecretsKeySelection.Named(var name))) {
            return null;
        }
        val baseUrl = baseUrlOf(requestSettings);
        for (val secrets : List.of(ctx.pdpSecrets(), ctx.subscriptionSecrets())) {
            if (!resolveHttpHeaders(secrets, secretsKey).isEmpty() && !destinationPermitted(secrets, name, baseUrl)) {
                return Value.error(ERROR_SECRET_DESTINATION_NOT_PERMITTED, name, String.valueOf(baseUrl));
            }
        }
        return null;
    }

    private static boolean destinationPermitted(ObjectValue secrets, String name, @Nullable String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        for (val allowedPrefix : allowedBaseUrls(secrets, name)) {
            if (destinationMatches(allowedPrefix, baseUrl)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> allowedBaseUrls(ObjectValue secrets, String name) {
        if (secrets == null || !(secrets.get(SECRETS_HTTP) instanceof ObjectValue httpObj)
                || !(httpObj.get(name) instanceof ObjectValue namedObj)
                || !(namedObj.get(SECRETS_ALLOWED_BASE_URLS) instanceof ArrayValue urls)) {
            return List.of();
        }
        val result = new ArrayList<String>();
        for (val url : urls) {
            if (url instanceof TextValue(var text)) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * True when {@code requestUrl} falls under {@code allowedPrefix} by scheme,
     * host, and effective port equality plus a path-segment prefix. Component
     * comparison, not string prefix, so {@code https://api.corp} does not match
     * {@code https://api.corp.attacker.com}. A prefix that cannot be parsed
     * matches nothing.
     */
    private static boolean destinationMatches(String allowedPrefix, String requestUrl) {
        final URI allowed;
        final URI request;
        try {
            allowed = new URI(allowedPrefix);
            request = new URI(requestUrl);
        } catch (URISyntaxException e) {
            return false;
        }
        if (allowed.getScheme() == null || request.getScheme() == null
                || !allowed.getScheme().equalsIgnoreCase(request.getScheme())) {
            return false;
        }
        if (allowed.getHost() == null || request.getHost() == null
                || !allowed.getHost().equalsIgnoreCase(request.getHost())) {
            return false;
        }
        if (effectivePort(allowed) != effectivePort(request)) {
            return false;
        }
        return pathWithinPrefix(allowed.getRawPath(), request.getRawPath());
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        val scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
        case "https", "wss" -> 443;
        case "http", "ws"   -> 80;
        default             -> -1;
        };
    }

    private static boolean pathWithinPrefix(@Nullable String prefixPath, @Nullable String requestPath) {
        val prefix  = prefixPath == null ? "" : prefixPath;
        val request = requestPath == null ? "" : requestPath;
        val trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (trimmed.isEmpty()) {
            return true;
        }
        return request.equals(trimmed) || request.startsWith(trimmed + "/");
    }

    private static @Nullable String baseUrlOf(ObjectValue requestSettings) {
        return requestSettings.get(BlockingWebClient.BASE_URL) instanceof TextValue(var url) ? url : null;
    }

    static ObjectValue mergeHeaders(AttributeAccessContext ctx, ObjectValue requestSettings) {
        val secretsKey          = extractSecretsKey(requestSettings);
        val subscriptionHeaders = resolveHttpHeaders(ctx.subscriptionSecrets(), secretsKey);
        val policyHeaders       = extractPolicyHeaders(requestSettings);
        val pdpHeaders          = resolveHttpHeaders(ctx.pdpSecrets(), secretsKey);

        if (subscriptionHeaders.isEmpty() && policyHeaders.isEmpty() && pdpHeaders.isEmpty()) {
            return stripSecretsKey(requestSettings);
        }

        // HTTP header names are case-insensitive, so a higher-priority source must
        // overwrite a lower-priority one even when the names differ only in case.
        // Canonicalize on lower case while keeping the winning source's exact name.
        val merged = new LinkedHashMap<String, Map.Entry<String, Value>>();
        applyHeaders(merged, subscriptionHeaders);
        applyHeaders(merged, policyHeaders);
        applyHeaders(merged, pdpHeaders);

        val headers = ObjectValue.builder();
        for (val entry : merged.values()) {
            headers.put(entry.getKey(), entry.getValue());
        }

        val builder = ObjectValue.builder();
        for (val entry : requestSettings.entrySet()) {
            if (!SECRETS_KEY.equals(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        builder.put(BlockingWebClient.HEADERS, headers.build());
        return builder.build();
    }

    private static void applyHeaders(LinkedHashMap<String, Map.Entry<String, Value>> merged, ObjectValue headers) {
        for (val entry : headers.entrySet()) {
            merged.put(entry.getKey().toLowerCase(Locale.ROOT), entry);
        }
    }

    private static ObjectValue resolveHttpHeaders(ObjectValue secrets, SecretsKeySelection secretsKey) {
        // Named secrets only. There is no unnamed flat default: an unbound
        // credential is exactly the "applies to any URL" shape this PIP must not
        // have. A request without a valid secretsKey contributes no secret headers.
        if (secrets == null || secrets.isEmpty() || !(secretsKey instanceof SecretsKeySelection.Named(var name))
                || !(secrets.get(SECRETS_HTTP) instanceof ObjectValue httpObj)
                || !(httpObj.get(name) instanceof ObjectValue namedObj)) {
            return Value.EMPTY_OBJECT;
        }
        val h = namedObj.get(SECRETS_HEADERS);
        return h instanceof ObjectValue hObj && !hObj.isEmpty() ? hObj : Value.EMPTY_OBJECT;
    }

    private static SecretsKeySelection extractSecretsKey(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(SECRETS_KEY)) {
            return new SecretsKeySelection.Absent();
        }
        val v = requestSettings.get(SECRETS_KEY);
        return v instanceof TextValue(var s) ? new SecretsKeySelection.Named(s) : new SecretsKeySelection.Malformed();
    }

    private static ObjectValue extractPolicyHeaders(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(BlockingWebClient.HEADERS)) {
            return Value.EMPTY_OBJECT;
        }
        val h = requestSettings.get(BlockingWebClient.HEADERS);
        return h instanceof ObjectValue obj && !obj.isEmpty() ? obj : Value.EMPTY_OBJECT;
    }

    private static ObjectValue stripSecretsKey(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(SECRETS_KEY)) {
            return requestSettings;
        }
        val builder = ObjectValue.builder();
        for (val entry : requestSettings.entrySet()) {
            if (!SECRETS_KEY.equals(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

}
