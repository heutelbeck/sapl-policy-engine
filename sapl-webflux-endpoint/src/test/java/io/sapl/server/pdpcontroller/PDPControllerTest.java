/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.pdpcontroller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Import(PolicyDecisionPoint.class)
@WebFluxTest(controllers = PDPController.class)
@ContextConfiguration(classes = { PDPController.class })
class PDPControllerTest {

	@MockBean
	private PolicyDecisionPoint pdp;

	@Autowired
	private WebTestClient webClient;

	@Test
	void decideWithValidBody() {
		when(pdp.decide((AuthorizationSubscription) any(AuthorizationSubscription.class))).thenReturn(Flux
				.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

		var subscription = AuthorizationSubscription.of("subject", "action", "resource");

		var result = webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
				.returnResult(AuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.DENY,
				AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE).verifyComplete();

		verify(pdp, times(1)).decide(subscription);
	}

	@Test
	void decideOnceValidBody() {
		when(pdp.decide((AuthorizationSubscription) any(AuthorizationSubscription.class))).thenReturn(Flux
				.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

		var subscription = AuthorizationSubscription.of("subject", "action", "resource");

		var result = webClient.post().uri("/api/pdp/decide-once").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
				.returnResult(AuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.DENY).verifyComplete();

		verify(pdp, times(1)).decide(subscription);
	}

	@Test
	void decideWithValidProcessingError() {
		when(pdp.decide((AuthorizationSubscription) any(AuthorizationSubscription.class)))
				.thenReturn(Flux.error(new RuntimeException()));

		var subscription = AuthorizationSubscription.of("subject", "action", "resource");

		var result = webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
				.returnResult(AuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.INDETERMINATE).verifyComplete();

		verify(pdp, times(1)).decide(subscription);
	}

	@Test
	void decideOnceWithValidProcessingError() {
		when(pdp.decide((AuthorizationSubscription) any(AuthorizationSubscription.class)))
				.thenReturn(Flux.error(new RuntimeException()));

		var subscription = AuthorizationSubscription.of("subject", "action", "resource");

		var result = webClient.post().uri("/api/pdp/decide-once").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
				.returnResult(AuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.INDETERMINATE).verifyComplete();

		verify(pdp, times(1)).decide(subscription);
	}

	@Test
	void decideWithInvalidBody() {
		webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue("INVALID BODY")).exchange().expectStatus().isBadRequest();
	}

	@Test
	void subscribeToMultiDecisions() {
		when(pdp.decide((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class))).thenReturn(Flux
				.just(IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE,
						IdentifiableAuthorizationDecision.INDETERMINATE));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(IdentifiableAuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE,
				IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE)
				.verifyComplete();

		verify(pdp, times(1)).decide(multiAuthzSubscription);
	}

	@Test
	void subscribeToMultiDecisionsProcessingError() {
		when(pdp.decide((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class)))
				.thenReturn(Flux.error(new RuntimeException()));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(IdentifiableAuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE)
				.verifyComplete();

		verify(pdp, times(1)).decide(multiAuthzSubscription);
	}

	@Test
	void subscribeToMultiDecisionsInvalidBody() {
		var subscription = AuthorizationSubscription.of("subject", "action", "resource");
		webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isBadRequest()
				.returnResult(IdentifiableAuthorizationDecision.class);
	}

	@Test
	void subscribeToMultiAllDecisions() {
		when(pdp.decideAll((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class)))
				.thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
						MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(MultiAuthorizationDecision.class);

		StepVerifier
				.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate(),
						MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate())
				.verifyComplete();

		verify(pdp, times(1)).decideAll(multiAuthzSubscription);
	}

	@Test
	void oneMultiAllDecisions() {
		when(pdp.decideAll((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class)))
				.thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
						MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide-all-once").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(MultiAuthorizationDecision.class);

		StepVerifier
				.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
				.verifyComplete();

		verify(pdp, times(1)).decideAll(multiAuthzSubscription);
	}

	@Test
	void subscribeToMultiAllDecisionsProcessingError() {
		when(pdp.decideAll((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class)))
				.thenReturn(Flux.error(new RuntimeException()));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(MultiAuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
				.verifyComplete();

		verify(pdp, times(1)).decideAll(multiAuthzSubscription);
	}

	@Test
	void oneMultiAllDecisionsProcessingError() {
		when(pdp.decideAll((MultiAuthorizationSubscription) any(MultiAuthorizationSubscription.class)))
				.thenReturn(Flux.error(new RuntimeException()));

		var multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "subject", "action1", "resource")
				.addAuthorizationSubscription("id2", "subject", "action2", "other resource");

		var result = webClient.post().uri("/api/pdp/multi-decide-all-once").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
				.returnResult(MultiAuthorizationDecision.class);

		StepVerifier.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
				.verifyComplete();

		verify(pdp, times(1)).decideAll(multiAuthzSubscription);
	}

	@Test
	void subscribeToMultiDecisionsAllInvalidBody() {
		var subscription = AuthorizationSubscription.of("subject", "action", "resource");
		webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
				.body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isBadRequest()
				.returnResult(IdentifiableAuthorizationDecision.class);
	}

}
