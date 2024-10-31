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
package io.sapl.pdp.remote;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

class RemoteHttpPolicyDecisionPointTests {

    private static final String ID = "id1";

    private static final String RESOURCE = "resource";

    private static final String ACTION = "action";

    private static final String SUBJECT = "subject";

    private static final ObjectMapper    MAPPER = new ObjectMapper().registerModule(new Jdk8Module());
    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;

    private MockWebServer server;

    private RemoteHttpPolicyDecisionPoint pdp;

    @BeforeAll
    static void setupLog() {
        // Route MockWebServer logs to shared logs
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        pdp = RemotePolicyDecisionPoint.builder().http().baseUrl(this.server.url("/").toString())
                .withHttpClient(HttpClient.create()).basicAuth("secret", "key").build();
        pdp.setBackoffFactor(2);
        pdp.setFirstBackoffMillis(100);
        pdp.setMaxBackOffMillis(200);
    }

    @AfterEach
    void shutdownServer() throws IOException {
        server.shutdown();
    }

    @Test
    void whenSubscribingIncludingErrors_thenAfterErrorsCloseConnectionsAndReconnection()
            throws JsonProcessingException {
        // The first is propagated. The second results in an error. The third is dropped
        // due to the error
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
    void whenSubscribingMultiDecideAll_thenGetResults() throws JsonProcessingException {
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
    void whenSubscribingMultiDecide_thenGetResults() throws JsonProcessingException {
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

    private void prepareDecisions(Object[] decisions) throws JsonProcessingException {
        StringBuilder body = new StringBuilder();
        for (var decision : decisions) {
            if (decision == null)
                body.append("data: INTENDED INVALID VALUE TO CAUSE AN ERROR\n\n");
            else {
                /* "text/event-stream" */
                body.append("data: ").append(MAPPER.writeValueAsString(decision)).append("\n\n");
            }
        }
        final var response = new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE /* .APPLICATION_NDJSON_VALUE */)
                .setResponseCode(HttpStatus.OK.value()).setBody(body.toString());
        server.enqueue(response);
    }

    @Test
    void construct() {
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").build();
        assertThat(pdpUnderTest, notNullValue());
    }

    @Test
    void constructWithSslContext() throws SSLException {
        final var sslContext   = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").secure(sslContext).build();
        assertThat(pdpUnderTest, notNullValue());
    }

    @Test
    void settersAndGetters() {
        final var pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").build();
        pdpUnderTest.setBackoffFactor(999);
        pdpUnderTest.setFirstBackoffMillis(998);
        pdpUnderTest.setMaxBackOffMillis(1001);
        assertAll(() -> assertThat(pdpUnderTest.getBackoffFactor(), is(999)),
                () -> assertThat(pdpUnderTest.getFirstBackoffMillis(), is(998)),
                () -> assertThat(pdpUnderTest.getMaxBackOffMillis(), is(1001)));
    }

}
