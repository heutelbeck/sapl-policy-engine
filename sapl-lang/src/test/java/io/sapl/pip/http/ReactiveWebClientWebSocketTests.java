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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.pip.http.ReactiveWebClientWebSocketTests.WebSocketConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Import(WebSocketConfig.class)
@EnableAutoConfiguration
@SpringBootTest(properties = "spring.main.web-application-type=reactive", webEnvironment = WebEnvironment.RANDOM_PORT)
class ReactiveWebClientWebSocketTests {

    @LocalServerPort
    private Integer port;

    @Test
    void when_sendBodyToEcho_then_receiveEcho() throws JsonProcessingException, InterruptedException {
        var template        = """
                {
                    "baseUrl" : "%s",
                    "body" : "\\\"hello\\\""
                }
                """;
        var httpTestRequest = Val.ofJson(String.format(template, "ws://localhost:" + port + "/echo"));
        var streamUnderTest = new ReactiveWebClient(new ObjectMapper()).consumeWebSocket(httpTestRequest).next();
        StepVerifier.create(streamUnderTest).expectNext(Val.of("hello")).expectComplete()
                .verify(Duration.ofSeconds(5L));
    }

    @Test
    void when_sendBodyNonJSONToEcho_then_receiveEchoButError() throws JsonProcessingException, InterruptedException {
        var template        = """
                {
                    "baseUrl" : "%s",
                    "body" : "hello"
                }
                """;
        var httpTestRequest = Val.ofJson(String.format(template, "ws://localhost:" + port + "/echo"));
        var streamUnderTest = new ReactiveWebClient(new ObjectMapper()).consumeWebSocket(httpTestRequest).next();
        StepVerifier.create(streamUnderTest)
                .expectNextMatches(val -> val.isError() && val.getMessage().contains("Unrecognized token"))
                .expectComplete().verify(Duration.ofSeconds(5L));
    }

    @Test
    void when_sendNoBodyToCounter_then_receiveStreamOfNumbers() throws JsonProcessingException, InterruptedException {
        var template        = """
                {
                    "baseUrl" : "%s"
                }
                """;
        var httpTestRequest = Val.ofJson(String.format(template, "ws://localhost:" + port + "/counter"));
        var streamUnderTest = new ReactiveWebClient(new ObjectMapper()).consumeWebSocket(httpTestRequest).take(3);
        StepVerifier.create(streamUnderTest).expectNext(Val.of(0)).expectNext(Val.of(1)).expectNext(Val.of(2))
                .expectComplete().verify(Duration.ofSeconds(5L));
    }

    @Component
    public static class EchoHandler implements WebSocketHandler {
        @Override
        public Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession.send(webSocketSession.receive().map(WebSocketMessage::getPayloadAsText)
                    .map(webSocketSession::textMessage));
        }
    }

    @Component
    public static class CounterHandler implements WebSocketHandler {
        @Override
        public Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession
                    .send(Flux.interval(Duration.ofMillis(50)).map(Object::toString).map(webSocketSession::textMessage))
                    .and(webSocketSession.receive().map(WebSocketMessage::getPayloadAsText));
        }
    }

    @Configuration
    public static class WebSocketConfig {

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
