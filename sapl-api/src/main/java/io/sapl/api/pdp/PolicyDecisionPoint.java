/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.api.pdp;

import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
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
	 * @param authzSubscription the SAPL authorization subscription object
	 * @return a {@link Flux} emitting the authorization decisions for the given
	 * authorization subscription. New authorization decisions are only added to the
	 * stream if they are different from the preceding authorization decision.
	 */
	Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription);

	/**
	 * Multi-subscription variant of {@link #decide(AuthorizationSubscription)}.
	 * @param multiAuthzSubscription the multi-subscription object containing the
	 * subjects, actions, resources and environments of the authorization subscriptions to
	 * be evaluated by the PDP.
	 * @return a {@link Flux} emitting authorization decisions for the given authorization
	 * subscriptions as soon as they are available. Related authorization decisions and
	 * authorization subscriptions have the same id.
	 */
	Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription);

	/**
	 * Multi-subscription variant of {@link #decide(AuthorizationSubscription)}.
	 * @param multiAuthzSubscription the multi-subscription object containing the
	 * subjects, actions, resources and environments of the authorization subscriptions to
	 * be evaluated by the PDP.
	 * @return a {@link Flux} emitting authorization decisions for the given authorization
	 * subscriptions as soon as at least one authorization decision for each authorization
	 * subscription is available.
	 */
	Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription);

}
