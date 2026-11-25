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
package io.sapl.pdp.configuration;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("International endpoint names reach the same ASCII destination")
class InternationalEndpointTests {
    @Mock
    private HttpClient        httpClient;
    @Mock
    private WebSocket.Builder webSocketBuilder;
    @Mock
    private WebSocket         webSocket;

    @Mock
    private HttpResponse<InputStream> keyResponse;

    @MethodSource("destinations")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Unicode and Punycode service prefixes activate without a custom profile")
    void whenInternationalServiceConfiguredThenConfigurationActivates(String description, String prefix, String request,
            String destination) {
        val data = new PdpData(variables(prefix), Value.EMPTY_OBJECT);
        try (val source = new PdpVoterSource(new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER)),
                Clock.systemUTC())) {
            source.loadConfiguration(new PDPConfiguration("test", "international", CombiningAlgorithm.DEFAULT,
                    List.of("policy \"books\" permit"), data));
            assertThat(source.getPdpStatus("test"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.LOADED));
        }
    }

    @MethodSource("destinations")
    @ParameterizedTest(name = "{0}")
    @DisplayName("service checks and the HTTP client agree on host, scheme, port, path and query")
    void whenInternationalServiceRequestedThenClientReceivesCanonicalHost(String description, String prefix,
            String request, String destination) {
        val transport = new RecordingClient(httpClient);
        val pip       = new HttpPolicyInformationPoint(transport);
        val context   = new AttributeAccessContext(variables(prefix), Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val settings  = requestSettings(request);
        try (val stream = pip.get(context, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                    .awaitsNext(value -> assertThat(value).isEqualTo(Value.TRUE));
        }
        assertThat(transport.destination.get()).isEqualTo(URI.create(destination));
    }

    static Stream<Arguments> destinations() {
        return Stream.of(
                arguments("Unicode books prefix", "https://b\u00fccher.example.test:8443/library",
                        "https://xn--bcher-kva.example.test:8443/library/item?q=first",
                        "https://xn--bcher-kva.example.test:8443/library/item?q=first"),
                arguments("Unicode books request", "https://xn--bcher-kva.example.test:8443/library",
                        "https://b\u00fccher.example.test:8443/library/item?q=first",
                        "https://xn--bcher-kva.example.test:8443/library/item?q=first"),
                arguments("Unicode street prefix", "http://stra\u00dfe.example.test:8080/library",
                        "http://xn--strae-oqa.example.test:8080/library/item?q=first",
                        "http://xn--strae-oqa.example.test:8080/library/item?q=first"),
                arguments("Unicode street request", "http://xn--strae-oqa.example.test:8080/library",
                        "http://stra\u00dfe.example.test:8080/library/item?q=first",
                        "http://xn--strae-oqa.example.test:8080/library/item?q=first"));
    }

    @ValueSource(strings = { "https://xn--bcher-kva.example.test:8444/library/item",
            "https://xn--bcher-kva.example.test:8443/catalogue/item",
            "http://xn--bcher-kva.example.test:8443/library/item" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("normalization preserves the service port, path and scheme restrictions")
    void whenRequestIsOutsideServiceThenItDoesNotReachClient(String request) {
        val transport = new RecordingClient(httpClient);
        val pip       = new HttpPolicyInformationPoint(transport);
        val context   = new AttributeAccessContext(variables("https://b\u00fccher.example.test:8443/library"),
                Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val settings  = requestSettings(request);
        try (val stream = pip.get(context, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                    .awaitsNext(value -> assertThat(value).isInstanceOf(ErrorValue.class));
        }
        assertThat(transport.destination.get()).isNull();
    }

    @ValueSource(strings = { "https://b\u00fccher.example.test:8443", "https://xn--bcher-kva.example.test:8443",
            "https://stra\u00dfe.example.test:8443", "https://xn--strae-oqa.example.test:8443" })
    @ParameterizedTest(name = "{0}")
    @DisplayName("international key-server hosts keep ordinary and already encoded path placeholders distinct")
    void whenKeyServerIsInternationalThenOnlyTheWrittenPlaceholderIsReplaced(String origin) {
        val template = origin + "/keys/%7Bkid%7D/{kid}?version=1";
        val settings = JsonMapper.shared().createObjectNode().put("uri", template);
        assertThat(JWTKeyProvider.resolveKeyServerUri(settings, "book.1"))
                .isEqualTo(origin + "/keys/%7Bkid%7D/book.1?version=1");
    }

    @MethodSource("keyServerRequests")
    @ParameterizedTest(name = "{0}")
    @DisplayName("an international key server reaches the HTTP request builder with its ASCII hostname")
    void whenInternationalKeyServerRespondsThenItsRequestHasCanonicalHost(String origin, String destination)
            throws IOException, InterruptedException {
        when(keyResponse.statusCode()).thenReturn(404);
        when(keyResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(httpClient.<InputStream>send(any(), any())).thenReturn(keyResponse);
        val provider = new JWTKeyProvider(httpClient, Clock.systemUTC());
        val settings = JsonMapper.shared().createObjectNode().put("uri", origin + "/keys/{kid}?version=1");
        assertThatCode(() -> provider.provide("book.1", settings)).doesNotThrowAnyException();
        val request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertThat(request.getValue().uri()).isEqualTo(URI.create(destination));
    }

    static Stream<Arguments> keyServerRequests() {
        return Stream.of(
                arguments("https://b\u00fccher.example.test:8443",
                        "https://xn--bcher-kva.example.test:8443/keys/book.1?version=1"),
                arguments("https://stra\u00dfe.example.test:8443",
                        "https://xn--strae-oqa.example.test:8443/keys/book.1?version=1"));
    }

    @MethodSource("webSockets")
    @ParameterizedTest(name = "{0}")
    @DisplayName("WebSocket clients retain ws and wss while receiving the ASCII host")
    void whenWebSocketHostIsInternationalThenOnlyItsHostChanges(String source, String destination) {
        when(httpClient.newWebSocketBuilder()).thenReturn(webSocketBuilder);
        when(webSocketBuilder.buildAsync(any(), any())).thenAnswer(invocation -> {
            WebSocket.Listener listener = invocation.getArgument(1);
            listener.onOpen(webSocket);
            listener.onText(webSocket, "true", true);
            listener.onClose(webSocket, WebSocket.NORMAL_CLOSURE, "done");
            return CompletableFuture.completedFuture(webSocket);
        });
        val client   = new BlockingWebClient(JsonMapper.shared(), httpClient);
        val settings = ObjectValue.builder().put("baseUrl", Value.of(source)).build();
        try (val stream = client.consumeWebSocket(settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(5))
                    .awaitsNext(value -> assertThat(value).isEqualTo(Value.TRUE));
        }
        val uri = ArgumentCaptor.forClass(URI.class);
        verify(webSocketBuilder).buildAsync(uri.capture(), any());
        assertThat(uri.getValue()).isEqualTo(URI.create(destination));
    }

    static Stream<Arguments> webSockets() {
        return Stream.of(
                arguments("wss://b\u00fccher.example.test:9443/@reports?q=first",
                        "wss://xn--bcher-kva.example.test:9443/@reports?q=first"),
                arguments("ws://stra\u00dfe.example.test:8080/stream?q=first",
                        "ws://xn--strae-oqa.example.test:8080/stream?q=first"));
    }

    private static ObjectValue variables(String prefix) {
        val service  = ObjectValue.builder().put("allowedBaseUrls", Value.ofArray(Value.of(prefix))).build();
        val services = ObjectValue.builder().put("books", service).build();
        val http     = ObjectValue.builder().put("services", services).build();
        return ObjectValue.builder().put("http", http).build();
    }

    private static ObjectValue requestSettings(String address) {
        return ObjectValue.builder().put("baseUrl", Value.of(address)).put("secretsKey", Value.of("books")).build();
    }

    private static final class RecordingClient extends BlockingWebClient {
        private final AtomicReference<URI> destination = new AtomicReference<>();

        private RecordingClient(HttpClient client) {
            super(JsonMapper.shared(), client);
        }

        @Override
        public HttpResponse<InputStream> send(HttpRequest request) {
            destination.set(request.uri());
            return new Reply(request);
        }
    }

    private record Reply(HttpRequest request) implements HttpResponse<InputStream> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, values) -> true);
        }

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[] { 't', 'r', 'u', 'e' });
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
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
