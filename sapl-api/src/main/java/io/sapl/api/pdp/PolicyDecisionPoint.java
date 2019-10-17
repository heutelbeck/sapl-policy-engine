package io.sapl.api.pdp;

import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthDecision;
import reactor.core.publisher.Flux;

/**
 * The policy decision point is the component in the system, which will take an
 * authorization subscription, retrieve matching policies from the policy retrieval point,
 * evaluate the policies, while potentially consulting external resources (e.g., through
 * attribute finders), and return a {@link Flux} of authorization decision objects.
 *
 * This interface offers a number of convenience methods to hand over an authorization
 * subscription to the policy decision point, which only differ in the construction of the
 * underlying authorization subscription object.
 */
public interface PolicyDecisionPoint {

	/**
	 * Takes an authorization subscription object and returns a {@link Flux} emitting
	 * matching authorization decisions.
	 * @param authSubscription the SAPL authorization subscription object
	 * @return a {@link Flux} emitting the authorization decisions for the given
	 * authorization subscription. New authorization decisions are only added to the
	 * stream if they are different from the preceding authorization decision.
	 */
	Flux<AuthDecision> decide(AuthSubscription authSubscription);

	/**
	 * Multi-subscription variant of {@link #decide(AuthSubscription)}.
	 * @param multiAuthSubscription the multi-subscription object containing the subjects,
	 * actions, resources and environments of the authorization subscriptions to be
	 * evaluated by the PDP.
	 * @return a {@link Flux} emitting authorization decisions for the given authorization
	 * subscriptions as soon as they are available. Related authorization decisions and
	 * authorization subscriptions have the same id.
	 */
	Flux<IdentifiableAuthDecision> decide(MultiAuthSubscription multiAuthSubscription);

	/**
	 * Multi-subscription variant of {@link #decide(AuthSubscription)}.
	 * @param multiAuthSubscription the multi-subscription object containing the subjects,
	 * actions, resources and environments of the authorization subscriptions to be
	 * evaluated by the PDP.
	 * @return a {@link Flux} emitting authorization decisions for the given authorization
	 * subscriptions as soon as at least one authorization decision for each authorization
	 * subscription is available.
	 */
	Flux<MultiAuthDecision> decideAll(MultiAuthSubscription multiAuthSubscription);

}
