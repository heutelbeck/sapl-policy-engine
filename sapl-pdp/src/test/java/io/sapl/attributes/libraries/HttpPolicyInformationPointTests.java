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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.stream.Stream;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import lombok.val;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.sapl.util.SaplTesting.evaluate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpPolicyInformationPoint (vnext)")
class HttpPolicyInformationPointTests {

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static final TextValue URL = (TextValue) Value.of("https://localhost:1234");

    @Mock
    private BlockingWebClient mockClient;

    @Nested
    @DisplayName("Environment attributes")
    class EnvironmentAttributes {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("delegates to client with correct HTTP method")
        void whenEnvironmentAttributeCalledThenDelegatesToClient(String name, String expectedMethod,
                EnvironmentAttributeInvoker invoker) {
            stubHttpRequest(mockClient);
            val request = baseRequest("https://localhost:8008");
            val pip     = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> invoker.invoke(pip, EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).httpRequest(expectedMethod, request);
        }

        static java.util.stream.Stream<Arguments> whenEnvironmentAttributeCalledThenDelegatesToClient() {
            return java.util.stream.Stream.of(
                    arguments("GET", "GET", (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::get),
                    arguments("POST", "POST", (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::post),
                    arguments("PUT", "PUT", (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::put),
                    arguments("PATCH", "PATCH", (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::patch),
                    arguments("DELETE", "DELETE", (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::delete));
        }

        @Test
        @DisplayName("websocket delegates to consumeWebSocket")
        void whenEnvironmentWebSocketCalledThenConsumesWebSocket() {
            stubWebSocket(mockClient);
            val request = baseRequest("https://localhost:8008");
            val pip     = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> pip.websocket(EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).consumeWebSocket(request);
        }
    }

    @Nested
    @DisplayName("Entity attributes with request settings")
    class EntityAttributesWithSettings {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("delegates to client with merged URL")
        void whenEntityAttributeCalledThenDelegatesToClientWithMergedUrl(String name, String expectedMethod,
                EntityAttributeInvoker invoker) {
            stubHttpRequest(mockClient);
            val request         = baseRequest("https://localhost:8008");
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> invoker.invoke(pip, URL, EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).httpRequest(expectedMethod, expectedRequest);
        }

        static java.util.stream.Stream<Arguments> whenEntityAttributeCalledThenDelegatesToClientWithMergedUrl() {
            return java.util.stream.Stream.of(
                    arguments("GET", "GET", (EntityAttributeInvoker) HttpPolicyInformationPoint::get),
                    arguments("POST", "POST", (EntityAttributeInvoker) HttpPolicyInformationPoint::post),
                    arguments("PUT", "PUT", (EntityAttributeInvoker) HttpPolicyInformationPoint::put),
                    arguments("PATCH", "PATCH", (EntityAttributeInvoker) HttpPolicyInformationPoint::patch),
                    arguments("DELETE", "DELETE", (EntityAttributeInvoker) HttpPolicyInformationPoint::delete));
        }

        @Test
        @DisplayName("websocket delegates to consumeWebSocket with merged URL")
        void whenWebSocketCalledWithUrlThenConsumesWebSocketWithMergedUrl() {
            stubWebSocket(mockClient);
            val request         = baseRequest("https://localhost:8008");
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> pip.websocket(URL, EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).consumeWebSocket(expectedRequest);
        }
    }

    @Nested
    @DisplayName("Entity attributes without request settings")
    class EntityAttributesNoArgs {

        @MethodSource
        @ParameterizedTest(name = "{0}")
        @DisplayName("no-args overload delegates with empty settings")
        void whenEntityAttributeCalledWithoutSettingsThenDelegatesWithEmptyObject(String name, String expectedMethod,
                NoArgsEntityAttributeInvoker invoker) {
            stubHttpRequest(mockClient);
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> invoker.invoke(pip, URL, EMPTY_CTX)).doesNotThrowAnyException();
            verify(mockClient, times(1)).httpRequest(expectedMethod, expectedRequest);
        }

        static java.util.stream.Stream<Arguments> whenEntityAttributeCalledWithoutSettingsThenDelegatesWithEmptyObject() {
            return java.util.stream.Stream.of(
                    arguments("GET", "GET", (NoArgsEntityAttributeInvoker) HttpPolicyInformationPoint::get),
                    arguments("POST", "POST", (NoArgsEntityAttributeInvoker) HttpPolicyInformationPoint::post),
                    arguments("PUT", "PUT", (NoArgsEntityAttributeInvoker) HttpPolicyInformationPoint::put),
                    arguments("PATCH", "PATCH", (NoArgsEntityAttributeInvoker) HttpPolicyInformationPoint::patch),
                    arguments("DELETE", "DELETE", (NoArgsEntityAttributeInvoker) HttpPolicyInformationPoint::delete));
        }

        @Test
        @DisplayName("websocket no-args overload delegates with empty settings")
        void whenWebSocketCalledWithUrlAndNoSettingsThenConsumesWithEmptyObject() {
            stubWebSocket(mockClient);
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> pip.websocket(URL, EMPTY_CTX)).doesNotThrowAnyException();
            verify(mockClient, times(1)).consumeWebSocket(expectedRequest);
        }
    }

    @Nested
    @DisplayName("Header merging from secrets")
    class HeaderMerging {

        @Test
        @DisplayName("pdpSecrets HTTP headers appear in merged request")
        void whenPdpSecretsHaveHttpHeadersThenHeadersInRequest() {
            val pdpSecrets = httpSecrets("X-Api-Key", "secret123");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = baseRequest("https://example.com");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers).isNotNull();
            assertThat(((TextValue) headers.get("X-Api-Key")).value()).isEqualTo("secret123");
        }

        @Test
        @DisplayName("subscriptionSecrets HTTP headers appear in merged request")
        void whenSubscriptionSecretsHaveHttpHeadersThenHeadersInRequest() {
            val subSecrets = httpSecrets("X-Sub-Key", "sub-value");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, subSecrets);
            val request    = baseRequest("https://example.com");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers).isNotNull();
            assertThat(((TextValue) headers.get("X-Sub-Key")).value()).isEqualTo("sub-value");
        }

