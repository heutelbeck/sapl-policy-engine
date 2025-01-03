/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.pdp.remote.metadata.SimpleAuthenticationEncoder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class RemoteRsocketPolicyDecisionPointTests {

    private static CloseableChannel                 server;
    private static RemoteRsocketPolicyDecisionPoint pdp;

    private static final String ID = "id1";

    private static final String RESOURCE = "resource";

    private static final String ACTION = "action";

    private static final String SUBJECT = "subject";

    private static AnnotationConfigApplicationContext context;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @BeforeAll
    public static void setupOnce() {

        // create a Spring context for this test suite and obtain some beans
        context = new AnnotationConfigApplicationContext(ServerConfig.class);

        // Create an RSocket server for use in testing
        RSocketMessageHandler messageHandler = context.getBean(RSocketMessageHandler.class);
        server = RSocketServer.create(messageHandler.responder()).payloadDecoder(PayloadDecoder.ZERO_COPY)
                .bind(TcpServerTransport.create("localhost", 0)).block();
        pdp    = RemotePolicyDecisionPoint.builder().rsocket().host("localhost")
                .keepAlive(Duration.ofSeconds(20), Duration.ofSeconds(90)).port(server.address().getPort()).build();
    }

    @AfterAll
    public static void tearDownOnce() {
        server.dispose();
        context.close();
    }

    private void prepareDecisions(Object[] decisions) {
        ServerController.prepareDecisions(decisions);
    }

    @Test
    void whenSubscribingIncludingErrors_thenAfterErrorsCloseConnectionsAndReconnection() {
        // The first is propagated. The second results in an error. The third is dropped
        // due to the errorprepareDecisions(
        prepareDecisions(
                new AuthorizationDecision[] { AuthorizationDecision.DENY, null, AuthorizationDecision.PERMIT });
        prepareDecisions(
                new AuthorizationDecision[] { AuthorizationDecision.PERMIT, null, AuthorizationDecision.PERMIT });
        prepareDecisions(new AuthorizationDecision[] { AuthorizationDecision.INDETERMINATE, null,
                AuthorizationDecision.PERMIT });
        prepareDecisions(new AuthorizationDecision[] { AuthorizationDecision.NOT_APPLICABLE, null,
                AuthorizationDecision.PERMIT });

        final var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);
        StepVerifier.create(pdp.decide(subscription))
                .expectNext(AuthorizationDecision.DENY, AuthorizationDecision.INDETERMINATE,
                        AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE,
                        AuthorizationDecision.NOT_APPLICABLE, AuthorizationDecision.INDETERMINATE)
                .thenCancel().verify();
    }

    @Test
    void whenSubscribingMultiDecideAll_thenGetResults() {
        final var decision1 = new MultiAuthorizationDecision();
        decision1.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
        final var decision2 = new MultiAuthorizationDecision();
        decision2.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.DENY);
        final var indeterminate = MultiAuthorizationDecision.indeterminate();

        prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2, null });
        prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2 });

        final var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID,
                JSON.textNode(SUBJECT), JSON.textNode(ACTION), JSON.textNode(RESOURCE));

        StepVerifier.create(pdp.decideAll(subscription))
                .expectNext(decision1, decision2, indeterminate, decision1, decision2).thenCancel().verify();
    }

    @Test
    void whenSubscribingMultiDecide_thenGetResults() {
        final var decision1     = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.PERMIT);
        final var decision2     = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.DENY);
        final var indeterminate = IdentifiableAuthorizationDecision.INDETERMINATE;

        prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2, null });
        prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2 });

        final var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID,
                JSON.textNode(SUBJECT), JSON.textNode(ACTION), JSON.textNode(RESOURCE));

        StepVerifier.create(pdp.decide(subscription))
                .expectNext(decision1, decision2, indeterminate, decision1, decision2).thenCancel().verify();
    }

    /**
     * Fake Spring @Controller class which is a stand-in 'test rig' for our real
     * server. It contains a custom @ConnectMapping that tests if our ClientHandler
     * is responding to server-side calls for telemetry data.
     */
    @Controller
    static class ServerController {
        private static final Queue<Object[]> decisionsQueue = new LinkedList<>();

        private static void prepareDecisions(Object[] decisions) {
            decisionsQueue.add(decisions);
        }

        @MessageMapping("decide")
        Flux<AuthorizationDecision> fakeDecide() {
            return Flux.fromArray((AuthorizationDecision[]) decisionsQueue.remove());
        }

        @MessageMapping("multi-decide")
        public Flux<IdentifiableAuthorizationDecision> fakeMultiDecide() {
            return Flux.fromArray((IdentifiableAuthorizationDecision[]) decisionsQueue.remove());
        }

        @MessageMapping("multi-decide-all")
        public Flux<MultiAuthorizationDecision> fakeMultiDecideAll() {
            return Flux.fromArray((MultiAuthorizationDecision[]) decisionsQueue.remove());
        }

    }

    /**
     * This test-specific configuration allows Spring to help configure our test
     * environment. These beans will be placed into the Spring context and can be
     * accessed when required.
     */
    @TestConfiguration
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "WI_MANUALLY_ALLOCATING_AN_AUTOWIRED_BEAN")
    static class ServerConfig {

        @Bean
        ServerController serverController() {
            return new ServerController();
        }

        @Bean
        RSocketMessageHandler serverMessageHandler() {
            RSocketMessageHandler handler    = new RSocketMessageHandler();
            final var             strategies = RSocketStrategies.builder().encoder(new Jackson2JsonEncoder())
                    .encoder(new SimpleAuthenticationEncoder()).decoder(new Jackson2JsonDecoder()).build();
            handler.setRSocketStrategies(strategies);
            return handler;
        }
    }

    @Test
    void construct() {
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().rsocket().host("localhost").port(7000)
                .basicAuth("secret", "key").build();
        assertThat(pdpUnderTest, notNullValue());
    }

    @Test
    void constructWithSslContext() throws SSLException {
        final var sslContext   = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().rsocket().host("localhost").port(7000)
                .basicAuth("secret", "key").secure(sslContext).build();
        assertThat(pdpUnderTest, notNullValue());
    }

    @Test
    void settersAndGetters() {
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().rsocket().host("localhost").port(7000)
                .basicAuth("secret", "key").build();
        pdpUnderTest.setBackoffFactor(999);
        pdpUnderTest.setFirstBackoffMillis(998);
        pdpUnderTest.setMaxBackOffMillis(1001);
        assertAll(() -> assertThat(pdpUnderTest.getBackoffFactor(), is(999)),
                () -> assertThat(pdpUnderTest.getFirstBackoffMillis(), is(998)),
                () -> assertThat(pdpUnderTest.getMaxBackOffMillis(), is(1001)));
    }

}
