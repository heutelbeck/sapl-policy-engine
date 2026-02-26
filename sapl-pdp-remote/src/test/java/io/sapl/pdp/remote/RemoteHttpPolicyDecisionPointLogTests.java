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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteHttpPolicyDecisionPointLogTests {

    private static final String SUBJECT      = "subject";
    private static final String ACTION       = "action";
    private static final String RESOURCE     = "resource";
    private static final String SECRET_VALUE = "TOP_SECRET_CREDENTIAL_12345";
    private static final String AUTH_SECRET  = "my-secret-api-key-67890";

    private MockWebServer               server;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger                      pdpLogger;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        logAppender = new ListAppender<>();
        logAppender.start();
        pdpLogger = (Logger) LoggerFactory.getLogger(RemoteHttpPolicyDecisionPoint.class);
        pdpLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() throws IOException {
        pdpLogger.detachAppender(logAppender);
        logAppender.stop();
        server.shutdown();
    }

    private RemoteHttpPolicyDecisionPoint createPdpWithBasicAuth(String key, String secret) {
        val p = RemotePolicyDecisionPoint.builder().http().baseUrl(server.url("/").toString())
                .withHttpClient(HttpClient.create()).basicAuth(key, secret).build();
        p.setFirstBackoffMillis(50);
        p.setMaxBackOffMillis(100);
        p.setTimeoutMillis(2000);
        return p;
    }

    private RemoteHttpPolicyDecisionPoint createPdpWithApiKey(String apiKey) {
        val p = RemotePolicyDecisionPoint.builder().http().baseUrl(server.url("/").toString())
                .withHttpClient(HttpClient.create()).apiKey(apiKey).build();
        p.setFirstBackoffMillis(50);
        p.setMaxBackOffMillis(100);
        p.setTimeoutMillis(2000);
        return p;
    }

    private List<String> allLogMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Nested
    @DisplayName("Secret exclusion from logs")
    class SecretExclusion {

        @Test
        @Timeout(5)
        @DisplayName("Subscription containing secrets does not appear in log output (REQ-LOG-1/SECRETS-2)")
        void whenSubscriptionHasSecretsThenSecretsNotInLogs() {
            val pdp = createPdpWithBasicAuth("user", "pass");
            server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

            val subscription = AuthorizationSubscription.of(SECRET_VALUE, ACTION, RESOURCE);

            StepVerifier.create(pdp.decideOnce(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                    .verifyComplete();

            assertThat(allLogMessages()).noneMatch(msg -> msg.contains(SECRET_VALUE));
        }

        @Test
        @Timeout(5)
        @DisplayName("Auth credentials are not present in log output (REQ-LOG-2/AUTH-3)")
        void whenAuthConfiguredThenCredentialsNotInLogs() {
            val pdp = createPdpWithApiKey(AUTH_SECRET);
            server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decideOnce(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                    .verifyComplete();

            assertThat(allLogMessages()).noneMatch(msg -> msg.contains(AUTH_SECRET));
        }
    }

    @Nested
    @DisplayName("Error body truncation")
    class ErrorBodyTruncation {

        @Test
        @Timeout(5)
        @DisplayName("Long error response body does not appear in full in log output (REQ-LOG-3)")
        void whenPdpReturnsErrorThenResponseBodyTruncatedInLog() {
            val pdp      = createPdpWithBasicAuth("user", "pass");
            val longBody = "E".repeat(1000);
            server.enqueue(
                    new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).setBody(longBody));

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decideOnce(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                    .verifyComplete();

            assertThat(allLogMessages()).as("Full error body should not appear in log output")
                    .noneMatch(msg -> msg.contains(longBody));
        }
    }

    @Nested
    @DisplayName("Log level escalation")
    class LogLevelEscalation {

        @Test
        @Timeout(10)
        @DisplayName("Repeated stream failures escalate log level from WARN to ERROR (REQ-STREAM-4)")
        void whenRepeatedStreamFailuresThenLogEscalatesWarnToError() {
            val pdp = createPdpWithBasicAuth("user", "pass");
            pdp.setMaxRetries(6);

            for (var i = 0; i < 7; i++) {
                server.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            }

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.INDETERMINATE).verifyError();

            val reconnectLogs = logAppender.list.stream().filter(e -> e.getFormattedMessage().contains("reconnecting"))
                    .toList();

            val warnCount  = reconnectLogs.stream().filter(e -> e.getLevel() == Level.WARN).count();
            val errorCount = reconnectLogs.stream().filter(e -> e.getLevel() == Level.ERROR).count();

            assertThat(warnCount).as("First retries should be WARN").isGreaterThan(0);
            assertThat(errorCount).as("Later retries should escalate to ERROR").isGreaterThan(0);
        }

        @Test
        @Timeout(10)
        @DisplayName("401/403 auth errors are logged at ERROR on every occurrence (REQ-STREAM-5)")
        void whenAuthErrorThenLoggedAtErrorOnEveryOccurrence() {
            val pdp = createPdpWithBasicAuth("user", "pass");
            pdp.setMaxRetries(2);

            for (var i = 0; i < 3; i++) {
                server.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
            }

            val subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

            StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.INDETERMINATE).verifyError();

            val authErrorLogs = logAppender.list.stream().filter(e -> e.getLevel() == Level.ERROR)
                    .filter(e -> e.getFormattedMessage().contains("PDP authentication failed")).toList();

            assertThat(authErrorLogs).as("Auth errors should be logged at ERROR every time")
                    .hasSizeGreaterThanOrEqualTo(2);
        }
    }

}
