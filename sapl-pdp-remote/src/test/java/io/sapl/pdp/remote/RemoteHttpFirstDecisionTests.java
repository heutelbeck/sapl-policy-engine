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
package io.sapl.pdp.remote;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Timeout(5)
@DisplayName("HTTP first-decision deadline and heartbeats")
class RemoteHttpFirstDecisionTests {

    private static final AuthorizationSubscription INPUT   = AuthorizationSubscription.of("alice", "read", "doc");
    private static final String                    COMMENT = ": keep-alive\n\n";
    private static final String                    PERMIT  = """
            data:{"decision":"PERMIT"}

            """;
    private static final String                    DENY    = """
            data:{"decision":"DENY"}

            """;

    @ParameterizedTest(name = "keepalive before decision: {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("A decision within the deadline is delivered with or without a preceding heartbeat")
    void whenFirstDecisionArrivesInTimeThenItIsDelivered(boolean comment) {
        StepVerifier.withVirtualTime(() -> client(() -> first(comment, 500)).decide(INPUT)).expectSubscription()
                .thenAwait(Duration.ofMillis(500)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @ParameterizedTest(name = "keepalive before decision: {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("A heartbeat cannot replace a first decision that missed the deadline")
    void whenFirstDecisionArrivesLateThenItIsIndeterminate(boolean comment) {
        StepVerifier.withVirtualTime(() -> client(() -> first(comment, 1500)).decide(INPUT)).expectSubscription()
                .expectNoEvent(Duration.ofMillis(999)).thenAwait(Duration.ofMillis(1))
                .expectNext(AuthorizationDecision.INDETERMINATE).thenCancel().verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Heartbeats keep a quiet stream alive after its first decision")
    void whenFirstDecisionWasDeliveredThenHeartbeatsKeepTheStreamAlive() {
        StepVerifier
                .withVirtualTime(() -> client(() -> Flux.concat(Flux.just(PERMIT),
                        Flux.interval(Duration.ofMillis(750)).take(4).map(ignored -> COMMENT),
                        Mono.delay(Duration.ofMillis(750)).thenReturn(DENY), Flux.never())).decide(INPUT))
                .expectSubscription().expectNext(AuthorizationDecision.PERMIT).thenAwait(Duration.ofMillis(3750))
                .expectNext(AuthorizationDecision.DENY).thenCancel().verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Each independent subscription gets its own full first-decision budget")
    void whenSubscribedAgainThenTheFirstDecisionDeadlineStartsAgain() {
        StepVerifier.withVirtualTime(() -> {
            val pdp = client(() -> first(true, 1500));
            return pdp.decide(INPUT).take(1).concatWith(pdp.decide(INPUT).take(1));
        }).expectSubscription().expectNoEvent(Duration.ofMillis(999)).thenAwait(Duration.ofMillis(1))
                .expectNext(AuthorizationDecision.INDETERMINATE).expectNoEvent(Duration.ofMillis(999))
                .thenAwait(Duration.ofMillis(1)).expectNext(AuthorizationDecision.INDETERMINATE).verifyComplete();
    }

    @Test
    @DisplayName("A retry gets a fresh deadline and can deliver a healthy first decision")
    void whenFirstAttemptTimesOutThenAHealthyReconnectDeliversItsDecision() {
        val attempts = new AtomicInteger();
        StepVerifier
                .withVirtualTime(
                        () -> client(() -> first(true, attempts.getAndIncrement() == 0 ? 1500 : 500)).decide(INPUT))
                .expectSubscription().thenAwait(Duration.ofSeconds(1)).expectNext(AuthorizationDecision.INDETERMINATE)
                .thenAwait(Duration.ofMillis(600)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                .verify(Duration.ofSeconds(2));
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Silence after the first decision still causes a reconnect")
    void whenHeartbeatsAndDecisionsStopThenTheClientReconnects() {
        val attempts = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> client(() -> {
            attempts.incrementAndGet();
            return Flux.concat(Flux.just(PERMIT), Flux.never());
        }).decide(INPUT)).expectSubscription().expectNext(AuthorizationDecision.PERMIT).thenAwait(Duration.ofSeconds(2))
                .expectNext(AuthorizationDecision.INDETERMINATE).thenAwait(Duration.ofMillis(100))
                .expectNext(AuthorizationDecision.PERMIT).thenCancel().verify(Duration.ofSeconds(2));
        assertThat(attempts.get()).isEqualTo(2);
    }

    private static Flux<String> first(boolean comment, int delayMillis) {
        val before = comment ? Flux.just(COMMENT) : Flux.<String>empty();
        return Flux.concat(before, Mono.delay(Duration.ofMillis(delayMillis)).thenReturn(PERMIT), Flux.never());
    }

    private static RemoteHttpReactivePolicyDecisionPoint client(Supplier<Flux<String>> events) {
        val mapper     = SaplJacksonModule.jsonMapperBuilder().build();
        val strategies = ExchangeStrategies.builder().codecs(codecs -> {
                           codecs.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
                           codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                       }).build();
        val webClient  = WebClient.builder().baseUrl("http://pdp.example").exchangeStrategies(strategies)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK, strategies)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(events.get()
                                .map(text -> DefaultDataBufferFactory.sharedInstance.wrap(text.getBytes(UTF_8))))
                        .build()))
                .build();
        val pdp        = new RemoteHttpReactivePolicyDecisionPoint(webClient);
        pdp.setTimeoutMillis(1000);
        pdp.setInactivityTimeoutMillis(2000);
        pdp.setFirstBackoffMillis(100);
        pdp.setMaxBackOffMillis(100);
        return pdp;
    }
}
