/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pep;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This service can be used to establish a policy enforcement point at any
 * location in users code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEnforcementPoint {

	private static final AuthorizationDecision DENY = AuthorizationDecision.DENY;

	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks
	 * the PDP for a decision. In case of {@link Decision#PERMIT permit}, obligation
	 * and advice handlers are invoked. Emits {@link Decision#PERMIT permit} only if
	 * all obligations could be fulfilled and no resource value was provided by the
	 * PDP's decision. Emits {@link Decision#DENY deny} otherwise. Decisions are
	 * only emitted if they are different from the preceding one.
	 * 
	 * @param subject     the subject, will be serialized into JSON.
	 * @param action      the action, will be serialized into JSON.
	 * @param resource    the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 *         {@link Decision#PERMIT permit} and all obligations could be
	 *         fulfilled, and the PDP's authorization decision did not contain a
	 *         resource value, {@link Decision#DENY deny} otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource, Object environment) {
		return execute(subject, action, resource, environment, false).map(AuthorizationDecision::getDecision);

	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks
	 * the PDP for a decision. In case of {@link Decision#PERMIT permit}, obligation
	 * and advice handlers are invoked. Emits {@link Decision#PERMIT permit} only if
	 * all obligations could be fulfilled and no resource value was provided by the
	 * PDP's decision. Emits {@link Decision#DENY deny} otherwise. Decisions are
	 * only emitted if they are different from the preceding one.
	 * 
	 * @param subject  the subject, will be serialized into JSON.
	 * @param action   the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 *         {@link Decision#PERMIT permit} and all obligations could be
	 *         fulfilled, and the PDP's authorization decision did not contain a
	 *         resource value, {@link Decision#DENY deny} otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource) {
		return enforce(subject, action, resource, null);
	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks
	 * the PDP for a decision. In case of {@link Decision#PERMIT permit}, obligation
	 * and advice handlers are invoked. If all obligations can be fulfilled, the
	 * original authorization decision emitted by the PDP is passed through. Emits
	 * an {@link AuthorizationDecision authorization decision} containing
	 * {@link Decision#DENY deny} and no resource otherwise. Authorization decisions
	 * are only emitted if they are different from the preceding one.
	 * 
	 * @param subject     the subject, will be serialized into JSON.
	 * @param action      the action, will be serialized into JSON.
	 * @param resource    the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting the original decision of the PDP, if the PDP returned
	 *         a decision containing {@link Decision#PERMIT permit} and all
	 *         obligations could be fulfilled, an {@link AuthorizationDecision
	 *         authorization decision} containing {@link Decision#DENY deny} and no
	 *         resource otherwise.
	 */
	public Flux<AuthorizationDecision> filterEnforce(Object subject, Object action, Object resource,
			Object environment) {
		return execute(subject, action, resource, environment, true);
	}

	private Flux<AuthorizationDecision> execute(Object subject, Object action, Object resource, Object environment,
			boolean supportResourceTransformation) {
		AuthorizationSubscription authzSubscription = buildRequest(subject, action, resource, environment);
		final Flux<AuthorizationDecision> decisionFlux = pdp.decide(authzSubscription);
		return decisionFlux.map(authzDecision -> {
			log.debug("SUBSCRIPTION   : ACTION={} RESOURCE={} SUBJ={} ENV={}", authzSubscription.getAction(),
					authzSubscription.getResource(), authzSubscription.getSubject(),
					authzSubscription.getEnvironment());
			log.debug("AUTHZ_DECISION : {} - {}", authzDecision == null ? "null" : authzDecision.getDecision(),
					authzDecision);

			if (authzDecision == null || authzDecision.getDecision() != Decision.PERMIT) {
				return DENY;
			}
			if (!supportResourceTransformation && authzDecision.getResource().isPresent()) {
				log.debug("PDP returned a new resource value. "
						+ "This PEP cannot handle resource replacement. Thus, deny access.");
				return DENY;
			}
			try {
				constraintHandlers.handleObligations(authzDecision);
				constraintHandlers.handleAdvices(authzDecision);
				return authzDecision;
			} catch (AccessDeniedException e) {
				log.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
						e.getLocalizedMessage());
				return DENY;
			}
		}).distinctUntilChanged();
	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks
	 * the PDP for a decision. In case of {@link Decision#PERMIT permit}, obligation
	 * and advice handlers are invoked. If all obligations can be fulfilled, the
	 * original authorization decision emitted by the PDP is passed through. Emits
	 * an {@link AuthorizationDecision authorization decision} containing
	 * {@link Decision#DENY deny} and no resource otherwise. Authorization decisions
	 * are only emitted if they are different from the preceding one.
	 * 
	 * @param subject  the subject, will be serialized into JSON.
	 * @param action   the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting the original decision of the PDP, if the PDP returned
	 *         a decision containing {@link Decision#PERMIT permit} and all
	 *         obligations could be fulfilled, an {@link AuthorizationDecision
	 *         authorization decision} containing {@link Decision#DENY deny} and no
	 *         resource otherwise.
	 */
	public Flux<AuthorizationDecision> filterEnforce(Object subject, Object action, Object resource) {
		return filterEnforce(subject, action, resource, null);
	}

	/**
	 * Sends the given {@code multiAuthzSubscription} to the PDP which emits
	 * {@link IdentifiableAuthorizationDecision identifiable authorization
	 * decisions} as soon as they are available. Each authorization decision is
	 * handled as follows: If its decision is {@link Decision#PERMIT permit},
	 * obligation and advice handlers are invoked. If all obligations can be
	 * fulfilled, the original authorization decision is left as is. If its decision
	 * is not {@link Decision#PERMIT permit} or if not all obligations can be
	 * fulfilled, the authorization decision is replaced by an authorization
	 * decision containing {@link Decision#DENY deny} and no resource.
	 * 
	 * @param multiAuthzSubscription the multi-subscription to be sent to the PDP.
	 * @return a Flux emitting {@link IdentifiableAuthorizationDecision identifiable
	 *         authorization decisions} which may differ from the original ones
	 *         emitted by the PDP after having handled the obligations.
	 */
	public Flux<IdentifiableAuthorizationDecision> filterEnforce(
			MultiAuthorizationSubscription multiAuthzSubscription) {
		final Flux<IdentifiableAuthorizationDecision> identifiableAuthzDecisionFlux = pdp
				.decide(multiAuthzSubscription);
		return identifiableAuthzDecisionFlux.map(iad -> {
			log.debug("SUBSCRIPTION      : {}",
					multiAuthzSubscription.getAuthorizationSubscriptionWithId(iad.getAuthorizationSubscriptionId()));
			log.debug("ORIGINAL DECISION : {}", iad.getAuthorizationDecision());
			return handle(iad);
		});
	}

	/**
	 * Sends the given {@code multiAuthzSubscription} to the PDP which emits related
	 * {@link MultiAuthorizationDecision multi-decisions}. Each authorization
	 * decision in the multi-decision is handled as follows: If its decision is
	 * {@link Decision#PERMIT permit}, obligation and advice handlers are invoked.
	 * If all obligations can be fulfilled, the original authorization decision is
	 * left as is. If its decision is not {@link Decision#PERMIT permit} or if not
	 * all obligations can be fulfilled, the authorization decision is replaced by
	 * an authorization decision containing {@link Decision#DENY deny} and no
	 * resource. {@link MultiAuthorizationDecision}s are only emitted if they are
	 * different from the preceding one.
	 * 
	 * @param multiAuthzSubscription the multi-subscription to be sent to the PDP.
	 * @return a Flux emitting {@link MultiAuthorizationDecision multi-decisions}
	 *         which may differ from the original ones emitted by the PDP after
	 *         having handled the obligations.
	 */
	public Flux<MultiAuthorizationDecision> filterEnforceAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		final Flux<MultiAuthorizationDecision> multiDecisionFlux = pdp.decideAll(multiAuthzSubscription);
		return multiDecisionFlux.map(multiDecision -> {
			log.debug("SUBSCRIPTION      : {}", multiAuthzSubscription);
			log.debug("ORIGINAL DECISION : {}", multiDecision);

			final MultiAuthorizationDecision resultMultiDecision = new MultiAuthorizationDecision();
			for (IdentifiableAuthorizationDecision identifiableAuthzDecision : multiDecision) {
				final IdentifiableAuthorizationDecision handledDecision = handle(identifiableAuthzDecision);
				resultMultiDecision.setAuthorizationDecisionForSubscriptionWithId(
						handledDecision.getAuthorizationSubscriptionId(), handledDecision.getAuthorizationDecision());
			}

			log.debug("RETURNED DECISION : {}", resultMultiDecision);
			return resultMultiDecision;
		}).distinctUntilChanged();
	}

	private AuthorizationSubscription buildRequest(Object subject, Object action, Object resource, Object environment) {
		final JsonNode subjectNode = mapper.valueToTree(subject);
		final JsonNode actionNode = mapper.valueToTree(action);
		final JsonNode resourceNode = mapper.valueToTree(resource);
		final JsonNode environmentNode = mapper.valueToTree(environment);
		return new AuthorizationSubscription(subjectNode, actionNode, resourceNode, environmentNode);
	}

	private IdentifiableAuthorizationDecision handle(IdentifiableAuthorizationDecision identifiableAuthzDecision) {
		final String subscriptionId = identifiableAuthzDecision.getAuthorizationSubscriptionId();
		final AuthorizationDecision authzDecision = identifiableAuthzDecision.getAuthorizationDecision();
		if (authzDecision == null || authzDecision.getDecision() != Decision.PERMIT) {
			return new IdentifiableAuthorizationDecision(subscriptionId, DENY);
		}
		try {
			constraintHandlers.handleObligations(authzDecision);
			constraintHandlers.handleAdvices(authzDecision);
			return identifiableAuthzDecision;
		} catch (AccessDeniedException e) {
			log.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
					e.getLocalizedMessage());
			return new IdentifiableAuthorizationDecision(subscriptionId, DENY);
		}
	}

}
