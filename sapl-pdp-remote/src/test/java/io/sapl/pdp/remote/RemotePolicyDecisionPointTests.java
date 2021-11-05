/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

class RemotePolicyDecisionPointTests {

	private static final String ID = "id1";

	private static final String RESOURCE = "resource";

	private static final String ACTION = "action";

	private static final String SUBJECT = "subject";

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

	private MockWebServer server;

	private RemotePolicyDecisionPoint pdp;

	@BeforeAll
	static void setupLog() {
		// Route MockWebServer logs to shared logs
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		Hooks.onOperatorDebug();
	}

	@BeforeEach
	private void startServer() throws IOException {
		server = new MockWebServer();
		server.start();
		pdp = new RemotePolicyDecisionPoint(this.server.url("/").toString(), "secret", "key");
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

		var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);

		StepVerifier.create(pdp.decide(subscription))
				.expectNext(AuthorizationDecision.DENY, AuthorizationDecision.INDETERMINATE,
						AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE,
						AuthorizationDecision.NOT_APPLICABLE, AuthorizationDecision.INDETERMINATE)
				.thenCancel().verify();
	}

	@Test
	void whenSubscribingMultiDecideAll_thenGetResults() throws JsonProcessingException {
		var decision1 = new MultiAuthorizationDecision();
		decision1.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
		var decision2 = new MultiAuthorizationDecision();
		decision2.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.DENY);
		var indeterminate = MultiAuthorizationDecision.indeterminate();

		prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2, null });
		prepareDecisions(new MultiAuthorizationDecision[] { decision1, decision2 });

		var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
				RESOURCE);

		StepVerifier.create(pdp.decideAll(subscription))
				.expectNext(decision1, decision2, indeterminate, decision1, decision2).thenCancel().verify();
	}

	@Test
	void whenSubscribingMultiDecide_thenGetResults() throws JsonProcessingException {
		var decision1 = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.PERMIT);
		var decision2 = new IdentifiableAuthorizationDecision(ID, AuthorizationDecision.DENY);
		var indeterminate = IdentifiableAuthorizationDecision.INDETERMINATE;

		prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2, null });
		prepareDecisions(new IdentifiableAuthorizationDecision[] { decision1, decision2 });

		var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
				RESOURCE);

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
		var response = new MockResponse()
				.setHeader(HttpHeaders.CONTENT_TYPE.toString(),
						MediaType.TEXT_EVENT_STREAM_VALUE /* .APPLICATION_NDJSON_VALUE */)
				.setResponseCode(HttpStatus.OK.value()).setBody(body.toString());
		server.enqueue(response);
	}

	@Test
	void construct() {
		assertThat(new RemotePolicyDecisionPoint("http://localhost", "secret", "key"), notNullValue());
	}

	@Test
	void constructWithSslContext() throws SSLException {
		var sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
		assertThat(new RemotePolicyDecisionPoint("http://localhost", "secret", "key", sslContext), notNullValue());
	}

	@Test
	void settersAndGetters() {
		var pdp = new RemotePolicyDecisionPoint("http://localhost", "secret", "key");
		pdp.setBackoffFactor(999);
		pdp.setFirstBackoffMillis(998);
		pdp.setMaxBackOffMillis(1001);
		assertAll(() -> assertThat(pdp.getBackoffFactor(), is(999)),
				() -> assertThat(pdp.getFirstBackoffMillis(), is(998)),
				() -> assertThat(pdp.getMaxBackOffMillis(), is(1001)));

	}

}
