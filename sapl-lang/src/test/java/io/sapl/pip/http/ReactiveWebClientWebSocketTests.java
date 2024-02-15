package io.sapl.pip.http;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.HtmlUtils;

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
    private final static String MESSAGE = """
            {
                "data" : 123
            }
            """;


    @Component
    public static class EchoHandler implements WebSocketHandler {
        @Override
        public Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession.send(webSocketSession.receive().doOnNext(System.out::println).map(WebSocketMessage::getPayloadAsText)
                    .map(webSocketSession::textMessage));
        }
    }
    
    public record Request(String name) {
    }

    public record Response(String message) {
    }

    public record Event(String eventId, String eventDt) {
    }

    @LocalServerPort
    private Integer port;

    @Component
    public static class EventEmitter implements WebSocketHandler {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private Flux<String> eventFlux = Flux.generate(sink -> {
            Event event = new Event(UUID.randomUUID().toString(), Instant.now().toString());
            try {
                sink.next(MAPPER.writeValueAsString(event));
            } catch (JsonProcessingException e) {
                sink.error(e);
            }
        });

        private Flux<String> intervalFlux = Flux.interval(Duration.ofMillis(1000L)).zipWith(eventFlux,
                (time, event) -> event);

        @Override
        public Mono<Void> handle(WebSocketSession webSocketSession) {
            return webSocketSession.send(intervalFlux.map(webSocketSession::textMessage))
                    .and(webSocketSession.receive().map(WebSocketMessage::getPayloadAsText).log());
        }
    }

    @Configuration
    public static class WebSocketConfig {

        @Bean
        EventEmitter reactiveWebSocketHandler() {
            return new EventEmitter();
        }
        @Bean
        EchoHandler echoHandler() {
            return new EchoHandler();
        }

        @Bean
        HandlerMapping webSocketHandlerMapping(EventEmitter webSocketHandler, EchoHandler echoHandler) {
            Map<String, WebSocketHandler> map = new HashMap<>();
            map.put("/event-emitter", webSocketHandler);
            map.put("/echo", echoHandler);

            SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
            handlerMapping.setOrder(1);
            handlerMapping.setUrlMap(map);
            return handlerMapping;
        }
    }

    @Test
    void testEventStream() throws JsonProcessingException {
        var template        = """
                {
                    "baseUrl" : "%s",
                    "body" : "hello"
                }
                """;
        var httpTestRequest = Val.ofJson(String.format(template, "ws://localhost:" + port + "/event-emitter"));
        var clientUnderTest = new ReactiveWebClient(new ObjectMapper());
        StepVerifier.create(clientUnderTest.consumeWebSocket(httpTestRequest)).expectNext(Val.ofEmptyArray())
                .verifyComplete();
    }
}
