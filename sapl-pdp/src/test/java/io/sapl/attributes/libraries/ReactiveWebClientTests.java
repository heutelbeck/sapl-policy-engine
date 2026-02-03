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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReactiveWebClient")
class ReactiveWebClientTests {

    private static final JsonMapper   MAPPER           = JsonMapper.builder().build();
    private static final String       DEFAULT_BODY     = """
            {"message":"success"}""";
    private static final MockResponse DEFAULT_RESPONSE = new MockResponse().setBody(DEFAULT_BODY)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

    private static ReactiveWebClient clientUnderTest;

    private String        baseUrl;
    private MockWebServer mockBackEnd;

    @BeforeAll
    static void setUp() {
        clientUnderTest = new ReactiveWebClient(MAPPER);
    }

    @BeforeEach
    void initialize() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
        baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    }

    @AfterEach
    void stopBackEnd() throws IOException {
        mockBackEnd.shutdown();
    }

    private String toJsonString(Value v) throws JacksonException {
        return MAPPER.writeValueAsString(ValueJsonMarshaller.toJsonNode(v));
    }

    private ObjectValue defaultRequest(String mimeType) {
        val template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "pollingIntervalMs" : 1000,
                    "repetitions" : 2
                }
                """;
        return (ObjectValue) ValueJsonMarshaller.json(template.formatted(baseUrl, mimeType));
    }

    @Test
    void whenHttpGetThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenUrlParamsAndHeadersThenParamsAndHeadersInReqest() throws InterruptedException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val template        = """
                {
                    "baseUrl" : "%s",
                    "path" : "rainbow",
                    "accept" : "%s",
                    "repetitions" : 2,
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
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller
                .fromJsonNode(MAPPER.readTree(String.format(template, baseUrl, MediaType.APPLICATION_JSON_VALUE)));
        clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).<String>handle((v, sink) -> {
            try {
                sink.next(toJsonString(v));
            } catch (JacksonException e) {
                sink.error(new RuntimeException(e));
            }
        }).blockFirst();
        val recordedRequest = mockBackEnd.takeRequest(1, TimeUnit.SECONDS);

        assertThat(recordedRequest).isNotNull();
        val requestUrl = recordedRequest.getRequestUrl();
        assertThat(requestUrl).isNotNull();
        val url     = requestUrl.toString();
        val headers = recordedRequest.getHeaders().toMultimap();

        assertThat(url).contains("willi=wurst", "h%C3%A4nschen=klein", "rainbow?");
        assertThat(headers).containsKey("X-A").containsKey("X-B");
        assertThat(headers.get("X-A")).contains("einmal");
        assertThat(headers.get("X-B")).contains("a", "b", "c");
    }

    @Test
    void whenHttpErrorThenValError() {
        val mockResponse = new MockResponse().setResponseCode(500).setHeader("content-type", "application/json")
                .setBody("{}");
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest);
        // @formatter:off
        StepVerifier.create(response)
                    .expectNextMatches(this::isServerError)
                    .expectNextMatches(this::isServerError)
                    .verifyComplete();
        // @formatter:on
    }

    private boolean isServerError(Value value) {
        if (!(value instanceof ErrorValue errorValue)) {
            return false;
        }
        return errorValue.message().contains("500 Internal");
    }

    @Test
    void whenFetchingXMLThenIsInTextVal() {
        val minimalXML   = "<a/>";
        val mockResponse = new MockResponse().setBody(minimalXML).addHeader("Content-Type",
                MediaType.APPLICATION_XML_VALUE);
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_XML_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest)
                .map(v -> ((TextValue) v).value());
        StepVerifier.create(response).expectNext(minimalXML).expectNext(minimalXML).expectComplete().verify();
    }

    @Test
    void whenReturningXMLWhenExpectingJsonThenIsInTextVal() {
        val minimalXML   = "<a/>";
        val mockResponse = new MockResponse().setBody(minimalXML).addHeader("Content-Type",
                MediaType.APPLICATION_XML_VALUE);
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest);
        StepVerifier.create(response).expectNextMatches(this::isContentTypeError)
                .expectNextMatches(this::isContentTypeError).verifyComplete();
    }

    private boolean isContentTypeError(Value value) {
        if (!(value instanceof ErrorValue errorValue)) {
            return false;
        }
        return errorValue.message().contains("Content type 'application/xml' not supported");
    }

    @Test
    void whenPollingIntervallNotDefinedFallsBackToDefaultAndWorks() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "repetitions" : 2
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller
                .fromJsonNode(MAPPER.readTree(String.format(template, baseUrl, MediaType.APPLICATION_JSON_VALUE)));
        val response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenHttpPostThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenHttpPostWithBodyThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val template        = """
                {
                    "baseUrl" : "%s",
                    "body" : {
                                 "some" : "data",
                                 "andMoreData" : 123
                             },
                    "accept" : "application/json",
                    "pollingIntervalMs" : 10,
                    "repetitions" : 2
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller
                .fromJsonNode(MAPPER.readTree(String.format(template, baseUrl)));
        val response        = clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenIntervalNotANumberThenError() {
        val template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "pollingIntervalMs" : null
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller
                .fromJsonNode(MAPPER.readTree(String.format(template, baseUrl)));
        assertThatThrownBy(() -> clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest))
                .hasMessage("pollingIntervalMs must be a number in HTTP requestSpecification, but was: NullValue.");
    }

    @Test
    void whenNoBaseUrlThenError() {
        val httpTestRequest = Value.EMPTY_OBJECT;
        assertThatThrownBy(() -> clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest))
                .hasMessage(ReactiveWebClient.ERROR_NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST);
    }

    @Test
    void whenHttpPutThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.PUT, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenHttpPatchThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.PATCH, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenHttpDeleteThenReturnsExpectedResponse() {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        val httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        val response        = clientUnderTest.httpRequest(HttpMethod.DELETE, httpTestRequest).map(v -> {
                                try {
                                    return toJsonString(v);
                                } catch (JacksonException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void whenServerSentEventsThenReceivesEventStream() {
        val eventStream = "id:id1\nevent:event1\ndata:" + DEFAULT_BODY + "\n\n" + "id:id2\nevent:event2\ndata:"
                + DEFAULT_BODY + "\n\n" + "id:id3\nevent:event3\ndata:" + DEFAULT_BODY + "\n\n";
        mockBackEnd.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));

        val template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream",
                    "pollingIntervalMs" : 1000,
                    "repetitions" : 2
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller
                .fromJsonNode(MAPPER.readTree(String.format(template, baseUrl)));

        val response = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).<String>handle((v, sink) -> {
            try {
                sink.next(toJsonString(v));
            } catch (JacksonException e) {
                sink.error(new RuntimeException(e));
            }
        });
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY)
                .expectComplete().verify();
    }

}
