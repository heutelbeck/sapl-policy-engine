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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive HTTP and WebSocket client for policy information point attribute
 * retrieval.
 * <p>
 * Security: Connection and read timeouts are enforced to prevent resource
 * exhaustion from slow or unresponsive servers.
 * The AttributeBroker provides an additional timeout layer, but this client
 * ensures TCP connections are properly closed
 * when timeouts occur.
 */
@RequiredArgsConstructor
public class ReactiveWebClient {

    static final String        ERROR_NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST = "No base URL specified for web request.";
    public static final String BASE_URL                                    = "baseUrl";
    public static final String PATH                                        = "path";
    public static final String URL_PARAMS                                  = "urlParameters";
    public static final String HEADERS                                     = "headers";
    public static final String BODY                                        = "body";
    public static final String POLLING_INTERVAL                            = "pollingIntervalMs";
    public static final String REPEAT_TIMES                                = "repetitions";
    public static final String ACCEPT_MEDIATYPE                            = "accept";
    public static final String CONTENT_MEDIATYPE                           = "contentType";
    static final long          DEFAULT_POLLING_INTERVALL_MS                = 1000L;
    static final long          DEFAULT_REPETITIONS                         = Long.MAX_VALUE;

    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS       = 30;

    private static final JsonNodeFactory JSON             = JsonNodeFactory.instance;
    private static final TextNode        APPLICATION_JSON = JSON.textNode(MediaType.APPLICATION_JSON.toString());

    private final ObjectMapper mapper;

    /**
     * <p>
     * Connects to an HTTP service and produces a Flux&lt;Value&gt;
     * </p>
     *
     * @param method
     * the @see HttpMethod to execute and a @see ObjectValue containing the settings
     * @param requestSettings
     * contains the HTTP parameters for the request.
     *
     * @return a @see Flux&lt;@see Value&gt;
     */
    public Flux<Value> httpRequest(HttpMethod method, ObjectValue requestSettings) {
        val baseUrl            = baseUrl(requestSettings);
        val path               = getFieldAsTextOrDefault(requestSettings, PATH, "");
        val urlParameters      = toStringMap(
                getFieldAsJsonNodeOrDefault(requestSettings, URL_PARAMS, JSON.objectNode()));
        val requestHeaders     = getFieldAsJsonNodeOrDefault(requestSettings, HEADERS, JSON.objectNode());
        val pollingIntervallMs = longOrDefault(requestSettings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS);
        val repetitions        = longOrDefault(requestSettings, REPEAT_TIMES, DEFAULT_REPETITIONS);
        val accept             = toMediaType(
                getFieldAsJsonNodeOrDefault(requestSettings, ACCEPT_MEDIATYPE, APPLICATION_JSON));
        val contentType        = toMediaType(
                getFieldAsJsonNodeOrDefault(requestSettings, CONTENT_MEDIATYPE, APPLICATION_JSON));
        val body               = getFieldAsJsonNodeOrDefault(requestSettings, BODY, null);

        // @formatter:off
        val httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECTION_TIMEOUT_SECONDS * 1000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        val spec = WebClient.builder()
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .baseUrl(baseUrl).build()
                            .method(method)
                            .uri(u -> setUrlParams(u, urlParameters).path(path).build())
                            .headers(h -> setHeaders(h,requestHeaders))
                            .accept(accept);
        // @formatter:on

        var client = (RequestHeadersSpec<?>) spec;
        if (method != HttpMethod.GET && body != null) {
            client = spec.contentType(contentType).bodyValue(body);
        }

        switch (accept.toString()) {
        case MediaType.TEXT_EVENT_STREAM_VALUE:
            return retrieveSSE(client).map(ValueJsonMarshaller::fromJsonNode).onErrorResume(this::mapError);
        case MediaType.APPLICATION_JSON_VALUE:
            return poll(exchangeToMono(JsonNode.class, client).map(ValueJsonMarshaller::fromJsonNode)
                    .onErrorResume(this::mapError), pollingIntervallMs, repetitions);
        default:
            return poll(exchangeToMono(String.class, client).<Value>map(Value::of).onErrorResume(this::mapError),
                    pollingIntervallMs, repetitions);
        }
    }

    /**
     * @param requestSettings
     * the request specification
     *
     * @return a Flux of incoming messages
     */
    public Flux<Value> consumeWebSocket(ObjectValue requestSettings) {
        val baseUrl        = baseUrl(requestSettings);
        val path           = getFieldAsTextOrDefault(requestSettings, PATH, "");
        val requestHeaders = getFieldAsJsonNodeOrDefault(requestSettings, HEADERS, JSON.objectNode());
        val uri            = URI.create(baseUrl + path);
        val body           = getFieldAsJsonNodeOrDefault(requestSettings, BODY, null);
        val client         = new ReactorNettyWebSocketClient();

        val headers = new HttpHeaders();
        setHeaders(headers, requestHeaders);

        val               sessionReference = new AtomicReference<WebSocketSession>();
        Sinks.Many<Value> receiveBuffer    = Sinks.many().unicast().onBackpressureBuffer();
        client.execute(uri, headers, session -> {
            sessionReference.set(session);
            return sendAndListen(session, body, receiveBuffer);
        }).subscribe();
        // @formatter:off
        return receiveBuffer.asFlux()
                            .doOnCancel(() -> terminateSession(sessionReference))
                            .doOnTerminate(() -> terminateSession(sessionReference));
        // @formatter:on
    }

