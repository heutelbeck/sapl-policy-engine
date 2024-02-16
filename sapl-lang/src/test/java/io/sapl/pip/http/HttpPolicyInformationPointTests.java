package io.sapl.pip.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

class HttpPolicyInformationPointTests {

    private final static String DFAULT_REQUEST = """
            {
                "baseUrl" : "https://localhost:8008"
            }
            """;

    private final static String ALTERED_REQUEST = """
            {
                "baseUrl" : "https://localhost:1234"
            }
            """;

    private final static Val URL = Val.of("https://localhost:1234");

    @Test
    void get() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.get(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.GET, expectedRequest);
    }

    @Test
    void post() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.post(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.POST, expectedRequest);
    }

    @Test
    void put() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.put(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PUT, expectedRequest);
    }

    @Test
    void patch() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.patch(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PATCH, expectedRequest);
    }

    @Test
    void delete() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.delete(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.DELETE, expectedRequest);
    }

    @Test
    void webSocket() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var expectedRequest  = Val.ofJson(ALTERED_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.websocket(URL, request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).consumeWebSocket(expectedRequest);
    }

    @Test
    void environmentGet() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.get(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.GET, request);
    }

    @Test
    void environmentPost() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.post(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.POST, request);
    }

    @Test
    void environmentPut() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.put(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PUT, request);
    }

    @Test
    void environmentPatch() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.patch(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.PATCH, request);
    }

    @Test
    void environmentDelete() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.delete(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).httpRequest(HttpMethod.DELETE, request);
    }

    @Test
    void environmentWebSocket() throws JsonProcessingException {
        var mockClient       = mockClient();
        var request          = Val.ofJson(DFAULT_REQUEST);
        var httpPipUnderTest = new HttpPolicyInformationPoint(mockClient);
        assertThatCode(() -> httpPipUnderTest.websocket(request)).doesNotThrowAnyException();
        verify(mockClient, times(1)).consumeWebSocket(request);
    }

    private ReactiveWebClient mockClient() {
        var defaultResponse = Flux.just(Val.of(1), Val.of(2), Val.of(3));
        var mockClient      = mock(ReactiveWebClient.class);
        when(mockClient.httpRequest(any(), any())).thenReturn(defaultResponse);
        return mockClient;
    }
}
