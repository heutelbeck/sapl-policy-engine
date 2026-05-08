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
package io.sapl.attributes.libraries.vnext.util;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
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

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BlockingWebClient")
class BlockingWebClientTests {

    private static final JsonMapper   MAPPER           = JsonMapper.builder().build();
    private static final String       DEFAULT_BODY     = """
            {"message":"success"}""";
    private static final MockResponse DEFAULT_RESPONSE = new MockResponse().setBody(DEFAULT_BODY)
            .addHeader("Content-Type", "application/json");
    private static final Instant      T0               = Instant.parse("2026-05-08T12:00:00Z");

    private MockWebServer     server;
    private String            baseUrl;
    private MutableClock      clock;
    private TestTimeScheduler scheduler;
    private BlockingWebClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl   = String.format("http://localhost:%s", server.getPort());
        clock     = new MutableClock(T0);
        scheduler = new TestTimeScheduler(T0);
        client    = new BlockingWebClient(MAPPER, HttpClient.newHttpClient(), clock, scheduler);
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
                    "accept" : "%s",
                    "pollingIntervalMs" : 1000
                }
                """;
        return (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl, mimeType));
    }

    private void advanceOnePollingInterval() {
        clock.setInstant(clock.instant().plusSeconds(1));
        scheduler.advanceTo(clock.instant());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "GET", "POST", "PUT", "PATCH", "DELETE" })
    @DisplayName("HTTP method emits the JSON body, then again at the polling interval")
    void whenHttpMethodCalledThenEmitsResponseAndPolls(String method) {
        server.enqueue(DEFAULT_RESPONSE);
        server.enqueue(DEFAULT_RESPONSE);

        try (val stream = client.httpRequest(method, defaultRequest("application/json"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
            advanceOnePollingInterval();
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
        }
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
    @DisplayName("non-numeric polling interval emits an error value")
    void whenIntervalNotANumberThenError() {
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "pollingIntervalMs" : null
                }
                """;
        val request  = (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl));

        try (val stream = client.httpRequest("POST", request)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> {
                if (!(v instanceof ErrorValue err)) {
                    throw new AssertionError("Expected ErrorValue, got: " + v);
                }
                assertThat(err.message()).contains("pollingIntervalMs").contains("number");
            });
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

        // Fast-burst SSE events collapse in the latest-wins slot (drop-old by design);
        // drain captures whatever the consumer observes and asserts that at least one
        // parsed event matches the expected body.
        val drained = StreamAssertions.assertThat(client.httpRequest("GET", request))
                .withinTimeout(Duration.ofSeconds(2L)).drain();
        assertThat(drained).isNotEmpty();
        assertThat(drained).allSatisfy(v -> assertThat(toJsonString(v)).isEqualTo(DEFAULT_BODY));
    }
}
