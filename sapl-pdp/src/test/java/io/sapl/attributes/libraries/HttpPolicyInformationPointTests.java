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

import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HttpPolicyInformationPoint")
class HttpPolicyInformationPointTests {

    private static final AttributeAccessContext EMPTY_CTX = new AttributeAccessContext(Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static final TextValue URL = (TextValue) Value.of("https://localhost:1234");

    @Nested
    @DisplayName("Environment attributes")
    class EnvironmentAttributes {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("delegates to client with correct HTTP method")
        void whenEnvironmentAttributeCalledThenDelegatesToClient(String name, HttpMethod expectedMethod,
                EnvironmentAttributeInvoker invoker) {
            val mockClient = mockHttpClient();
            val request    = baseRequest("https://localhost:8008");
            val pip        = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> invoker.invoke(pip, EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).httpRequest(expectedMethod, request);
        }

        static Stream<Arguments> whenEnvironmentAttributeCalledThenDelegatesToClient() {
            return Stream.of(
                    Arguments.of("GET", HttpMethod.GET, (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::get),
                    Arguments.of("POST", HttpMethod.POST,
                            (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::post),
                    Arguments.of("PUT", HttpMethod.PUT, (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::put),
                    Arguments.of("PATCH", HttpMethod.PATCH,
                            (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::patch),
                    Arguments.of("DELETE", HttpMethod.DELETE,
                            (EnvironmentAttributeInvoker) HttpPolicyInformationPoint::delete));
        }

        @Test
        @DisplayName("websocket delegates to consumeWebSocket")
        void whenEnvironmentWebSocketCalledThenConsumesWebSocket() {
            val mockClient = mockWebSocketClient();
            val request    = baseRequest("https://localhost:8008");
            val pip        = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> pip.websocket(EMPTY_CTX, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).consumeWebSocket(request);
        }
    }

    @Nested
    @DisplayName("Entity attributes")
    class EntityAttributes {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("delegates to client with merged URL")
        void whenEntityAttributeCalledThenDelegatesToClientWithMergedUrl(String name, HttpMethod expectedMethod,
                EntityAttributeInvoker invoker) {
            val mockClient      = mockHttpClient();
            val request         = baseRequest("https://localhost:8008");
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> invoker.invoke(pip, EMPTY_CTX, URL, request)).doesNotThrowAnyException();
            verify(mockClient, times(1)).httpRequest(expectedMethod, expectedRequest);
        }

        static Stream<Arguments> whenEntityAttributeCalledThenDelegatesToClientWithMergedUrl() {
            return Stream.of(
                    Arguments.of("GET", HttpMethod.GET, (EntityAttributeInvoker) HttpPolicyInformationPoint::get),
                    Arguments.of("POST", HttpMethod.POST, (EntityAttributeInvoker) HttpPolicyInformationPoint::post),
                    Arguments.of("PUT", HttpMethod.PUT, (EntityAttributeInvoker) HttpPolicyInformationPoint::put),
                    Arguments.of("PATCH", HttpMethod.PATCH, (EntityAttributeInvoker) HttpPolicyInformationPoint::patch),
                    Arguments.of("DELETE", HttpMethod.DELETE,
                            (EntityAttributeInvoker) HttpPolicyInformationPoint::delete));
        }

        @Test
        @DisplayName("websocket delegates to consumeWebSocket with merged URL")
        void whenWebSocketCalledWithUrlThenConsumesWebSocketWithMergedUrl() {
            val mockClient      = mockWebSocketClient();
            val request         = baseRequest("https://localhost:8008");
            val expectedRequest = baseRequest("https://localhost:1234");
            val pip             = new HttpPolicyInformationPoint(mockClient);

            assertThatCode(() -> pip.websocket(EMPTY_CTX, URL, request)).doesNotThrowAnyException();
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
    @DisplayName("Broker integration")
    class BrokerIntegration {

        @Test
        @DisplayName("broker loads http PIP library")
        void whenBrokerLoadsHttpPipThenLibraryIsAvailable() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);
            val pip        = new HttpPolicyInformationPoint(mockHttpClient());

            broker.loadPolicyInformationPointLibrary(pip);

            assertThat(broker.getLoadedLibraryNames()).contains("http");
        }

        @Test
        @DisplayName("loading unannotated class throws exception")
        void whenLoadLibraryWithoutAnnotationThenThrowsException() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);

            class NotAnnotated {
                @SuppressWarnings("unused")
                public Value someAttribute() {
                    return Value.of("test");
                }
            }

            assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NotAnnotated()))
                    .hasMessageContaining("must be annotated with @PolicyInformationPoint");
        }

        @Test
        @DisplayName("loading duplicate library throws exception")
        void whenLoadDuplicateLibraryThenThrowsException() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);
            val pip        = new HttpPolicyInformationPoint(mockHttpClient());

