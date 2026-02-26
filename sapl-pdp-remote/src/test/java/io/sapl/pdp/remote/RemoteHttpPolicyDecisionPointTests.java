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
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        pdp.setFirstBackoffMillis(100);
        pdp.setMaxBackOffMillis(200);
        pdp.setTimeoutMillis(5000);
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
        pdpUnderTest.setFirstBackoffMillis(998);
        pdpUnderTest.setMaxBackOffMillis(1001);
        pdpUnderTest.setTimeoutMillis(997);
        assertThat(pdpUnderTest).satisfies(p -> {
            assertThat(p.getFirstBackoffMillis()).isEqualTo(998);
            assertThat(p.getMaxBackOffMillis()).isEqualTo(1001);
            assertThat(p.getTimeoutMillis()).isEqualTo(997);
        });
    }

    @Nested
    @DisplayName("Authentication validation")
    class AuthenticationValidation {

        @Test
        @DisplayName("Builder rejects both basic and API key authentication (REQ-AUTH-4)")
        void whenDualAuthConfiguredThenThrows() {
            assertThatThrownBy(() -> RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost")
                    .basicAuth("secret", "key").apiKey("my-api-key").build()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("authentication method already defined");
        }
    }

    @Nested
    @DisplayName("Request-response behavior")
    class RequestResponseBehavior {

        @Test
        @Timeout(5)
        @DisplayName("decideOnce does not retry on failure (REQ-RR-3)")
        void whenRequestResponseFailsThenNoRetry() {
            server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decideOnce(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                    .verifyComplete();

            assertThat(server.getRequestCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SSE stream handling")
    class SseStreamHandling {

        @Test
        @Timeout(5)
        @DisplayName("Oversized SSE data line triggers INDETERMINATE (REQ-SSE-5)")
        void whenSseLineExceedsBufferLimitThenIndeterminate() {
            val largeAdvice   = "x".repeat(512 * 1024);
            val oversizedBody = "data: {\"decision\":\"PERMIT\",\"advice\":[\"" + largeAdvice + "\"]}\n\n";
            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .setResponseCode(HttpStatus.OK.value()).setBody(oversizedBody));

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decide(subscription))
                    .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE))
                    .thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("Decision deduplication")
    class DecisionDeduplication {

        @Test
        @Timeout(5)
        @DisplayName("Deeply nested decisions with different leaf values are not deduplicated (REQ-DEDUP-2)")
        void whenDeeplyNestedDecisionsThenTreatedAsDifferent() throws JacksonException {
            val deepValue1 = createDeeplyNestedValue("value1", 20);
            val deepValue2 = createDeeplyNestedValue("value2", 20);

            val decision1 = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(deepValue1), Value.EMPTY_ARRAY,
                    Value.UNDEFINED);
            val decision2 = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(deepValue2), Value.EMPTY_ARRAY,
                    Value.UNDEFINED);

            prepareDecisions(new AuthorizationDecision[] { decision1, decision2 });

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decide(subscription)).expectNextCount(2).thenCancel().verify();
        }

        private Value createDeeplyNestedValue(String leafValue, int depth) {
            Value current = Value.of(leafValue);
            for (var i = 0; i < depth; i++) {
                current = Value.ofObject(Map.of("level" + i, current));
            }
            return current;
        }
    }

}
