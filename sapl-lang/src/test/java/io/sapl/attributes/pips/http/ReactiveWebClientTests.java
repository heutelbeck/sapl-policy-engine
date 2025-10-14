/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.pips.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveWebClientTests {

    private static final ObjectMapper MAPPER           = new ObjectMapper();
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

    private Val defaultRequest(String mimeType) throws JsonProcessingException {
        final var template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "pollingIntervalMs" : 1000,
                    "repetitions" : 2
                }
                """;
        return Val.ofJson(String.format(template, baseUrl, mimeType));
    }

    @Test
    void testGet() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void when_UrlParamsAndHeaders_then_paramsAndHeadersInReqest() throws JsonProcessingException, InterruptedException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var template        = """
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
        final var httpTestRequest = Val.ofJson(String.format(template, baseUrl, MediaType.APPLICATION_JSON_VALUE));
        clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(Val::toString).blockFirst();
        final var recordedRequest = mockBackEnd.takeRequest(1, TimeUnit.SECONDS);
        final var url             = recordedRequest.getRequestUrl().toString();
        final var headers         = recordedRequest.getHeaders().toMultimap();

        final var sa = new SoftAssertions();
        sa.assertThat(url).contains("willi=wurst");
        sa.assertThat(url).contains("h%C3%A4nschen=klein");
        sa.assertThat(url).contains("rainbow?");
        sa.assertThat(headers.get("X-A")).contains("einmal");
        sa.assertThat(headers.get("X-B")).contains("a", "b", "c");
        sa.assertAll();
    }

    @Test
    void when_httpError_then_valError() throws JsonProcessingException {
        final var mockResponse = new MockResponse().setResponseCode(500).setHeader("content-type", "application/json")
                .setBody("{}");
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest);
        // @formatter:off
        StepVerifier.create(response)
                    .expectNextMatches(this::isServerError)
                    .expectNextMatches(this::isServerError)
                    .verifyComplete();
        // @formatter:on
    }

    private boolean isServerError(Val v) {
        return v.isError() && v.getMessage().contains("500 Internal");
    }

    @Test
    void when_fetchingXML_then_isInTextVal() throws JsonProcessingException {
        final var minimalXML   = "<a/>";
        final var mockResponse = new MockResponse().setBody(minimalXML).addHeader("Content-Type",
                MediaType.APPLICATION_XML_VALUE);
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_XML_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(Val::getText);
        StepVerifier.create(response).expectNext(minimalXML).expectNext(minimalXML).expectComplete().verify();
    }

    @Test
    void when_returningXMLWhenExpectingJson_then_isInTextVal() throws JsonProcessingException {
        final var minimalXML   = "<a/>";
        final var mockResponse = new MockResponse().setBody(minimalXML).addHeader("Content-Type",
                MediaType.APPLICATION_XML_VALUE);
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest);
        StepVerifier.create(response).expectNextMatches(this::isContentTypeError)
                .expectNextMatches(this::isContentTypeError).verifyComplete();
    }

    private boolean isContentTypeError(Val v) {
        return v.isError() && v.getMessage().contains("Content type 'application/xml' not supported");
    }

    @Test
    void when_pollingIntervallNotDefined_fallsBackToDefaultAndWorks() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "repetitions" : 2
                }
                """;
        final var httpTestRequest = Val.ofJson(String.format(template, baseUrl, MediaType.APPLICATION_JSON_VALUE));
        final var response        = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void testPost() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void testPostWithBody() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var template        = """
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
        final var httpTestRequest = Val.ofJson(String.format(template, baseUrl));
        final var response        = clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void when_intervallNotANumber_then_error() throws JsonProcessingException {
        final var template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "application/json",
                    "pollingIntervalMs" : null
                }
                """;
        final var httpTestRequest = Val.ofJson(String.format(template, baseUrl));
        assertThatThrownBy(() -> clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest))
                .hasMessage("pollingIntervalMs must be an integer in HTTP requestSpecification, but was: NULL");
    }

    @Test
    void when_noBaseUrl_thenError() {
        final var httpTestRequest = Val.ofEmptyObject();
        assertThatThrownBy(() -> clientUnderTest.httpRequest(HttpMethod.POST, httpTestRequest))
                .hasMessage(ReactiveWebClient.NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST_ERROR);
    }

    @Test
    void testPut() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.PUT, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void testPatch() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.PATCH, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void testDelete() throws JsonProcessingException {
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        mockBackEnd.enqueue(DEFAULT_RESPONSE);
        final var httpTestRequest = defaultRequest(MediaType.APPLICATION_JSON_VALUE);
        final var response        = clientUnderTest.httpRequest(HttpMethod.DELETE, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectComplete().verify();
    }

    @Test
    void testSSE() throws JsonProcessingException {
        final var eventStream = "id:id1\nevent:event1\ndata:" + DEFAULT_BODY + "\n\n" + "id:id2\nevent:event2\ndata:"
                + DEFAULT_BODY + "\n\n" + "id:id3\nevent:event3\ndata:" + DEFAULT_BODY + "\n\n";
        mockBackEnd.enqueue(new MockResponse().setBody(eventStream).addHeader("Content-Type", "text/event-stream"));

        final var template        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "text/event-stream",
                    "pollingIntervalMs" : 1000,
                    "repetitions" : 2
                }
                """;
        final var httpTestRequest = Val.ofJson(String.format(template, baseUrl));

        final var response = clientUnderTest.httpRequest(HttpMethod.GET, httpTestRequest).map(Val::toString);
        StepVerifier.create(response).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY).expectNext(DEFAULT_BODY)
                .expectComplete().verify();
    }

}
