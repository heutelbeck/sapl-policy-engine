package io.sapl.spring;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEnforcementPoint {
	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;

	public boolean enforce(Object subject, Object action, Object resource, Object environment) {
		Request request = new Request(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
		Response response = pdp.decide(request).block();
		if (response.getDecision() != Decision.PERMIT) {
			LOGGER.trace("Access not permitted by policy decision point. Decision was: {}", response.getDecision());
			return false;
		}
		if (!constraintHandlers.obligationHandlersForObligationsAvailable(response)) {
			LOGGER.trace("Access not permitted by policy enforcement point. Obligations cannot be fulfilled.");
			return false;
		}
		constraintHandlers.handleObligations(response);
		constraintHandlers.handleAdvices(response);
		return true;
	}

	public boolean enforce(Object subject, Object action, Object resource) {
		return enforce(subject, action, resource, null);
	}
}
