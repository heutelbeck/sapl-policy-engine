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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Flux;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpPolicyInformationPointTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_REQUEST = """
            {
                "baseUrl" : "https://localhost:8008"
            }
            """;

    private static final String ALTERED_REQUEST = """
            {
                "baseUrl" : "https://localhost:1234"
            }
            """;

    private static final TextValue URL = (TextValue) Value.of("https://localhost:1234");

    @Test
    void get() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.get(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.GET, expectedRequest);
    }

    @Test
    void post() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.post(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.POST, expectedRequest);
    }

    @Test
    void put() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.put(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PUT, expectedRequest);
    }

    @Test
    void patch() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.patch(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PATCH, expectedRequest);
    }

    @Test
    void delete() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.delete(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.DELETE, expectedRequest);
    }

    @Test
    void when_webSocketCalledWithUrl_then_consumesWebSocketWithModifiedRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val expectedRequest  = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(ALTERED_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.websocket(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).consumeWebSocket(expectedRequest);
    }

    @Test
    void when_environmentGetCalled_then_executesGetRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.get(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.GET, request);
    }

    @Test
    void when_environmentPostCalled_then_executesPostRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.post(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.POST, request);
    }

    @Test
    void when_environmentPutCalled_then_executesPutRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.put(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PUT, request);
    }

    @Test
    void when_environmentPatchCalled_then_executesPatchRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.patch(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PATCH, request);
    }

    @Test
    void when_environmentDeleteCalled_then_executesDeleteRequest() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.delete(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.DELETE, request);
    }

    @Test
    void when_environmentWebSocketCalled_then_consumesWebSocket() throws JsonProcessingException {
        val mockClient       = mockClient();
        val request          = (ObjectValue) ValueJsonMarshaller.fromJsonNode(MAPPER.readTree(DEFAULT_REQUEST));
        val httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.websocket(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).consumeWebSocket(request);
    }

    private ReactiveWebClient mockClient() {
        val defaultResponse = Flux.<Value>just(Value.of(1), Value.of(2), Value.of(3));
        val mockClient      = mock(ReactiveWebClient.class);
        when(mockClient.httpRequest(any(), any())).thenReturn(defaultResponse);
        return mockClient;
    }

    @Test
    void when_brokerLoadsHttpPip_then_libraryIsAvailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new HttpPolicyInformationPoint(mockClient());

        broker.loadPolicyInformationPointLibrary(pip);

        assertThat(broker.getLoadedLibraryNames()).contains("http");
    }

    @Test
    void when_loadLibraryWithoutAnnotation_then_throwsException() {
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
    void when_loadDuplicateLibrary_then_throwsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new HttpPolicyInformationPoint(mockClient());

        broker.loadPolicyInformationPointLibrary(pip);

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new HttpPolicyInformationPoint(mockClient())))
                .hasMessageContaining("Library already loaded: http");
    }
}
