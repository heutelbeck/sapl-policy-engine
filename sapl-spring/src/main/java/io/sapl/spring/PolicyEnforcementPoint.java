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
		LOGGER.debug("REQUEST  : ACTION={} RESOURCE={} SUBJ={} ENV={}", request.getAction(), request.getResource(), request.getSubject(), request.getEnvironment());
		LOGGER.debug("RESPONSE : {} - {}",response.getDecision(),response);

		if (response.getDecision() != Decision.PERMIT) {
			return false;
		}
		if (!constraintHandlers.obligationHandlersForObligationsAvailable(response)) {
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
