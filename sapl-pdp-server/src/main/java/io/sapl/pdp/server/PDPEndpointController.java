/**
 * Copyright © 2017 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.pdp.server;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * REST controller providing endpoints for a policy decision point. The endpoints can be
 * connected using the client {@link io.sapl.pdp.remote.RemotePolicyDecisionPoint} in the
 * module sapl-pdp-client.
 */
@RestController
@RequestMapping("/api/pdp")
@RequiredArgsConstructor
public class PDPEndpointController {

	private final PolicyDecisionPoint pdp;

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(AuthorizationSubscription)}.
	 * @param authzSubscription the authorization subscription to be processed by the PDP.
	 * @return a flux emitting the current authorization decisions.
	 * @see PolicyDecisionPoint#decide(AuthorizationSubscription)
	 */
	@PostMapping(value = "/decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<AuthorizationDecision> decide(@RequestBody AuthorizationSubscription authzSubscription) {
		return pdp.decide(authzSubscription).onErrorResume(error -> Flux.just(AuthorizationDecision.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(MultiAuthorizationSubscription)}.
	 * @param multiAuthzSubscription the authorization multi-subscription to be processed
	 * by the PDP.
	 * @return a flux emitting authorization decisions related to the individual
	 * subscriptions contained in the given {@code multiAuthzSubscription} as soon as they
	 * are available.
	 * @see PolicyDecisionPoint#decide(MultiAuthorizationSubscription)
	 */
	@PostMapping(value = "/multi-decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<IdentifiableAuthorizationDecision> decide(
			@RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
		return pdp.decide(multiAuthzSubscription)
				.onErrorResume(error -> Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)}.
	 * @param multiAuthzSubscription the authorization multi-subscription to be processed
	 * by the PDP.
	 * @return a flux emitting multi-decisions containing authorization decisions for all
	 * the individual authorization subscriptions contained in the given
	 * {@code multiAuthzSubscription}.
	 * @see PolicyDecisionPoint#decideAll(MultiAuthorizationSubscription)
	 */
	@PostMapping(value = "/multi-decide-all", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<MultiAuthorizationDecision> decideAll(
			@RequestBody MultiAuthorizationSubscription multiAuthzSubscription) {
		return pdp.decideAll(multiAuthzSubscription)
				.onErrorResume(error -> Flux.just(MultiAuthorizationDecision.indeterminate()));
	}

}