        @Test
        @DisplayName("pdpSecrets override policy headers on conflict")
        void whenPdpAndPolicyOverlapThenPdpWins() {
            val pdpSecrets = httpSecrets("Authorization", "Bearer pdp-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithHeaders("https://example.com", "Authorization", "Bearer policy-token");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("Authorization")).value()).isEqualTo("Bearer pdp-token");
        }

        @Test
        @DisplayName("policy headers override subscriptionSecrets on conflict")
        void whenSubscriptionAndPolicyOverlapThenPolicyWins() {
            val subSecrets = httpSecrets("Authorization", "Bearer sub-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, subSecrets);
            val request    = requestWithHeaders("https://example.com", "Authorization", "Bearer policy-token");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("Authorization")).value()).isEqualTo("Bearer policy-token");
        }

        @Test
        @DisplayName("pdpSecrets override both policy and subscription on three-way conflict")
        void whenAllThreeSourcesOverlapThenPdpWins() {
            val pdpSecrets = httpSecrets("Authorization", "Bearer pdp-token");
            val subSecrets = httpSecrets("Authorization", "Bearer sub-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, subSecrets);
            val request    = requestWithHeaders("https://example.com", "Authorization", "Bearer policy-token");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("Authorization")).value()).isEqualTo("Bearer pdp-token");
        }

        @Test
        @DisplayName("without any secrets only policy headers are used")
        void whenNoSecretsThenOnlyPolicyHeaders() {
            val request = requestWithHeaders("https://example.com", "Accept-Language", "en");

            val merged = HttpPolicyInformationPoint.mergeHeaders(EMPTY_CTX, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers).isNotNull();
            assertThat(((TextValue) headers.get("Accept-Language")).value()).isEqualTo("en");
        }