            broker.loadPolicyInformationPointLibrary(pip);

            assertThatThrownBy(
                    () -> broker.loadPolicyInformationPointLibrary(new HttpPolicyInformationPoint(mockHttpClient())))
                    .hasMessageContaining("Library already loaded: http");
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
                        MediaType.APPLICATION_JSON_VALUE);
                mockBackEnd.enqueue(mockResponse);

                val baseUrl    = "http://localhost:" + mockBackEnd.getPort();
                val pdpSecrets = namedHttpSecrets("weather-api", "X-API-Key", "abc123");
                val ctx        = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
                val request    = ObjectValue.builder().put("baseUrl", Value.of(baseUrl))
                        .put("accept", Value.of(MediaType.APPLICATION_JSON_VALUE))
                        .put("secretsKey", Value.of("weather-api")).put("pollingIntervalMs", Value.of(1000))
                        .put("repetitions", Value.of(1)).build();
                val realClient = new ReactiveWebClient(JsonMapper.builder().build());
                val pip        = new HttpPolicyInformationPoint(realClient);

                pip.get(ctx, request).blockFirst();

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
                        MediaType.APPLICATION_JSON_VALUE);
                mockBackEnd.enqueue(mockResponse);

                val baseUrl       = "http://localhost:" + mockBackEnd.getPort();
                val pdpSecrets    = httpSecrets("Authorization", "Bearer pdp-token");
                val ctx           = new AttributeAccessContext(Value.EMPTY_OBJECT, pdpSecrets, Value.EMPTY_OBJECT);
                val policyHeaders = ObjectValue.builder().put("Accept-Language", Value.of("de"))
                        .put("Authorization", Value.of("Bearer policy-token")).build();
                val request       = ObjectValue.builder().put("baseUrl", Value.of(baseUrl))
                        .put("accept", Value.of(MediaType.APPLICATION_JSON_VALUE)).put("headers", policyHeaders)
                        .put("pollingIntervalMs", Value.of(1000)).put("repetitions", Value.of(1)).build();
                val realClient    = new ReactiveWebClient(JsonMapper.builder().build());
                val pip           = new HttpPolicyInformationPoint(realClient);

                pip.get(ctx, request).blockFirst();

                val recorded = mockBackEnd.takeRequest(5, TimeUnit.SECONDS);
                assertThat(recorded).isNotNull();
                assertThat(recorded.getHeader("Accept-Language")).isEqualTo("de");
                assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer pdp-token");
            } finally {
                mockBackEnd.shutdown();
            }
        }
    }

    private static ReactiveWebClient mockHttpClient() {
        val defaultResponse = Flux.<Value>just(Value.of(1), Value.of(2), Value.of(3));
        val mockClient      = mock(ReactiveWebClient.class);
        when(mockClient.httpRequest(any(), any())).thenReturn(defaultResponse);
        return mockClient;
    }

    private static ReactiveWebClient mockWebSocketClient() {
        val defaultResponse = Flux.<Value>just(Value.of(1), Value.of(2), Value.of(3));
        val mockClient      = mock(ReactiveWebClient.class);
        when(mockClient.consumeWebSocket(any())).thenReturn(defaultResponse);
        return mockClient;
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
        Flux<Value> invoke(HttpPolicyInformationPoint pip, AttributeAccessContext ctx, ObjectValue requestSettings);
    }

    @FunctionalInterface
    interface EntityAttributeInvoker {
        Flux<Value> invoke(HttpPolicyInformationPoint pip, AttributeAccessContext ctx, TextValue url,
                ObjectValue requestSettings);
    }

}
