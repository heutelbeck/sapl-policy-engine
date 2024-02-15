/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip.http;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.One;
import reactor.retry.Repeat;

@RequiredArgsConstructor
public class ReactiveWebClient {

    static final String                  NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST_ERROR = "No base URL specified for web request.";
    private static final String          BASE_URL                                    = "baseUrl";
    private static final String          PATH                                        = "path";
    private static final String          URL_PARAMS                                  = "urlParameters";
    private static final String          HEADERS                                     = "headers";
    private static final String          BODY                                        = "body";
    private static final String          POLLING_INTERVAL                            = "pollingIntervalMs";
    private static final String          REPEAT_TIMES                                = "repetitions";
    private static final String          ACCEPT_MEDIATYPE                            = "accept";
    private static final String          CONTENT_MEDIATYPE                           = "contentType";
    private static final long            DEFAULT_POLLING_INTERVALL_MS                = 1000L;
    private static final long            DEFAULT_REPETITIONS                         = Long.MAX_VALUE;
    private static final JsonNodeFactory JSON                                        = JsonNodeFactory.instance;
    private static final TextNode        APPLICATION_JSON                            = JSON
            .textNode(MediaType.APPLICATION_JSON.toString());

    private final ObjectMapper mapper;

    /**
     * <p>
     * Connects to an HTTP service and produces a Flux<Val>
     * </p>
     *
     * @param the @see HttpMethod to execute and a @see Val containing the settings
     * @return a @see Flux<@see Val>
     */
    public Flux<Val> httpRequest(HttpMethod method, Val requestSettings) {
        var baseUrl            = baseUrl(requestSettings);
        var path               = requestSettings.fieldValOrElse(PATH, Val.of("")).get().asText();
        var urlParameters      = toStringMap(requestSettings.fieldJsonNodeOrElse(URL_PARAMS, JSON::objectNode));
        var requestHeaders     = requestSettings.fieldJsonNodeOrElse(HEADERS, JSON::objectNode);
        var pollingIntervallMs = longOrDefault(requestSettings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS);
        var repetitions        = longOrDefault(requestSettings, REPEAT_TIMES, DEFAULT_REPETITIONS);
        var accept             = toMediaType(requestSettings.fieldJsonNodeOrElse(ACCEPT_MEDIATYPE, APPLICATION_JSON));
        var contentType        = toMediaType(requestSettings.fieldJsonNodeOrElse(CONTENT_MEDIATYPE, APPLICATION_JSON));
        var body               = requestSettings.fieldJsonNodeOrElse(BODY, (JsonNode) null);

        // @formatter:off
        var spec = WebClient.builder()
                            .baseUrl(baseUrl).build()
                            .method(method)
                            .uri(u -> setUrlParams(u, urlParameters).path(path).build())
                            .headers(h -> setHeaders(h,requestHeaders))
                            .accept(accept);
        // @formatter:on

        RequestHeadersSpec<?> client = spec;
        if (method != HttpMethod.GET && body != null) {
            client = spec.contentType(contentType).bodyValue(body);
        }

        switch (accept.toString()) {
        case MediaType.TEXT_EVENT_STREAM_VALUE:
            return retrieveSSE(client).map(Val::of).onErrorResume(this::mapError);
        case MediaType.APPLICATION_JSON_VALUE:
            return poll(exchangeToMono(JsonNode.class, client).map(Val::of).onErrorResume(this::mapError),
                    pollingIntervallMs, repetitions);
        default:
            return poll(exchangeToMono(String.class, client).map(Val::of).onErrorResume(this::mapError),
                    pollingIntervallMs, repetitions);
        }
    }

    private void setHeaders(HttpHeaders headers, JsonNode requestHeaders) {
        requestHeaders.fields().forEachRemaining(field -> {
            var key   = field.getKey();
            var value = field.getValue();
            if (value.isArray()) {
                var elements = new ArrayList<String>();
                value.elements().forEachRemaining(e -> elements.add(e.asText()));
                headers.put(key, elements);
            } else {
                headers.set(key, value.asText());
            }
        });
    }

    private String baseUrl(Val requestSettings) {
        return requestSettings.fieldJsonNodeOrElseThrow(BASE_URL,
                () -> new PolicyEvaluationException(NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST_ERROR)).asText();
    }

    private long longOrDefault(Val requestSettings, String fieldName, long defaultValue) {
        var value = requestSettings.fieldJsonNodeOrElse(fieldName, () -> JSON.numberNode(defaultValue));
        if (!value.isNumber())
            throw new PolicyEvaluationException(
                    fieldName + " must be an integer in HTTP requestSpecification, but was: " + value.getNodeType());
        return value.longValue();
    }

    private Map<String, String> toStringMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, String>>() {
        });
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
        ParameterizedTypeReference<ServerSentEvent<JsonNode>> type = new ParameterizedTypeReference<ServerSentEvent<JsonNode>>() {
        };
        return client.retrieve().bodyToFlux(type).map(ServerSentEvent::data);
    }

    private Flux<Val> poll(Mono<Val> in, long pollingInterval, long repeatTimes) {
        return in.repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))));
    }

    private <T> Mono<T> exchangeToMono(Class<T> clazz, RequestHeadersSpec<?> in) {
        return in.retrieve()
                .onStatus(HttpStatusCode::isError,
                        clientResponse -> clientResponse.createException()
                                .flatMap(error -> Mono.error(new PolicyEvaluationException(error.getMessage()))))
                .bodyToMono(clazz);
    }

    private Mono<Val> mapError(Throwable e) {
        if (e instanceof WebClientResponseException clientException) {
            return Mono.just(Val.error(clientException.getRootCause()));
        }
        return Mono.just(Val.error(e));
    }

    public Flux<Val> consumeWebSocket(Val requestSettings) {
        var baseUrl        = baseUrl(requestSettings);
        var path           = requestSettings.fieldValOrElse(PATH, Val.of("")).get().asText();
        var requestHeaders = requestSettings.fieldJsonNodeOrElse(HEADERS, JSON::objectNode);
        var uri            = URI.create(baseUrl + path);
        var body           = requestSettings.fieldJsonNodeOrElse(BODY, (JsonNode) null);
        var client         = new ReactorNettyWebSocketClient();

        var headers = new HttpHeaders();
        setHeaders(headers, requestHeaders);

        Sinks.One<WebSocketSession> receiveBuffer = Sinks.one();
        client.execute(uri, headers, session -> handleSession(session, body, receiveBuffer)).subscribe();
        return receiveBuffer.asMono().flatMapMany(this::listenToIncomingDataFromWebSocket);
    }

    private Mono<Void> handleSession(WebSocketSession session, JsonNode body, One<WebSocketSession> receiveBuffer) {
        receiveBuffer.tryEmitValue(session);
        return body == null ? Mono.empty() : Mono.just(session.textMessage(body.asText())).then();
    }

    private Flux<Val> listenToIncomingDataFromWebSocket(WebSocketSession session) {
        return session.receive().map(WebSocketMessage::getPayloadAsText).concatMap(payload -> {
            try {
                return Mono.just(Val.ofJson(payload));
            } catch (JsonProcessingException e) {
                return Val.errorFlux(e.getMessage());
            }
        }).doOnTerminate(() -> session.close().subscribe());
    }

}
