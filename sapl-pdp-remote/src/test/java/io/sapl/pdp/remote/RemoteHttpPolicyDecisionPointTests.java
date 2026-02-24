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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import javax.net.ssl.SSLException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteHttpPolicyDecisionPointTests {

    private static final String ID = "id1";

    private static final String RESOURCE = "resource";

    private static final String ACTION = "action";

    private static final String SUBJECT = "subject";

    private static final JsonMapper      MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
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
    void whenSubscribingIncludingErrors_thenAfterErrorsCloseConnectionsAndReconnection() throws JacksonException {
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

        val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

        StepVerifier.create(pdp.decide(subscription))
                .expectNext(AuthorizationDecision.DENY, AuthorizationDecision.INDETERMINATE,
                        AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE,
                        AuthorizationDecision.NOT_APPLICABLE, AuthorizationDecision.INDETERMINATE)
                .thenCancel().verify();
    }

    @Test
    void whenSubscribingMultiDecideAll_thenGetResults() throws JacksonException {
        val decision1 = new MultiAuthorizationDecision();
        decision1.setDecision(ID, AuthorizationDecision.PERMIT);
        val decision2 = new MultiAuthorizationDecision();
        decision2.setDecision(ID, AuthorizationDecision.DENY);
        val indeterminate = MultiAuthorizationDecision.indeterminate();

        prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2, null });
        prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2 });

        val subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID,
                JSON.stringNode(SUBJECT), JSON.stringNode(ACTION), JSON.stringNode(RESOURCE));

        StepVerifier.create(pdp.decideAll(subscription))
                .expectNext(decision1, decision2, indeterminate, decision1, decision2).thenCancel().verify();
    }

    @Test
    void whenSubscribingMultiDecide_thenGetResults() throws JacksonException {
        val decision1     = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.PERMIT);
        val decision2     = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.DENY);
        val indeterminate = IdentifiableAuthorizationDecision.INDETERMINATE;

        prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2, null });
        prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2 });

        val subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID,
                JSON.stringNode(SUBJECT), JSON.stringNode(ACTION), JSON.stringNode(RESOURCE));

        StepVerifier.create(pdp.decide(subscription))
                .expectNext(decision1, decision2, indeterminate, decision1, decision2).thenCancel().verify();
    }

    private void prepareDecisions(Object[] decisions) throws JacksonException {
        StringBuilder body = new StringBuilder();
        for (var decision : decisions) {
            if (decision == null)
                body.append("data: INTENDED INVALID VALUE TO CAUSE AN ERROR\n\n");
            else {
                body.append("data: ").append(MAPPER.writeValueAsString(decision)).append("\n\n");
            }
        }
        val response = new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .setResponseCode(HttpStatus.OK.value()).setBody(body.toString());
        server.enqueue(response);
    }

    @Test
    void construct() {
        val pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").build();
        assertThat(pdpUnderTest).isNotNull();
    }

    @Test
    void constructWithSslContext() throws SSLException {
        val sslContext   = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        val pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").secure(sslContext).build();
        assertThat(pdpUnderTest).isNotNull();
    }

    @Test
    void settersAndGetters() {
        val pdpUnderTest = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                .basicAuth("secret", "key").build();
        pdpUnderTest.setBackoffFactor(999);
        pdpUnderTest.setFirstBackoffMillis(998);
        pdpUnderTest.setMaxBackOffMillis(1001);
        assertThat(pdpUnderTest).satisfies(p -> {
            assertThat(p.getBackoffFactor()).isEqualTo(999);
            assertThat(p.getFirstBackoffMillis()).isEqualTo(998);
            assertThat(p.getMaxBackOffMillis()).isEqualTo(1001);
        });
    }

}
