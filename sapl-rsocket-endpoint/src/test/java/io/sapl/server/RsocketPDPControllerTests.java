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
package io.sapl.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.TestSocketUtils;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.server.pdpcontroller.RSocketPDPController;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.tcp.TcpClient;
import reactor.test.StepVerifier;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({ PolicyDecisionPoint.class, RSocketMessageHandler.class })
@ContextConfiguration(classes = { RsocketPDPControllerTests.class })
class RsocketPDPControllerTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final int  serverPort = TestSocketUtils.findAvailableTcpPort();
    private Disposable server;

    @MockitoBean
    private PolicyDecisionPoint pdp;

    final RSocketStrategies  rSocketStrategies = RSocketStrategies.builder().encoder(new Jackson2JsonEncoder())
            .decoder(new Jackson2JsonDecoder()).build();
    private RSocketRequester requester;

    @BeforeAll
    public void startRsocketServer() {
        SocketAcceptor responder = RSocketMessageHandler.responder(rSocketStrategies, new RSocketPDPController(pdp));
        this.server = RSocketServer.create(responder).payloadDecoder(PayloadDecoder.ZERO_COPY)
                .bind(TcpServerTransport.create(serverPort)).block();

        this.requester = createRSocketRequester();
    }

    RSocketRequester createRSocketRequester() {
        final var builder = RSocketRequester.builder().rsocketStrategies(rSocketStrategies);
        return builder.transport(TcpClientTransport.create(TcpClient.create().port(serverPort)));
    }

    @Test
    void decideWithValidPayload() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY,
                AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("decide").data(subscription).retrieveFlux(AuthorizationDecision.class);

        StepVerifier.create(result).expectNext(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT,
                AuthorizationDecision.INDETERMINATE).verifyComplete();
        verify(pdp, times(1)).decide(subscription);
    }

    @AfterAll
    public void tearDown() {
        server.dispose();
    }

    @Test
    void decideOnceValidPayload() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY,
                AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("decide-once").data(subscription)
                .retrieveMono(AuthorizationDecision.class);

        StepVerifier.create(result).expectNext(AuthorizationDecision.DENY).verifyComplete();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideWithValidProcessingError() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("decide").data(subscription).retrieveFlux(AuthorizationDecision.class);

        StepVerifier.create(result).expectNext(AuthorizationDecision.INDETERMINATE).verifyComplete();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideOnceWithValidProcessingError() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("decide-once").data(subscription)
                .retrieveMono(AuthorizationDecision.class);

        StepVerifier.create(result).expectNext(AuthorizationDecision.INDETERMINATE).verifyComplete();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideWithInvalidBody() {
        final var result = requester.route("decide").data("INVALID BODY").retrieveMono(AuthorizationDecision.class);
        StepVerifier.create(result).verifyError();
    }

    @Test
    void subscribeToMultiDecisions() {
        when(pdp.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.just(
                IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE,
                IdentifiableAuthorizationDecision.INDETERMINATE));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = requester.route("multi-decide").data(multiAuthzSubscription)
                .retrieveFlux(IdentifiableAuthorizationDecision.class);

        StepVerifier.create(result).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE,
                IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE)
                .verifyComplete();

        verify(pdp, times(1)).decide(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsProcessingError() {
        when(pdp.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = requester.route("multi-decide").data(multiAuthzSubscription)
                .retrieveFlux(IdentifiableAuthorizationDecision.class);

        StepVerifier.create(result).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE).verifyComplete();

        verify(pdp, times(1)).decide(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsInvalidBody() {
        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("multi-decide").data(subscription)
                .retrieveFlux(IdentifiableAuthorizationDecision.class);
        StepVerifier.create(result).expectError().verify();
    }

    @Test
    void subscribeToMultiAllDecisions() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class)))
                .thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = requester.route("multi-decide-all").data(multiAuthzSubscription)
                .retrieveFlux(MultiAuthorizationDecision.class);

        StepVerifier
                .create(result).expectNext(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate())
                .verifyComplete();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void oneMultiAllDecisions() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class)))
                .thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = requester.route("multi-decide-all-once").data(multiAuthzSubscription)
                .retrieveMono(MultiAuthorizationDecision.class);

        StepVerifier.create(result).expectNext(MultiAuthorizationDecision.indeterminate()).verifyComplete();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiAllDecisionsProcessingError() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = requester.route("multi-decide-all-once").data(multiAuthzSubscription)
                .retrieveMono(MultiAuthorizationDecision.class);

        StepVerifier.create(result).expectNext(MultiAuthorizationDecision.indeterminate()).verifyComplete();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsAllInvalidBody() {
        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        final var result       = requester.route("multi-decide-all").data(subscription)
                .retrieveFlux(IdentifiableAuthorizationDecision.class);
        StepVerifier.create(result).expectError().verify();
    }
}
