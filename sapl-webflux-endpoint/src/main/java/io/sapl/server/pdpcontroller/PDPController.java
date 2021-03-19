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
package io.sapl.server.pdpcontroller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * REST controller providing endpoints for a policy decision point. The
 * endpoints can be connected using the client in the module
 * sapl-pdp-client.
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdp")
public class PDPController {

	private final PolicyDecisionPoint pdp;

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(AuthorizationSubscription)}.
	 * 
	 * @param authzSubscription the authorization subscription to be processed by
	 *                          the PDP.
	 * @return a flux emitting the current authorization decisions.
	 * @see PolicyDecisionPoint#decide(AuthorizationSubscription)
	 */
	@PostMapping(value = "/decide", produces = MediaType.APPLICATION_NDJSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public Flux<ServerSentEvent<AuthorizationDecision>> decide(
			@RequestBody AuthorizationSubscription authzSubscription) {
		return pdp.decide(authzSubscription).onErrorResume(error -> Flux.just(AuthorizationDecision.INDETERMINATE))
				.map(decision -> ServerSentEvent.<AuthorizationDecision>builder().data(decision).build());
	}

	/**
	 * Delegates to
	 * {@link PolicyDecisionPoint#decide(MultiAuthorizationSubscription)}.
	 * 
	 * @param multiAuthzSubscription the authorization multi-subscription to be
	 *                               processed by the PDP.
	 * @return a flux emitting authorization decisions related to the individual
	 *         subscriptions contained in the given {@code multiAuthzSubscription}
	 *         as soon as they are available.
	 * @see PolicyDecisionPoint#decide(MultiAuthorizationSubscription)
	 */
	@PostMapping(value = "/multi-decide", produces = MediaType.APPLICATION_NDJSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public Flux<ServerSentEvent<IdentifiableAuthorizationDecision>> decide(
			@RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
		return pdp.decide(multiAuthzSubscription)
				.onErrorResume(error -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE))
				.map(decision -> ServerSentEvent.<IdentifiableAuthorizationDecision>builder().data(decision).build());
	}

	/**
	 * Delegates to
	 * {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
	 * 
	 * @param multiAuthzSubscription the authorization multi-subscription to be
	 *                               processed by the PDP.
	 * @return a flux emitting multi-decisions containing authorization decisions
	 *         for all the individual authorization subscriptions contained in the
	 *         given {@code multiAuthzSubscription}.
	 * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
	 */
	@PostMapping(value = "/multi-decide-all", produces = MediaType.APPLICATION_NDJSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public Flux<ServerSentEvent<MultiAuthorizationDecision>> decideAll(
			@RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
		return pdp.decideAll(multiAuthzSubscription)
				.onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate()))
				.map(decision -> ServerSentEvent.<MultiAuthorizationDecision>builder().data(decision).build());
	}

}