    private void setHeaders(HttpHeaders headers, JsonNode requestHeaders) {
        requestHeaders.properties().forEach(field -> {
            val key   = field.getKey();
            val value = field.getValue();
            if (value.isArray()) {
                val elements = new ArrayList<String>();
                value.elements().forEachRemaining(e -> elements.add(e.asText()));
                headers.put(key, elements);
            } else {
                headers.set(key, value.asText());
            }
        });
    }

    private String baseUrl(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(BASE_URL)) {
            throw new IllegalArgumentException(ERROR_NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST);
        }
        val value = requestSettings.get(BASE_URL);
        if (value instanceof TextValue text) {
            return text.value();
        }
        throw new IllegalArgumentException("baseUrl must be a text value");
    }

    private String getFieldAsTextOrDefault(ObjectValue requestSettings, String fieldName, String defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        val value = requestSettings.get(fieldName);
        if (value instanceof TextValue text) {
            return text.value();
        }
        return defaultValue;
    }

    private JsonNode getFieldAsJsonNodeOrDefault(ObjectValue requestSettings, String fieldName, JsonNode defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        return ValueJsonMarshaller.toJsonNode(requestSettings.get(fieldName));
    }

    private long longOrDefault(ObjectValue requestSettings, String fieldName, long defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        val value = requestSettings.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " must be a number in HTTP requestSpecification, but was: null");
        }
        if (value instanceof NumberValue num) {
            return num.value().longValue();
        }
        throw new IllegalArgumentException(fieldName + " must be a number in HTTP requestSpecification, but was: "
                + value.getClass().getSimpleName());
    }

    private Map<String, String> toStringMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, String>>() {});
    }

    public MediaType toMediaType(JsonNode mediaTypeJson) {
        return MediaType.parseMediaType(mediaTypeJson.asText());
    }

    static UriBuilder setUrlParams(UriBuilder uri, Map<String, String> urlParams) {
        for (var param : urlParams.entrySet()) {
            uri = uri.queryParam(param.getKey(), param.getValue());
        }
        return uri;
    }

    private Flux<JsonNode> retrieveSSE(RequestHeadersSpec<?> client) {
        val type = new ParameterizedTypeReference<ServerSentEvent<JsonNode>>() {};
        return client.retrieve().bodyToFlux(type).map(ServerSentEvent::data);
    }

    private Flux<Value> poll(Mono<Value> in, long pollingInterval, long repeatTimes) {
        val safeRepeatTimes = (int) Math.min(repeatTimes, Integer.MAX_VALUE);
        return Flux.range(0, safeRepeatTimes)
                .concatMap(i -> i == 0 ? in : Mono.delay(Duration.ofMillis(pollingInterval)).then(in));
    }

    private <T> Mono<T> exchangeToMono(Class<T> clazz, RequestHeadersSpec<?> in) {
        return in.retrieve()
                .onStatus(HttpStatusCode::isError,
                        clientResponse -> clientResponse.createException()
                                .flatMap(error -> Mono.error(new IllegalArgumentException(error.getMessage()))))
                .bodyToMono(clazz);
    }

    private Mono<Value> mapError(Throwable e) {
        if (e instanceof WebClientResponseException clientException) {
            val cause   = clientException.getRootCause();
            val message = cause == null ? e.getMessage() : cause.getMessage();
            return Mono.just(Value.error(message));
        }
        return Mono.just(Value.error(e.getMessage()));
    }

    private Mono<Void> sendAndListen(WebSocketSession session, JsonNode body, Many<Value> receiveBuffer) {
        val send   = null == body ? Mono.empty() : session.send(Mono.just(session.textMessage(body.asText())));
        val listen = listenAndSendEventsToSink(session, receiveBuffer);
        return send.and(listen);
    }

    private void terminateSession(AtomicReference<WebSocketSession> sessionReference) {
        Optional.ofNullable(sessionReference.get()).filter(WebSocketSession::isOpen)
                .ifPresent(session -> session.close().subscribe());
    }

    private Mono<Void> listenAndSendEventsToSink(WebSocketSession session, Many<Value> receiveBuffer) {
        return session.receive().map(WebSocketMessage::getPayloadAsText).doOnNext(payload -> {
            try {
                JsonNode node = mapper.readTree(payload);
                receiveBuffer.tryEmitNext(ValueJsonMarshaller.fromJsonNode(node));
            } catch (JsonProcessingException e) {
                receiveBuffer.tryEmitNext(Value.error(e.getMessage()));
            }
        }).then();
    }

}
