package io.sapl.spring;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * This service can be used to establish a policy enforcement point at any location in
 * users code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEnforcementPoint {

	private static final AuthDecision DENY = AuthDecision.DENY;

	private final PolicyDecisionPoint pdp;

	private final ConstraintHandlerService constraintHandlers;

	private final ObjectMapper mapper;

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks the PDP
	 * for a decision. In case of {@link Decision#PERMIT permit}, obligation and advice
	 * handlers are invoked. Emits {@link Decision#PERMIT permit} only if all obligations
	 * could be fulfilled and no resource value was provided by the PDP's decision. Emits
	 * {@link Decision#DENY deny} otherwise. Decisions are only emitted if they are
	 * different from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 * {@link Decision#PERMIT permit} and all obligations could be fulfilled, and the
	 * PDP's authorization decision did not contain a resource value, {@link Decision#DENY
	 * deny} otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource, Object environment) {
		return execute(subject, action, resource, environment, false).map(AuthDecision::getDecision);

	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks the PDP
	 * for a decision. In case of {@link Decision#PERMIT permit}, obligation and advice
	 * handlers are invoked. Emits {@link Decision#PERMIT permit} only if all obligations
	 * could be fulfilled and no resource value was provided by the PDP's decision. Emits
	 * {@link Decision#DENY deny} otherwise. Decisions are only emitted if they are
	 * different from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting {@link Decision#PERMIT permit}, if the PDP returned
	 * {@link Decision#PERMIT permit} and all obligations could be fulfilled, and the
	 * PDP's authorization decision did not contain a resource value, {@link Decision#DENY
	 * deny} otherwise.
	 */
	public Flux<Decision> enforce(Object subject, Object action, Object resource) {
		return enforce(subject, action, resource, null);
	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks the PDP
	 * for a decision. In case of {@link Decision#PERMIT permit}, obligation and advice
	 * handlers are invoked. If all obligations can be fulfilled, the original
	 * authorization decision emitted by the PDP is passed through. Emits an
	 * {@link AuthDecision authorization decision} containing {@link Decision#DENY deny}
	 * and no resource otherwise. Authorization decisions are only emitted if they are
	 * different from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return a Flux emitting the original decision of the PDP, if the PDP returned a
	 * decision containing {@link Decision#PERMIT permit} and all obligations could be
	 * fulfilled, an {@link AuthDecision authorization decision} containing
	 * {@link Decision#DENY deny} and no resource otherwise.
	 */
	public Flux<AuthDecision> filterEnforce(Object subject, Object action, Object resource, Object environment) {
		return execute(subject, action, resource, environment, true);
	}

	private Flux<AuthDecision> execute(Object subject, Object action, Object resource, Object environment,
			boolean supportResourceTransformation) {
		AuthSubscription authSubscription = buildRequest(subject, action, resource, environment);
		final Flux<AuthDecision> decisionFlux = pdp.decide(authSubscription);
		return decisionFlux.map(authDecision -> {
			LOGGER.debug("SUBSCRIPTION  : ACTION={} RESOURCE={} SUBJ={} ENV={}", authSubscription.getAction(),
					authSubscription.getResource(), authSubscription.getSubject(), authSubscription.getEnvironment());
			LOGGER.debug("AUTH_DECISION : {} - {}", authDecision == null ? "null" : authDecision.getDecision(),
					authDecision);

			if (authDecision == null || authDecision.getDecision() != Decision.PERMIT) {
				return DENY;
			}
			if (!supportResourceTransformation && authDecision.getResource().isPresent()) {
				LOGGER.debug("PDP returned a new resource value. "
						+ "This PEP cannot handle resource replacement. Thus, deny access.");
				return DENY;
			}
			try {
				constraintHandlers.handleObligations(authDecision);
				constraintHandlers.handleAdvices(authDecision);
				return authDecision;
			}
			catch (AccessDeniedException e) {
				LOGGER.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
						e.getLocalizedMessage());
				return DENY;
			}
		}).distinctUntilChanged();
	}

	/**
	 * Creates a SAPL authorization subscription based on its parameters and asks the PDP
	 * for a decision. In case of {@link Decision#PERMIT permit}, obligation and advice
	 * handlers are invoked. If all obligations can be fulfilled, the original
	 * authorization decision emitted by the PDP is passed through. Emits an
	 * {@link AuthDecision authorization decision} containing {@link Decision#DENY deny}
	 * and no resource otherwise. Authorization decisions are only emitted if they are
	 * different from the preceding one.
	 * @param subject the subject, will be serialized into JSON.
	 * @param action the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return a Flux emitting the original decision of the PDP, if the PDP returned a
	 * decision containing {@link Decision#PERMIT permit} and all obligations could be
	 * fulfilled, an {@link AuthDecision authorization decision} containing
	 * {@link Decision#DENY deny} and no resource otherwise.
	 */
	public Flux<AuthDecision> filterEnforce(Object subject, Object action, Object resource) {
		return filterEnforce(subject, action, resource, null);
	}

	/**
	 * Sends the given {@code multiAuthSubscription} to the PDP which emits
	 * {@link IdentifiableAuthDecision identifiable authorization decisions} as soon as
	 * they are available. Each authorization decision is handled as follows: If its
	 * decision is {@link Decision#PERMIT permit}, obligation and advice handlers are
	 * invoked. If all obligations can be fulfilled, the original authorization decision
	 * is left as is. If its decision is not {@link Decision#PERMIT permit} or if not all
	 * obligations can be fulfilled, the authorization decision is replaced by an
	 * authorization decision containing {@link Decision#DENY deny} and no resource.
	 * @param multiAuthSubscription the multi-subscription to be sent to the PDP.
	 * @return a Flux emitting {@link IdentifiableAuthDecision identifiable authorization
	 * decisions} which may differ from the original ones emitted by the PDP after having
	 * handled the obligations.
	 */
	public Flux<IdentifiableAuthDecision> filterEnforce(MultiAuthSubscription multiAuthSubscription) {
		final Flux<IdentifiableAuthDecision> identifiableAuthDecisionFluxFlux = pdp.decide(multiAuthSubscription);
		return identifiableAuthDecisionFluxFlux.map(iad -> {
			LOGGER.debug("SUBSCRIPTION      : {}",
					multiAuthSubscription.getAuthSubscriptionWithId(iad.getAuthSubscriptionId()));
			LOGGER.debug("ORIGINAL DECISION : {}", iad.getAuthDecision());
			return handle(iad);
		});
	}

	/**
	 * Sends the given {@code multiAuthSubscription} to the PDP which emits related
	 * {@link MultiAuthDecision multi-decisions}. Each authorization decision in the
	 * multi-decision is handled as follows: If its decision is {@link Decision#PERMIT
	 * permit}, obligation and advice handlers are invoked. If all obligations can be
	 * fulfilled, the original authorization decision is left as is. If its decision is
	 * not {@link Decision#PERMIT permit} or if not all obligations can be fulfilled, the
	 * authorization decision is replaced by an authorization decision containing
	 * {@link Decision#DENY deny} and no resource. {@link MultiAuthDecision}s are only
	 * emitted if they are different from the preceding one.
	 * @param multiAuthSubscription the multi-subscription to be sent to the PDP.
	 * @return a Flux emitting {@link MultiAuthDecision multi-decisions} which may differ
	 * from the original ones emitted by the PDP after having handled the obligations.
	 */
	public Flux<MultiAuthDecision> filterEnforceAll(MultiAuthSubscription multiAuthSubscription) {
		final Flux<MultiAuthDecision> multiDecisionFlux = pdp.decideAll(multiAuthSubscription);
		return multiDecisionFlux.map(multiDecision -> {
			LOGGER.debug("SUBSCRIPTION      : {}", multiAuthSubscription);
			LOGGER.debug("ORIGINAL DECISION : {}", multiDecision);

			final MultiAuthDecision resultMultiDecision = new MultiAuthDecision();
			for (IdentifiableAuthDecision identifiableAuthDecision : multiDecision) {
				final IdentifiableAuthDecision handledDecision = handle(identifiableAuthDecision);
				resultMultiDecision.setAuthDecisionForSubscriptionWithId(handledDecision.getAuthSubscriptionId(),
						handledDecision.getAuthDecision());
			}

			LOGGER.debug("RETURNED DECISION : {}", resultMultiDecision);
			return resultMultiDecision;
		}).distinctUntilChanged();
	}

	private AuthSubscription buildRequest(Object subject, Object action, Object resource, Object environment) {
		final JsonNode subjectNode = mapper.valueToTree(subject);
		final JsonNode actionNode = mapper.valueToTree(action);
		final JsonNode resourceNode = mapper.valueToTree(resource);
		final JsonNode environmentNode = mapper.valueToTree(environment);
		return new AuthSubscription(subjectNode, actionNode, resourceNode, environmentNode);
	}

	private IdentifiableAuthDecision handle(IdentifiableAuthDecision identifiableAuthDecision) {
		final String subscriptionId = identifiableAuthDecision.getAuthSubscriptionId();
		final AuthDecision authDecision = identifiableAuthDecision.getAuthDecision();
		if (authDecision == null || authDecision.getDecision() != Decision.PERMIT) {
			return new IdentifiableAuthDecision(subscriptionId, DENY);
		}
		try {
			constraintHandlers.handleObligations(authDecision);
			constraintHandlers.handleAdvices(authDecision);
			return identifiableAuthDecision;
		}
		catch (AccessDeniedException e) {
			LOGGER.debug("PEP failed to fulfill PDP obligations ({}). Access denied by policy enforcement point.",
					e.getLocalizedMessage());
			return new IdentifiableAuthDecision(subscriptionId, DENY);
		}
	}

}
