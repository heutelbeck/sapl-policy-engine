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
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.retry.Repeat;

@Slf4j
@RequiredArgsConstructor
public class ReactiveWebClient {

    private final ObjectMapper mapper;

    private static final String          ACCEPT_MEDIATYPE  = "acceptMediaType";
    private static final String          CONTENT_MEDIATYPE = "contentType";
    private static final JsonNodeFactory JSON              = JsonNodeFactory.instance;

    private Sinks.Many<String>   sendBuffer;
    private Sinks.Many<JsonNode> receiveBuffer;

    @Getter
    private WebSocketSession webSocketSession;
    private Disposable       subscription;

    public Flux<JsonNode> connectToSocket(JsonNode requestSettings) {
        try {
            return connectToSocket(JsonHandler.getJsonBaseUrl(requestSettings),
                    JsonHandler.getJsonPath(requestSettings), JsonHandler.getJsonHeaders(requestSettings, mapper));
        } catch (RequestSettingException e) {
            return Flux.error(e);
        }
    }

    public Flux<JsonNode> connectToSocket(String baseUrl, String path, Map<String, String> requestHeaders) {

        URI                         uri    = URI.create(baseUrl + path);
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();
        sendBuffer        = Sinks.many().unicast().onBackpressureBuffer();
        receiveBuffer     = Sinks.many().unicast().onBackpressureBuffer();
        this.subscription = client
                .execute(uri, RequestHandler.setHeaders(new HttpHeaders(), requestHeaders), this::handleSession)
                .then(Mono.fromRunnable(this::onClose)).subscribe();
        return receiveBuffer.asFlux();
    }

    public void disconnectSocket() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            subscription = null;
            onClose();
        }
    }

    /**
     * <p>
     * Connects to an endpoint and produces a Flux<JsonNode>
     * </p>
     *
     * @param the @see HttpMethod to execute and a @see JsonNode containing the
     *            settings
     * @return a @see Flux<@see JsonNode>
     */
    public Flux<JsonNode> connect(HttpMethod method, JsonNode requestSettings) {
        try {
            return connect(method, JsonHandler.getJsonBaseUrl(requestSettings),
                    JsonHandler.getJsonPath(requestSettings), JsonHandler.getJsonUrlParams(requestSettings, mapper),
                    JsonHandler.getJsonHeaders(requestSettings, mapper),
                    JsonHandler.getJsonBody(requestSettings, mapper),
                    JsonHandler.getJsonMediaType(requestSettings, ACCEPT_MEDIATYPE),
                    JsonHandler.getJsonMediaType(requestSettings, CONTENT_MEDIATYPE),
                    JsonHandler.getJsonPollingInterval(requestSettings),
                    JsonHandler.getJsonRepeatTimes(requestSettings));
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    public Flux<JsonNode> connect(HttpMethod method, String baseUrl, String path, Map<String, String> urlParameters,
            Map<String, String> requestHeaders, JsonNode body, MediaType acceptMediaType, MediaType contentType,
            int pollingInterval, long repeatTimes) {

        RequestHeadersSpec<?> client;

        if (method == HttpMethod.GET) {

            log.debug("case get: {}", method.toString());
            client = RequestHandler.setGetRequest(createWebClient(baseUrl).method(method), path, urlParameters,
                    requestHeaders, acceptMediaType);
        } else {
            log.debug("case method default: {}", method.toString());
            client = RequestHandler.setRequest(createWebClient(baseUrl).method(method), path, urlParameters,
                    requestHeaders, body, acceptMediaType, contentType);
        }

        switch (acceptMediaType.toString()) {
        case MediaType.TEXT_EVENT_STREAM_VALUE: // text/event-stream
            try {
                log.debug("case sse");
                return retrieveSSE(client)
                        .doOnNext(r -> log.debug("ReactiveWebClient sse receiving: {}", r.toString()));
            } catch (Exception e) {
                return Flux.error(e);
            }

        case MediaType.TEXT_PLAIN_VALUE: // text/plain
            log.debug("case string");
            return returnFlux(exchangeToMono(String.class, client).map(JSON::textNode), pollingInterval, repeatTimes)
                    .doOnNext(r -> log.debug("ReactiveWebClient String receiving: ", r.toString()));

        default:
            log.debug("case flux");
            return returnFlux(exchangeToMono(JsonNode.class, client), pollingInterval, repeatTimes)
                    .doOnNext(r -> log.debug("ReactiveWebClient default receiving: ", r.toString()));

        }

    }

    // private methods

    private WebClient createWebClient(String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    private Flux<JsonNode> retrieveSSE(RequestHeadersSpec<?> client) {

        ParameterizedTypeReference<ServerSentEvent<JsonNode>> type = new ParameterizedTypeReference<ServerSentEvent<JsonNode>>() {
        };

        return client.retrieve().bodyToFlux(type).doOnNext(r -> log.debug("ReactiveWebClient SSE: {}", r))
                .map(e -> e.data());

    }

    private Flux<JsonNode> returnFlux(Mono<JsonNode> in, int pollingInterval, long repeatTimes) {
        return in.repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofSeconds(pollingInterval))));
    }

    private <T> Mono<T> exchangeToMono(Class<T> clazz, RequestHeadersSpec<?> in) {

        return in.retrieve()
                .onStatus(HttpStatusCode::isError,
                        clientResponse -> clientResponse.createException()
                                .flatMap(error -> Mono.error(new RuntimeException(error.getMessage()))))
                .bodyToMono(clazz);

    }

    private Mono<Void> handleSession(WebSocketSession session) {
        onOpen(session);

        Mono<Void> input  = session.receive().map(WebSocketMessage::getPayloadAsText)
                .map(res -> mapper.convertValue(res, JsonNode.class)).doOnNext(res -> {
                                      receiveBuffer.tryEmitNext(res);
                                      log.debug(
                                              (String.format("ReactiveWebClient socket output: %1$s", res.toString())));
                                  })
                .then();
        Mono<Void> output = session.send(sendBuffer.asFlux().map(session::textMessage));
        return Mono.zip(input, output).then();
    }

    private void onOpen(WebSocketSession session) {
        this.webSocketSession = session;
        log.debug("ReactiveWebClient: Session opened");
    }

    private void onClose() {
        webSocketSession.close();
        webSocketSession = null;
        log.debug("ReactiveWebClient: Session closed");
    }
}
