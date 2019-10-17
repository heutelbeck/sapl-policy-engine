package io.sapl.pdp.server;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthDecision;
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
	 * Delegates to {@link PolicyDecisionPoint#decide(AuthSubscription)}.
	 * @param authSubscription the authorization subscription to be processed by the PDP.
	 * @return a flux emitting the current authorization decisions.
	 * @see PolicyDecisionPoint#decide(AuthSubscription)
	 */
	@PostMapping(value = "/decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<AuthDecision> decide(@RequestBody AuthSubscription authSubscription) {
		return pdp.decide(authSubscription).onErrorResume(error -> Flux.just(AuthDecision.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(MultiAuthSubscription)}.
	 * @param multiAuthSubscription the authorization multi-subscription to be processed
	 * by the PDP.
	 * @return a flux emitting authorization decisions related to the individual
	 * subscriptions contained in the given {@code multiAuthSubscription} as soon as they
	 * are available.
	 * @see PolicyDecisionPoint#decide(MultiAuthSubscription)
	 */
	@PostMapping(value = "/multi-decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<IdentifiableAuthDecision> decide(@RequestBody MultiAuthSubscription multiAuthSubscription) {
		return pdp.decide(multiAuthSubscription)
				.onErrorResume(error -> Flux.just(IdentifiableAuthDecision.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decideAll(MultiAuthSubscription)}.
	 * @param multiAuthSubscription the authorization multi-subscription to be processed
	 * by the PDP.
	 * @return a flux emitting multi-decisions containing authorization decisions for all
	 * the individual authorization subscriptions contained in the given
	 * {@code multiAuthSubscription}.
	 * @see PolicyDecisionPoint#decideAll(MultiAuthSubscription)
	 */
	@PostMapping(value = "/multi-decide-all", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<MultiAuthDecision> decideAll(@RequestBody MultiAuthSubscription multiAuthSubscription) {
		return pdp.decideAll(multiAuthSubscription)
				.onErrorResume(error -> Flux.just(MultiAuthDecision.indeterminate()));
	}

}
