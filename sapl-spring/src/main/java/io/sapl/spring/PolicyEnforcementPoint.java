package io.sapl.spring;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * This service can be used to establish a policy enforcement point at any
 * location in users code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEnforcementPoint {
	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;

	/**
	 * This method will create a Request based on its parameters and will ask the
	 * PDP for a decision. In case of permit, obligation and advice handlers will be
	 * invoked. Will only return true if obligations are fulfilled and no resource
	 * value is provided by the PDPs decision.
	 *
	 * @param subject     the subject, will be serialized into JSON.
	 * @param action      the action, will be serialized into JSON.
	 * @param resource    the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return true, if the PDP returns permit, all obligations can be fulfilled,
	 *         and the response did not contain a resource value.
	 */
	public boolean enforce(Object subject, Object action, Object resource, Object environment) {
		Request request = new Request(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
		Response response = pdp.decide(request).blockFirst();
		LOGGER.debug("REQUEST  : ACTION={} RESOURCE={} SUBJ={} ENV={}", request.getAction(), request.getResource(),
				request.getSubject(), request.getEnvironment());
		LOGGER.debug("RESPONSE : {} - {}", response == null ? "null" : response.getDecision(), response);

		if (response == null || response.getDecision() != Decision.PERMIT) {
			return false;
		}

		if (!constraintHandlers.obligationHandlersForObligationsAvailable(response)) {
			LOGGER.debug("PEP cannot fulfill PDP obligations. Deny access.");
			return false;
		}

		if (response.getResource().isPresent()) {
			LOGGER.debug(
					"PDP returned a new resource value. This PEP cannot handle resource replacement. Thus, deny access.");
			return false;
		}
		try {
			constraintHandlers.handleObligations(response);
		} catch (AccessDeniedException e) {
			LOGGER.debug("PEP failed to fulfill PDP obligations. Deny access. {}", e.getLocalizedMessage());
			return false;
		}
		constraintHandlers.handleAdvices(response);
		return true;
	}

	/**
	 * This method will create a Request based on its parameters and will ask the
	 * PDP for a decision. In case of permit, obligation and advice handlers will be
	 * invoked. Will only return true if obligations are fulfilled and no resource
	 * value is provided by the PDPs decision.
	 *
	 * @param subject  the subject, will be serialized into JSON.
	 * @param action   the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return true, if the PDP returns permit, all obligations can be fulfilled,
	 *         and the response did not contain a resource value.
	 */
	public boolean enforce(Object subject, Object action, Object resource) {
		return enforce(subject, action, resource, null);
	}

	/**
	 * This method will create a Request based on its parameters and will ask the
	 * PDP for a decision. In case of permit, obligation and advice handlers will be
	 * invoked. If the decision is not permit or obligations cannot be fulfilled, an
	 * Access Denied Exception will be thrown. Otherwise the resource of the
	 * decision will be returned.
	 *
	 * @param subject     the subject, will be serialized into JSON.
	 * @param action      the action, will be serialized into JSON.
	 * @param resource    the resource, will be serialized into JSON.
	 * @param environment the environment, will be serialized into JSON.
	 * @return the resource of the PDPs decision.
	 */
	public Optional<JsonNode> filterEnforce(Object subject, Object action, Object resource, Object environment) {
		Request request = new Request(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
		Response response = pdp.decide(request).blockFirst();
		LOGGER.debug("REQUEST  : ACTION={} RESOURCE={} SUBJ={} ENV={}", request.getAction(), request.getResource(),
				request.getSubject(), request.getEnvironment());
		LOGGER.debug("RESPONSE : {} - {}", response == null ? "null" : response.getDecision(), response);

		if (response == null || response.getDecision() != Decision.PERMIT) {
			throw new AccessDeniedException("Access not permitted by policy enforcement point.");
		}

		if (!constraintHandlers.obligationHandlersForObligationsAvailable(response)) {
			throw new AccessDeniedException(
					"Obligations cannot be fulfilled. No handler available. Access not permitted by policy enforcement point.");
		}

		constraintHandlers.handleObligations(response);
		constraintHandlers.handleAdvices(response);

		return response.getResource();
	}

	/**
	 * This method will create a Request based on its parameters and will ask the
	 * PDP for a decision. In case of permit, obligation and advice handlers will be
	 * invoked. If the decision is not permit or obligations cannot be fulfilled, an
	 * Access Denied Exception will be thrown. Otherwise the resource of the
	 * decision will be returned.
	 *
	 * @param subject  the subject, will be serialized into JSON.
	 * @param action   the action, will be serialized into JSON.
	 * @param resource the resource, will be serialized into JSON.
	 * @return the resource of the PDPs decision.
	 */
	public Optional<JsonNode> filterEnforce(Object subject, Object action, Object resource) {
		return filterEnforce(subject, action, resource, null);
	}
}
