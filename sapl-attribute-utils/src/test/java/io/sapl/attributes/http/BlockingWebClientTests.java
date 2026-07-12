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
package io.sapl.attributes.http;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.RequiredArgsConstructor;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.HttpResponse.ResponseInfo;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BlockingWebClient")
class BlockingWebClientTests {

    private static final JsonMapper   MAPPER           = JsonMapper.builder().build();
    private static final String       DEFAULT_BODY     = """
            {"message":"success"}""";
    private static final MockResponse DEFAULT_RESPONSE = new MockResponse().setBody(DEFAULT_BODY)
            .addHeader("Content-Type", "application/json");
    private MockWebServer             server;
    private String                    baseUrl;
    private BlockingWebClient         client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = String.format("http://localhost:%s", server.getPort());
        client  = new BlockingWebClient(MAPPER, HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String toJsonString(Value v) {
        try {
            return MAPPER.writeValueAsString(ValueJsonMarshaller.toJsonNode(v));
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectValue defaultRequest(String mimeType) {
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s"
                }
                """;
        return (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl, mimeType));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "GET", "POST", "PUT", "PATCH", "DELETE" })
    @DisplayName("an HTTP method issues exactly one request, emits the response, then completes")
    void whenHttpMethodCalledThenEmitsResponseOnceAndCompletes(String method) throws InterruptedException {
        server.enqueue(DEFAULT_RESPONSE);

        try (val stream = client.httpRequest(method, defaultRequest("application/json"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }

        assertThat(server.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("URL parameters are URL-encoded and headers are forwarded")
    void whenUrlParamsAndHeadersThenParamsAndHeadersInRequest() throws InterruptedException {
        server.enqueue(DEFAULT_RESPONSE);
        val template = """
                {
                    "baseUrl" : "%s",
                    "path" : "/rainbow",
                    "accept" : "application/json",
                    "urlParameters" : {
                        "willi":"wurst",
                        "hänschen":"klein"
                    },
                    "headers" : {
                        "X-A": "einmal",
                        "X-B": [ "a","b","c" ]
                    }
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }

        val recorded = server.takeRequest(1L, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        val url     = recorded.getRequestUrl().toString();
        val headers = recorded.getHeaders().toMultimap();

        assertThat(url).contains("willi=wurst").contains("h%C3%A4nschen=klein").contains("/rainbow?");
        assertThat(headers.get("X-A")).contains("einmal");
        assertThat(headers.get("X-B")).contains("a", "b", "c");
    }

    @Test
    @DisplayName("a malformed base URL never leaks a query-string secret into the error value")
    void whenMalformedBaseUrlWithSecretThenErrorValueDoesNotLeakToken() {
        val secret   = "SUPERSECRET-TOKEN-123";
        val template = """
                {
                    "baseUrl" : "http://bad host:8082",
                    "path" : "/api/positions",
                    "accept" : "application/json",
                    "urlParameters" : {
                        "token" : "%s"
                    }
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(secret));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).doesNotContain(secret);
            });
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/api/SUPERSECRET-TOKEN-123/positions", "#SUPERSECRET-TOKEN-123" })
    @DisplayName("a malformed URL never leaks a path-segment or fragment secret into the error value")
    void whenMalformedUrlWithSecretInPathOrFragmentThenErrorValueDoesNotLeakToken(String secretBearingSuffix) {
        val secret   = "SUPERSECRET-TOKEN-123";
        val template = """
                {
                    "baseUrl" : "http://bad host:8082",
                    "path" : "%s",
                    "accept" : "application/json"
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(secretBearingSuffix));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).doesNotContain(secret);
            });
        }
    }

    @Test
    @DisplayName("a malformed URL with an '@' in the userinfo password never leaks the password tail")
    void whenMalformedUrlWithAtInPasswordThenErrorValueDoesNotLeakPassword() {
        val passwordTail = "ssword";
        val template     = """
                {
                    "baseUrl" : "http://user:p@ssword@bad host:8082",
                    "path" : "/api/positions",
                    "accept" : "application/json"
                }
                """;
        val request      = (ObjectValue) ValueJsonMarshaller.json(template.formatted());

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).doesNotContain(passwordTail);
            });
        }
    }

    @Test
    @DisplayName("an SSE producer's unchecked transport failure fails closed to an error value")
    void whenServerSentEventProducerThrowsUncheckedThenErrorValue() {
        val failingClient = new BlockingWebClient(MAPPER, new ThrowingHttpClient());
        val template      = """
                {
                    "baseUrl" : "http://localhost:1",
                    "accept" : "text/event-stream"
                }
                """;
        val request       = (ObjectValue) ValueJsonMarshaller.json(template.formatted());

        val drained = StreamAssertions.assertThat(failingClient.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();
        assertThat(drained).anySatisfy(v -> assertThat(v).isInstanceOf(ErrorValue.class));
    }

    @Test
    @DisplayName("a multi-byte SSE event over the byte budget fails closed even when its char count is under it")
    void whenServerSentEventExceedsMaxResponseBytesInUtf8ButNotCharCountThenErrorValue() {
        val multiByteChars = "€".repeat(40);
        val eventStream    = "data:" + multiByteChars + "\n\n";
        server.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream",
                    "maxResponseBytes" : 64
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        val drained = StreamAssertions.assertThat(client.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();
        assertThat(drained).anySatisfy(v -> {
            if (!(v instanceof ErrorValue err)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
            assertThat(err.message()).contains("64");
        });
    }

    @Test
    @DisplayName("HTTP 500 response emits an error value")
    void whenHttpErrorThenEmitsErrorValue() {
        val errorResponse = new MockResponse().setResponseCode(500).setHeader("content-type", "application/json")
                .setBody("{}");
        server.enqueue(errorResponse);

        try (val stream = client.httpRequest("GET", defaultRequest("application/json"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("500");
            });
        }
    }

    @Test
    @DisplayName("a response body exceeding maxResponseBytes fails closed to an error value")
    void whenResponseExceedsMaxResponseBytesThenErrorValue() {
        val oversized = "{\"big\":\"" + "x".repeat(5000) + "\"}";
        server.enqueue(new MockResponse().setBody(oversized).addHeader("Content-Type", "application/json"));
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "maxResponseBytes" : 64
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("64");
            });
        }
    }

    @Test
    @DisplayName("XML response is returned as a text value")
    void whenFetchingXmlThenIsTextValue() {
        val minimalXml  = "<a/>";
        val xmlResponse = new MockResponse().setBody(minimalXml).addHeader("Content-Type", "application/xml");
        server.enqueue(xmlResponse);

        try (val stream = client.httpRequest("GET", defaultRequest("application/xml"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof TextValue text)) {
                    throw new AssertionError("Expected TextValue, got: " + v);
                }
                assertThat(text.value()).isEqualTo(minimalXml);
            });
        }
    }

    @Test
    @DisplayName("XML response when JSON expected emits an error value")
    void whenXmlReturnedWhileJsonExpectedThenError() {
        val xmlResponse = new MockResponse().setBody("<a/>").addHeader("Content-Type", "application/xml");
        server.enqueue(xmlResponse);

        try (val stream = client.httpRequest("GET", defaultRequest("application/json"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
            });
        }
    }

    @Test
    @DisplayName("missing polling interval falls back to the default")
    void whenPollingIntervalNotDefinedThenFallsBackToDefault() {
        server.enqueue(DEFAULT_RESPONSE);
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json"
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }
    }

    @Test
    @DisplayName("POST with body forwards the body and emits the response")
    void whenHttpPostWithBodyThenEmitsResponse() throws InterruptedException {
        server.enqueue(DEFAULT_RESPONSE);
        val template = """
                {
                    "baseUrl" : "%s",
                    "body" : { "some" : "data", "andMoreData" : 123 },
                    "accept" : "application/json"
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("POST", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }

        val recorded = server.takeRequest(1L, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getBody().readUtf8()).contains("\"some\":\"data\"");
    }

    @Test
    @DisplayName("non-numeric maxResponseBytes emits an error value")
    void whenMaxResponseBytesNotANumberThenError() {
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "maxResponseBytes" : null
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("POST", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("maxResponseBytes").contains("number");
            });
        }
    }

    @ParameterizedTest(name = "maxResponseBytes={0}")
    @ValueSource(longs = { 0L, -1L })
    @DisplayName("a non-positive maxResponseBytes is rejected with an error value")
    void whenMaxResponseBytesNotPositiveThenError(long maxBytes) {
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "maxResponseBytes" : %d
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl, maxBytes));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("maxResponseBytes");
            });
        }
    }

    @Test
    @DisplayName("a huge maxResponseBytes does not overflow the read limit and still delivers the body")
    void whenMaxResponseBytesIsHugeThenBodyIsStillDelivered() {
        server.enqueue(DEFAULT_RESPONSE);
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "maxResponseBytes" : 9223372036854775807
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("GET", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }
    }

    @Test
    @DisplayName("missing base URL emits an error value")
    void whenNoBaseUrlThenError() {
        try (val stream = client.httpRequest("POST", Value.EMPTY_OBJECT)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("base URL");
            });
        }
    }

    @Test
    @DisplayName("server-sent events are parsed and emitted")
    void whenServerSentEventsThenReceivesEventStream() {
        val eventStream = "id:id1\nevent:event1\ndata:" + DEFAULT_BODY + "\n\n" + "id:id2\nevent:event2\ndata:"
                + DEFAULT_BODY + "\n\n" + "id:id3\nevent:event3\ndata:" + DEFAULT_BODY + "\n\n";
        server.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream"
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        val drained = StreamAssertions.assertThat(client.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();
        assertThat(drained).isNotEmpty().allSatisfy(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
    }

    @Test
    @DisplayName("an SSE event split across data fields is limited by emitted payload bytes")
    void whenServerSentEventPayloadFitsAcrossManyDataFieldsThenEventIsEmitted() {
        val expectedPayload = "{\"message\":\n\"success\"}";
        val eventStream     = "data:{\"message\":\ndata:\"success\"}\n\n";
        val maxBytes        = expectedPayload.getBytes(StandardCharsets.UTF_8).length;
        server.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream",
                    "maxResponseBytes" : %d
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl, maxBytes));

        val drained = StreamAssertions.assertThat(client.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();

        assertThat(drained).singleElement().satisfies(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
    }

    @Test
    @DisplayName("an unterminated SSE line aborts the read once the byte cap is crossed without unbounded buffering")
    void whenServerSentEventLineHasNoTerminatorThenAbortsReadOnceCapCrossedAndFailsClosed() {
        // A hostile SSE host streams a single newline-free line forever. A bounded
        // reader must abort once the cap is crossed. It must NOT read the line into
        // memory unbounded (which would OOM the PDP). The stream counts the bytes it
        // serves and asserts the client stops far below an unbounded read.
        val maxBytes       = 64L;
        val countingStream = new CountingInfiniteStream();
        val countingClient = new BlockingWebClient(MAPPER, new InputStreamHttpClient(countingStream), maxBytes);
        val template       = """
                {
                    "baseUrl" : "http://localhost:1",
                    "accept" : "text/event-stream",
                    "maxResponseBytes" : %d
                }
                """;
        val request        = (ObjectValue) ValueJsonMarshaller.json(template.formatted(maxBytes));

        val drained = StreamAssertions.assertThat(countingClient.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(5L)).drain();

        assertThat(drained).anySatisfy(v -> {
            if (!(v instanceof ErrorValue err)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
            assertThat(err.message()).contains("64");
        });
        assertThat(countingStream.bytesServed()).isLessThan(1_000_000L);
    }

    @Test
    @DisplayName("an SSE event exceeding maxResponseBytes fails closed to an error value")
    void whenServerSentEventExceedsMaxResponseBytesThenErrorValue() {
        val eventStream = "data:" + "x".repeat(5000) + "\n\n";
        server.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream",
                    "maxResponseBytes" : 64
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        val drained = StreamAssertions.assertThat(client.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();
        assertThat(drained).anySatisfy(v -> {
            if (!(v instanceof ErrorValue err)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
            assertThat(err.message()).contains("64");
        });
    }

    @Test
    @DisplayName("a one-shot request against a failing endpoint closes the response body so no connection leaks")
    void whenOneShotRequestReturnsErrorStatusThenResponseBodyIsClosed() {
        val bodyStream    = new TrackingInputStream("{}");
        val failingClient = new BlockingWebClient(MAPPER, new StatusInputStreamHttpClient(503, bodyStream));

        try (val stream = failingClient.httpRequest("GET", defaultRequest("application/json"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("503");
            });
        }

        assertThat(bodyStream.wasClosed()).isTrue();
    }

    @Test
    @DisplayName("an SSE source against a failing endpoint closes the response body so no connection leaks")
    void whenServerSentEventSourceReturnsErrorStatusThenResponseBodyIsClosed() {
        val bodyStream    = new TrackingInputStream("");
        val failingClient = new BlockingWebClient(MAPPER, new StatusInputStreamHttpClient(503, bodyStream));
        val template      = """
                {
                    "baseUrl" : "http://localhost:1",
                    "accept" : "text/event-stream"
                }
                """;
        val request       = (ObjectValue) ValueJsonMarshaller.json(template.formatted());

        val drained = StreamAssertions.assertThat(failingClient.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();

        assertThat(drained).anySatisfy(v -> {
            if (!(v instanceof ErrorValue err)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
            assertThat(err.message()).contains("503");
        });
        assertThat(bodyStream.wasClosed()).isTrue();
    }

    @Test
    @DisplayName("a fragmented WebSocket message within maxResponseBytes is emitted when complete")
    void whenFragmentedWebSocketMessageWithinMaxResponseBytesThenPayloadIsEmitted() {
        val payload          = DEFAULT_BODY;
        val fragmentedClient = new BlockingWebClient(MAPPER,
                new FragmentedWebSocketHttpClient(List.of("{\"message\"", ":\"success\"}")));
        val template         = """
                {
                    "baseUrl" : "ws://localhost:1",
                    "maxResponseBytes" : %d
                }
                """;
        val request          = (ObjectValue) ValueJsonMarshaller
                .json(template.formatted(payload.getBytes(StandardCharsets.UTF_8).length));

        val drained = StreamAssertions.assertThat(fragmentedClient.consumeWebSocket(request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();

        assertThat(drained).singleElement().satisfies(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
    }

    @Test
    @DisplayName("a fragmented WebSocket message exceeding maxResponseBytes fails closed")
    void whenFragmentedWebSocketMessageExceedsMaxResponseBytesThenErrorValue() {
        val fragmentedClient = new BlockingWebClient(MAPPER,
                new FragmentedWebSocketHttpClient(List.of("{\"message\":\"", "too-large\"}")));
        val template         = """
                {
                    "baseUrl" : "ws://localhost:1",
                    "maxResponseBytes" : 16
                }
                """;
        val request          = (ObjectValue) ValueJsonMarshaller.json(template.formatted());

        val drained = StreamAssertions.assertThat(fragmentedClient.consumeWebSocket(request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();

        assertThat(drained).anySatisfy(v -> {
            if (!(v instanceof ErrorValue err)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
            assertThat(err.message()).contains("16");
        });
    }

    /**
     * Stand-in transport whose blocking send throws an unchecked
     * exception, modelling a flaky endpoint or an invalid request that
     * surfaces as a RuntimeException from {@link HttpClient#send}.
     */
    private static class ThrowingHttpClient extends HttpClient {

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler) {
            throw new IllegalArgumentException("transport failure");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> responseBodyHandler) {
            throw new IllegalArgumentException("transport failure");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler,
                PushPromiseHandler<T> pushPromiseHandler) {
            throw new IllegalArgumentException("transport failure");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    /**
     * Newline-free byte source that never produces a line terminator and
     * counts how many bytes it has served. Models a hostile SSE host that
     * streams a single unbounded line. Bounded at a ceiling far above the
     * test's byte cap so the unfixed line-buffering path terminates with a
     * detectable over-read instead of exhausting the test JVM heap.
     */
    private static final class CountingInfiniteStream extends InputStream {
        private static final long CEILING = 8L * 1024L * 1024L;
        private final AtomicLong  served  = new AtomicLong();

        @Override
        public int read() {
            if (served.get() >= CEILING) {
                return -1;
            }
            served.incrementAndGet();
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            val remaining = CEILING - served.get();
            if (remaining <= 0L) {
                return -1;
            }
            val count = (int) Math.min(length, remaining);
            for (int i = 0; i < count; i++) {
                buffer[offset + i] = 'x';
            }
            served.addAndGet(count);
            return count;
        }

        long bytesServed() {
            return served.get();
        }
    }

    /**
     * Stand-in transport that answers every send with HTTP 200 and the
     * given {@link InputStream} as the body, driving whichever
     * {@link BodyHandler} the client supplies. This lets the test observe
     * how many body bytes the client pulls before aborting.
     */
    @RequiredArgsConstructor
    private static final class InputStreamHttpClient extends HttpClient {
        private final InputStream body;

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            val responseInfo = new ResponseInfo() {
                                 @Override
                                 public int statusCode() {
                                     return 200;
                                 }

                                 @Override
                                 public HttpHeaders headers() {
                                     return HttpHeaders.of(Map.of(), (a, b) -> true);
                                 }

                                 @Override
                                 public Version version() {
                                     return Version.HTTP_1_1;
                                 }
                             };
            val subscriber   = responseBodyHandler.apply(responseInfo);
            new BodyPublisher(body).subscribe(subscriber);
            final T value;
            try {
                value = subscriber.getBody().toCompletableFuture().get(5L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException(e);
            }
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return responseInfo.headers();
                }

                @Override
                public T body() {
                    return value;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(e);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler,
                PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    /**
     * Byte source over a fixed string that records whether it was closed.
     */
    private static final class TrackingInputStream extends InputStream {
        private final InputStream   delegate;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        TrackingInputStream(String content) {
            this.delegate = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            delegate.close();
        }

        boolean wasClosed() {
            return closed.get();
        }
    }

    /**
     * Stand-in transport answering every send with the given status code
     * and {@link InputStream} body.
     */
    @RequiredArgsConstructor
    private static final class StatusInputStreamHttpClient extends HttpClient {
        private final int         status;
        private final InputStream body;

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler) {
            // Return the tracking stream directly so the test can observe close(),
            // which BodyHandlers.ofInputStream would hide behind its own wrapper.
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return status;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (a, b) -> true);
                }

                @Override
                public T body() {
                    return (T) body;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler,
                PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor
    private static final class FragmentedWebSocketHttpClient extends ThrowingHttpClient {
        private final List<String> fragments;

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return new FragmentedWebSocketBuilder(fragments);
        }
    }

    @RequiredArgsConstructor
    private static final class FragmentedWebSocketBuilder implements WebSocket.Builder {
        private final List<String> fragments;

        @Override
        public WebSocket.Builder header(String name, String value) {
            return this;
        }

        @Override
        public WebSocket.Builder connectTimeout(Duration timeout) {
            return this;
        }

        @Override
        public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
            return this;
        }

        @Override
        public CompletableFuture<WebSocket> buildAsync(URI uri, WebSocket.Listener listener) {
            val webSocket = new RecordingWebSocket();
            listener.onOpen(webSocket);
            for (int i = 0; i < fragments.size() && !webSocket.aborted(); i++) {
                listener.onText(webSocket, fragments.get(i), i == fragments.size() - 1);
            }
            listener.onClose(webSocket, WebSocket.NORMAL_CLOSURE, "done");
            return CompletableFuture.completedFuture(webSocket);
        }
    }

    private static final class RecordingWebSocket implements WebSocket {
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final AtomicBoolean closed  = new AtomicBoolean(false);

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closed.set(true);
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            // The fake builder pushes fragments synchronously.
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return closed.get() || aborted.get();
        }

        @Override
        public boolean isInputClosed() {
            return closed.get() || aborted.get();
        }

        @Override
        public void abort() {
            aborted.set(true);
        }

        boolean aborted() {
            return aborted.get();
        }
    }

    /**
     * Minimal demand-driven publisher that streams an {@link InputStream}
     * as 8 KB byte-buffer chunks to a single {@link BodySubscriber}. Each
     * downstream request pulls and delivers exactly one further chunk, so
     * the subscriber controls how much of the body is ever read.
     */
    @RequiredArgsConstructor
    private static final class BodyPublisher implements Flow.Publisher<List<ByteBuffer>> {
        private final InputStream source;

        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private volatile boolean done;

                @Override
                public void request(long n) {
                    if (done) {
                        return;
                    }
                    for (long i = 0; i < n; i++) {
                        val       chunk = new byte[8192];
                        final int read;
                        try {
                            read = source.read(chunk, 0, chunk.length);
                        } catch (IOException e) {
                            done = true;
                            subscriber.onError(e);
                            return;
                        }
                        if (read < 0) {
                            done = true;
                            subscriber.onComplete();
                            return;
                        }
                        subscriber.onNext(List.of(ByteBuffer.wrap(chunk, 0, read)));
                    }
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