        @Test
        @DisplayName("secrets without http key yield only policy headers")
        void whenSecretsHaveNoHttpKeyThenOnlyPolicyHeaders() {
            val pdpSecrets = ObjectValue.builder().put("other", Value.of("irrelevant")).build();
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithHeaders("https://example.com", "X-Custom", "value");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("X-Custom")).value()).isEqualTo("value");
        }

        @Test
        @DisplayName("empty headers in secrets yield only policy headers")
        void whenEmptyHeadersInSecretsThenOnlyPolicyHeaders() {
            val httpObj    = ObjectValue.builder().put("headers", Value.EMPTY_OBJECT).build();
            val pdpSecrets = ObjectValue.builder().put("http", httpObj).build();
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithHeaders("https://example.com", "X-Custom", "value");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("X-Custom")).value()).isEqualTo("value");
        }

        @Test
        @DisplayName("operator pdp secret overrides a case-varied policy header so only one credential survives")
        void whenPolicyVariesHeaderCaseThenPdpSecretStillOverridesAndOnlyOneSurvives() {
            val pdpSecrets = httpSecrets("Authorization", "Bearer operator-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithHeaders("https://example.com", "authorization", "Bearer attacker-token");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers.entrySet()).singleElement()
                    .satisfies(e -> assertThat(e.getKey()).isEqualToIgnoringCase("authorization"))
                    .satisfies(e -> assertThat(((TextValue) e.getValue()).value()).isEqualTo("Bearer operator-token"));
        }

        @Test
        @DisplayName("policy header overrides a case-varied subscription secret so only one credential survives")
        void whenSubscriptionVariesHeaderCaseThenPolicyStillOverridesAndOnlyOneSurvives() {
            val subSecrets = httpSecrets("authorization", "Bearer sub-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, subSecrets);
            val request    = requestWithHeaders("https://example.com", "Authorization", "Bearer policy-token");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers.entrySet()).singleElement()
                    .satisfies(e -> assertThat(e.getKey()).isEqualToIgnoringCase("authorization"))
                    .satisfies(e -> assertThat(((TextValue) e.getValue()).value()).isEqualTo("Bearer policy-token"));
        }
    }

    @Nested
    @DisplayName("Named credential selection via secretsKey")
    class SecretKeyResolution {

        @Test
        @DisplayName("secretsKey selects named credential set")
        void whenSecretsKeyMatchesNamedEntryThenUsesNamedHeaders() {
            val pdpSecrets = namedHttpSecrets("weather-api", "X-API-Key", "abc123");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithSecretsKey("https://example.com", "weather-api");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers).isNotNull();
            assertThat(((TextValue) headers.get("X-API-Key")).value()).isEqualTo("abc123");
        }

        @Test
        @DisplayName("unmatched secretsKey yields no headers from that source")
        void whenSecretsKeyDoesNotMatchThenNoHeadersFromThatSource() {
            val pdpSecrets = namedHttpSecrets("weather-api", "X-API-Key", "abc123");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithSecretsKey("https://example.com", "unknown-api");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            assertThat(merged.containsKey("headers")).isFalse();
        }

        @Test
        @DisplayName("without secretsKey uses flat default headers")
        void whenNoSecretsKeyThenUsesFlatDefaultHeaders() {
            val pdpSecrets = httpSecrets("Authorization", "Bearer default-token");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = baseRequest("https://example.com");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("Authorization")).value()).isEqualTo("Bearer default-token");
        }

        @Test
        @DisplayName("secretsKey is stripped from merged request settings")
        void whenSecretsKeySpecifiedThenStrippedFromRequestSettings() {
            val pdpSecrets = namedHttpSecrets("my-api", "X-Key", "val");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = requestWithSecretsKey("https://example.com", "my-api");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            assertThat(merged.containsKey("secretsKey")).isFalse();
        }

        @Test
        @DisplayName("pdp named headers win over subscription flat headers")
        void whenPdpHasNamedAndSubscriptionHasFlatThenPdpNamedWins() {
            val pdpSecrets = namedHttpSecrets("my-api", "Authorization", "Bearer pdp-named");
            val subSecrets = httpSecrets("Authorization", "Bearer sub-flat");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, subSecrets);
            val request    = requestWithSecretsKey("https://example.com", "my-api");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(((TextValue) headers.get("Authorization")).value()).isEqualTo("Bearer pdp-named");
        }

        @Test
        @DisplayName("present but non-text secretsKey fails closed instead of using flat default headers")
        void whenSecretsKeyPresentButNotTextThenNoHeadersFromThatSource() {
            val pdpSecrets = httpSecrets("Authorization", "Bearer default-fallback");
            val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request    = ObjectValue.builder().put("baseUrl", Value.of("https://example.com"))
                    .put("secretsKey", Value.of(123)).build();

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            assertThat(merged.containsKey("headers")).isFalse();
        }

        @Test
        @DisplayName("correct named service selected when multiple configured")
        void whenMultipleServicesConfiguredThenCorrectOneSelected() {
            val weatherHeaders  = ObjectValue.builder()
                    .put("headers", ObjectValue.builder().put("X-Weather-Key", Value.of("weather-val")).build())
                    .build();
            val internalHeaders = ObjectValue.builder()
                    .put("headers", ObjectValue.builder().put("X-Internal-Key", Value.of("internal-val")).build())
                    .build();
            val httpObj         = ObjectValue.builder().put("weather-api", weatherHeaders)
                    .put("internal-api", internalHeaders).build();
            val pdpSecrets      = ObjectValue.builder().put("http", httpObj).build();
            val ctx             = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
            val request         = requestWithSecretsKey("https://example.com", "internal-api");

            val merged = HttpPolicyInformationPoint.mergeHeaders(ctx, request);

            val headers = (ObjectValue) merged.get("headers");
            assertThat(headers.containsKey("X-Internal-Key")).isTrue();
            assertThat(headers.containsKey("X-Weather-Key")).isFalse();
            assertThat(((TextValue) headers.get("X-Internal-Key")).value()).isEqualTo("internal-val");
        }
    }

    @Nested
    @DisplayName("End-to-end secrets header injection")
    class EndToEndSecretsHeaderInjection {

        @Test
        @DisplayName("secrets headers arrive on the wire via MockWebServer")
        void whenSecretsConfiguredThenHeadersArriveOnWire() throws IOException, InterruptedException {
            val mockBackEnd = new MockWebServer();
            mockBackEnd.start();
            try {
                val mockResponse = new MockResponse().setBody("{\"status\":\"ok\"}").addHeader("Content-Type",
                        "application/json");
                mockBackEnd.enqueue(mockResponse);

                val baseUrl    = "http://localhost:" + mockBackEnd.getPort();
                val pdpSecrets = namedHttpSecrets("weather-api", "X-API-Key", "abc123");
                val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
                val request    = ObjectValue.builder().put("baseUrl", Value.of(baseUrl))
                        .put("accept", Value.of("application/json")).put("secretsKey", Value.of("weather-api")).build();
                val realClient = newRealClient();
                val pip        = new HttpPolicyInformationPoint(realClient);

                try (val stream = pip.get(ctx, request)) {
                    StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                            .awaitsNext(v -> assertThat(v).isNotNull());
                }

                val recorded = mockBackEnd.takeRequest(5, TimeUnit.SECONDS);
                assertThat(recorded).isNotNull();
                assertThat(recorded.getHeader("X-API-Key")).isEqualTo("abc123");
                assertThat(recorded.getRequestUrl().toString()).doesNotContain("secretsKey");
            } finally {
                mockBackEnd.shutdown();
            }
        }

        @Test
        @DisplayName("policy and secrets headers both arrive with correct precedence")
        void whenPolicyAndSecretsHeadersThenBothArriveWithPrecedence() throws IOException, InterruptedException {
            val mockBackEnd = new MockWebServer();
            mockBackEnd.start();
            try {
                val mockResponse = new MockResponse().setBody("{\"status\":\"ok\"}").addHeader("Content-Type",
                        "application/json");
                mockBackEnd.enqueue(mockResponse);

                val baseUrl       = "http://localhost:" + mockBackEnd.getPort();
                val pdpSecrets    = httpSecrets("Authorization", "Bearer pdp-token");
                val ctx           = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
                val policyHeaders = ObjectValue.builder().put("Accept-Language", Value.of("de"))
                        .put("Authorization", Value.of("Bearer policy-token")).build();
                val request       = ObjectValue.builder().put("baseUrl", Value.of(baseUrl))
                        .put("accept", Value.of("application/json")).put("headers", policyHeaders).build();
                val realClient    = newRealClient();
                val pip           = new HttpPolicyInformationPoint(realClient);

                try (val stream = pip.get(ctx, request)) {
                    StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                            .awaitsNext(v -> assertThat(v).isNotNull());
                }

                val recorded = mockBackEnd.takeRequest(5, TimeUnit.SECONDS);
                assertThat(recorded).isNotNull();
                assertThat(recorded.getHeader("Accept-Language")).isEqualTo("de");
                assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer pdp-token");
            } finally {
                mockBackEnd.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("broker registration")
    class StoreRegistration {

        @Test
        @DisplayName("loads under the http namespace without errors")
        void whenLoadedIntoStoreThenRegistersUnderHttpNamespace() {
            try (val broker = new PolicyInformationPointAttributeBroker()) {
                val mapper    = JsonMapper.builder().build();
                val webClient = new BlockingWebClient(mapper, HttpClient.newHttpClient());
                val handle    = broker.load(new HttpPolicyInformationPoint(webClient));

                assertThat(handle.pipName()).isEqualTo("http");
                assertThat(handle.isLoaded()).isTrue();
                assertThat(broker.catalog()).containsExactly(handle);
            }
        }
    }

    @Test
    @DisplayName("the pollIntervalMs attribute option drives the http.get invocation poll interval end to end")
    void whenPollIntervalOptionOnHttpAttributeThenInvocationCarriesIt() {
        val invocation = evaluate("<http.get({\"baseUrl\": \"https://example.com\"})[{pollIntervalMs: 250}]>")
                .with("http.get", Value.of("ok")).onlyInvocation();

        assertThat(invocation.attributeName()).isEqualTo("http.get");
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    @DisplayName("the broker re-invokes the http attribute at the configured poll interval (repetition end to end)")
    void whenSubscribedWithShortPollIntervalThenBrokerReIssuesRequests() throws IOException {
        val server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                return new MockResponse().setBody("{\"v\":1}").addHeader("Content-Type", "application/json");
            }
        });
        server.start();
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            broker.load(new HttpPolicyInformationPoint(
                    new BlockingWebClient(JsonMapper.builder().build(), HttpClient.newHttpClient())));
            val request    = ObjectValue.builder().put("baseUrl", Value.of("http://localhost:" + server.getPort()))
                    .build();
            val invocation = new AttributeFinderInvocation("default", "http.get", List.of(request),
                    Duration.ofSeconds(1), Duration.ofMillis(50), Duration.ofMillis(50), 0L, false, EMPTY_CTX);
            val key        = new SubscriptionKey(invocation, false);

            val subscription = broker.open("poll-e2e", Set.of(key), snapshot -> Set.of(key));
            try {
                // A single-shot HTTP attribute re-issued by the broker poll interval must
                // produce multiple requests, no in-PIP looping.
                Awaitility.await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(() -> assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(2));
            } finally {
                subscription.close();
            }
        } finally {
            server.shutdown();
        }
    }

    private static void stubHttpRequest(BlockingWebClient client) {
        when(client.httpRequest(any(), any())).thenAnswer(invocation -> emittingStream());
    }

    private static void stubWebSocket(BlockingWebClient client) {
        when(client.consumeWebSocket(any())).thenAnswer(invocation -> emittingStream());
    }

    private static Stream<Value> emittingStream() {
        val s = new LatestSlotStream<Value>();
        s.put(Value.of(1));
        s.complete();
        return s;
    }

    private static BlockingWebClient newRealClient() {
        return new BlockingWebClient(JsonMapper.builder().build(), HttpClient.newHttpClient());
    }

    private static ObjectValue baseRequest(String url) {
        return ObjectValue.builder().put("baseUrl", Value.of(url)).build();
    }

    private static ObjectValue requestWithHeaders(String url, String headerName, String headerValue) {
        val headers = ObjectValue.builder().put(headerName, Value.of(headerValue)).build();
        return ObjectValue.builder().put("baseUrl", Value.of(url)).put("headers", headers).build();
    }

    private static ObjectValue requestWithSecretsKey(String url, String secretsKey) {
        return ObjectValue.builder().put("baseUrl", Value.of(url)).put("secretsKey", Value.of(secretsKey)).build();
    }

    private static ObjectValue httpSecrets(String headerName, String headerValue) {
        val headers = ObjectValue.builder().put(headerName, Value.of(headerValue)).build();
        val httpObj = ObjectValue.builder().put("headers", headers).build();
        return ObjectValue.builder().put("http", httpObj).build();
    }

    private static ObjectValue namedHttpSecrets(String name, String headerName, String headerValue) {
        val headers  = ObjectValue.builder().put(headerName, Value.of(headerValue)).build();
        val namedObj = ObjectValue.builder().put("headers", headers).build();
        val httpObj  = ObjectValue.builder().put(name, namedObj).build();
        return ObjectValue.builder().put("http", httpObj).build();
    }

    @FunctionalInterface
    interface EnvironmentAttributeInvoker {
        Stream<Value> invoke(HttpPolicyInformationPoint pip, AttributeAccessContext ctx, ObjectValue requestSettings);
    }

    @FunctionalInterface
    interface EntityAttributeInvoker {
        Stream<Value> invoke(HttpPolicyInformationPoint pip, TextValue url, AttributeAccessContext ctx,
                ObjectValue requestSettings);
    }

    @FunctionalInterface
    interface NoArgsEntityAttributeInvoker {
        Stream<Value> invoke(HttpPolicyInformationPoint pip, TextValue url, AttributeAccessContext ctx);
    }

}
