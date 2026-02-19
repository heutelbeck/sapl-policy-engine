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

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;
import static io.sapl.api.model.ValueJsonMarshaller.toJsonNode;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

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
            | `"https://api.example.com".<http.get(request)>` | Entity attribute, URL used as `baseUrl`. |
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
            | `pollingIntervalMs` | number | `1000` | Milliseconds between polling requests. |
            | `repetitions` | number | `Long.MAX_VALUE` | Upper bound for repeated requests. |
            | `secretsKey` | text | (none) | Selects a named credential set from secrets (see below). |

            The `secretsKey` field is metadata for credential selection and is stripped before
            the HTTP request is sent.

            ## Secrets Configuration

            HTTP credentials (API keys, bearer tokens, custom headers) are sourced from the
            `secrets` section in `pdp.json` and/or from subscription secrets. They are never
            embedded directly in policies.

            Header precedence (highest to lowest):
            1. **pdpSecrets** -- operator-configured secrets always win
            2. **Policy headers** -- headers specified in the `requestSettings` object
            3. **subscriptionSecrets** -- headers from the authorization subscription

            When headers from multiple sources use the same header name, the higher-priority
            source overwrites the lower-priority value.

            ### Named Credentials with `secretsKey`

            Use the `secretsKey` field in `requestSettings` to select which named credential
            set to use. For a request with `"secretsKey": "weather-api"`, the PDP resolves
            `secrets.http.weather-api.headers` from each secrets source.

            If the `secretsKey` is specified but the named entry does not exist in a given
            secrets source, no headers are contributed from that source (fail closed).

            ### Flat Fallback (no `secretsKey`)

            When no `secretsKey` is specified, the PDP falls back to `secrets.http.headers`
            as a flat default for each secrets source.

            ### Resolution Walkthrough

            For each secrets source (pdpSecrets and subscriptionSecrets):
            1. If `secretsKey` is present, look up `secrets.http.<secretsKey>.headers`.
            2. If `secretsKey` is absent, look up `secrets.http.headers`.
            3. If neither exists, no headers from that source.

            ### Multi-Service Secrets Example

            ```json
            {
              "variables": { },
              "secrets": {
                "http": {
                  "weather-api": {
                    "headers": { "X-API-Key": "abc123" }
                  },
                  "internal-api": {
                    "headers": { "Authorization": "Bearer infra-token" }
                  },
                  "headers": { "Authorization": "Bearer default-fallback" }
                }
              }
            }
            ```

            With this configuration:
            * A request with `"secretsKey": "weather-api"` gets header `X-API-Key: abc123`.
            * A request with `"secretsKey": "internal-api"` gets header
              `Authorization: Bearer infra-token`.
            * A request without `secretsKey` gets header
              `Authorization: Bearer default-fallback`.

            ### Subscription Secrets

            Subscription secrets follow the same structure and can be supplied per authorization
            subscription. They have the lowest priority and are overridden by both policy headers
            and pdpSecrets headers.

            ## Security

            Avoid embedding credentials directly in policy `headers`. Use the secrets
            configuration to keep credentials separate from policy logic. The `secretsKey`
            field itself is non-sensitive metadata and is safe to use in policies.

            ## Media Type Handling

            * `application/json`: Response body is parsed and mapped to a SAPL value.
            * `text/event-stream`: The PIP subscribes to server-sent events (SSEs) instead
              of polling.
            * Other types: Response body is returned as a text value.

            ## Timeouts

            Connection timeout is 10 seconds, read timeout is 30 seconds. Unresponsive
            endpoints result in an error value.
            """;

    private static final JsonNodeFactory JSON            = JsonNodeFactory.instance;
    private static final String          SECRETS_HEADERS = "headers";
    private static final String          SECRETS_HTTP    = "http";
    private static final String          SECRETS_KEY     = "secretsKey";

    private final ReactiveWebClient webClient;

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
              <http.get(request)>.status == "OK";
            ```
            """)
    public Flux<Value> get(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, mergeHeaders(ctx, requestSettings));
    }

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
    public Flux<Value> post(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, mergeHeaders(ctx, requestSettings));
    }

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
    public Flux<Value> put(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, mergeHeaders(ctx, requestSettings));
    }

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
    public Flux<Value> patch(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, mergeHeaders(ctx, requestSettings));
    }

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
    public Flux<Value> delete(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, mergeHeaders(ctx, requestSettings));
    }

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
    public Flux<Value> websocket(AttributeAccessContext ctx, ObjectValue requestSettings) {
        return webClient.consumeWebSocket(mergeHeaders(ctx, requestSettings));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> get(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.GET, withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> post(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.POST, withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> put(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PUT, withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> patch(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.PATCH, withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> delete(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.httpRequest(HttpMethod.DELETE, withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    @Attribute
    @EnvironmentAttribute(docs = """
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
    public Flux<Value> websocket(AttributeAccessContext ctx, TextValue resourceUrl, ObjectValue requestSettings) {
        return webClient.consumeWebSocket(withBaseUrl(resourceUrl, mergeHeaders(ctx, requestSettings)));
    }

    private static ObjectValue withBaseUrl(TextValue baseUrl, ObjectValue requestSettings) {
        val builder = ObjectValue.builder();
        builder.putAll(requestSettings);
        builder.put(ReactiveWebClient.BASE_URL, baseUrl);
        return builder.build();
    }

    static ObjectValue mergeHeaders(AttributeAccessContext ctx, ObjectValue requestSettings) {
        val secretsKey          = extractSecretsKey(requestSettings);
        val subscriptionHeaders = resolveHttpHeaders(ctx.subscriptionSecrets(), secretsKey);
        val policyHeaders       = extractPolicyHeaders(requestSettings);
        val pdpHeaders          = resolveHttpHeaders(ctx.pdpSecrets(), secretsKey);

        if (subscriptionHeaders.isEmpty() && policyHeaders.isEmpty() && pdpHeaders.isEmpty()) {
            return stripSecretsKey(requestSettings);
        }

        val merged = JSON.objectNode();
        if (!subscriptionHeaders.isEmpty()) {
            merged.setAll((ObjectNode) toJsonNode(subscriptionHeaders));
        }
        if (!policyHeaders.isEmpty()) {
            merged.setAll((ObjectNode) toJsonNode(policyHeaders));
        }
        if (!pdpHeaders.isEmpty()) {
            merged.setAll((ObjectNode) toJsonNode(pdpHeaders));
        }

        val builder = ObjectValue.builder();
        for (val entry : requestSettings.entrySet()) {
            if (!SECRETS_KEY.equals(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        builder.put(ReactiveWebClient.HEADERS, fromJsonNode(merged));
        return builder.build();
    }

    private static ObjectValue resolveHttpHeaders(ObjectValue secrets, String secretsKey) {
        if (secrets == null || secrets.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        val httpValue = secrets.get(SECRETS_HTTP);
        if (!(httpValue instanceof ObjectValue httpObj)) {
            return Value.EMPTY_OBJECT;
        }

        if (secretsKey != null) {
            val namedValue = httpObj.get(secretsKey);
            if (namedValue instanceof ObjectValue namedObj) {
                val h = namedObj.get(SECRETS_HEADERS);
                return h instanceof ObjectValue hObj && !hObj.isEmpty() ? hObj : Value.EMPTY_OBJECT;
            }
            return Value.EMPTY_OBJECT;
        }

        val h = httpObj.get(SECRETS_HEADERS);
        return h instanceof ObjectValue hObj && !hObj.isEmpty() ? hObj : Value.EMPTY_OBJECT;
    }

    private static String extractSecretsKey(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(SECRETS_KEY)) {
            return null;
        }
        val v = requestSettings.get(SECRETS_KEY);
        return v instanceof TextValue text ? text.value() : null;
    }

    private static ObjectValue extractPolicyHeaders(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(ReactiveWebClient.HEADERS)) {
            return Value.EMPTY_OBJECT;
        }
        val h = requestSettings.get(ReactiveWebClient.HEADERS);
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
