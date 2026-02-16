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

import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.libraries.ReactiveWebClientWebSocketTests.WebSocketConfig;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Import(WebSocketConfig.class)
@EnableAutoConfiguration
@SpringBootTest(properties = "spring.main.web-application-type=reactive", webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("ReactiveWebClientWebSocket")
class ReactiveWebClientWebSocketTests {

    @LocalServerPort
    private Integer port;

    @Test
    void whenSendBodyToEchoThenReceiveEcho() {
        val template        = """
                {
                    "baseUrl" : "ws://localhost:%d/echo",
                    "body" : "\\"hello\\""
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller.json(template.formatted(port));
        val streamUnderTest = new ReactiveWebClient(JsonMapper.builder().build()).consumeWebSocket(httpTestRequest)
                .next();
        StepVerifier.create(streamUnderTest).expectNext(Value.of("hello")).expectComplete()
                .verify(Duration.ofSeconds(5L));
    }

    @Test
    void whenSendBodyNonJSONToEchoThenReceiveEchoButError() {
        val template        = """
                {
                    "baseUrl" : "ws://localhost:%d/echo",
                    "body" : "hello"
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller.json(template.formatted(port));
        val streamUnderTest = new ReactiveWebClient(JsonMapper.builder().build()).consumeWebSocket(httpTestRequest)
                .next();
        StepVerifier.create(streamUnderTest).expectNextMatches(
                val -> (val instanceof ErrorValue) && ((ErrorValue) val).message().contains("Unrecognized token"))
                .expectComplete().verify(Duration.ofSeconds(5L));
    }

    @Test
    void whenSendNoBodyToCounterThenReceiveStreamOfNumbers() {
        val template        = """
                {
                    "baseUrl" : "ws://localhost:%d/counter"
                }
                """;
        val httpTestRequest = (ObjectValue) ValueJsonMarshaller.json(template.formatted(port));
        val streamUnderTest = new ReactiveWebClient(JsonMapper.builder().build()).consumeWebSocket(httpTestRequest)
                .take(3);
        StepVerifier.create(streamUnderTest).expectNext(Value.of(0)).expectNext(Value.of(1)).expectNext(Value.of(2))
                .expectComplete().verify(Duration.ofSeconds(15L));
    }

    static class EchoHandler implements WebSocketHandler {
        @Override
        public @NonNull Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession.send(webSocketSession.receive().map(WebSocketMessage::getPayloadAsText)
                    .map(webSocketSession::textMessage));
        }
    }

    static class CounterHandler implements WebSocketHandler {
        @Override
        public @NonNull Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession
                    .send(Flux.interval(Duration.ofMillis(100)).map(Object::toString)
                            .map(webSocketSession::textMessage))
                    .and(webSocketSession.receive().map(WebSocketMessage::getPayloadAsText));
        }
    }

    @Configuration
    static class WebSocketConfig {

        @Bean
        CounterHandler reactiveWebSocketHandler() {
            return new CounterHandler();
        }

        @Bean
        EchoHandler echoHandler() {
            return new EchoHandler();
        }

        @Bean
        HandlerMapping webSocketHandlerMapping(CounterHandler counterHandler, EchoHandler echoHandler) {
            Map<String, WebSocketHandler> map = new HashMap<>();
            map.put("/counter", counterHandler);
            map.put("/echo", echoHandler);

            SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
            handlerMapping.setOrder(1);
            handlerMapping.setUrlMap(map);
            return handlerMapping;
        }
    }

}
