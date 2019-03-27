package io.sapl.spring.method.post;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdvice;
import lombok.extern.slf4j.Slf4j;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PolicyBasedPostInvocationEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice
		implements PostInvocationEnforcementAdvice {

	public PolicyBasedPostInvocationEnforcementAdvice(PolicyDecisionPoint pdp,
			ConstraintHandlerService constraintHandlers, ObjectMapper mapper) {
		super(pdp, constraintHandlers, mapper);
	}

	@Override
	public Object after(Authentication authentication, MethodInvocation mi,
			PolicyBasedPostInvocationEnforcementAttribute pia, Object returnedObject) {
		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, mi);

		Object subject = retrieveSubjet(authentication, pia, ctx);
		Object action = retrieveAction(mi, pia, ctx);
		Object resource = retrieveResource(mi, pia, ctx);
		Object environment = retrieveEnvironment(pia, ctx);

		Request request = new Request(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));

		Response response = pdp.decide(request).block();

		LOGGER.debug("ATTRIBUTE: {} - {}", pia, pia.getClass());
		LOGGER.debug("REQUEST  :\n - ACTION={}\n - RESOURCE={}\n - SUBJ={}\n - ENV={}", request.getAction(),
				request.getResource(), request.getSubject(), request.getEnvironment());
		LOGGER.debug("RESPONSE : {} - {}", response.getDecision(), response);

		if (!response.getDecision().equals(Decision.PERMIT)) {
			throw new AccessDeniedException("Access denied by policy decision point.");
		}

		constraintHandlers.handleObligations(response);
		constraintHandlers.handleAdvices(response);

		if (response.getResource().isPresent()) {
			return response.getResource().get();
		} else {
			return returnedObject;
		}

	}

}