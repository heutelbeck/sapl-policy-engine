package io.sapl.spring.method.pre;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
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
 * Method pre-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PolicyBasedPreInvocationEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice
		implements PreInvocationEnforcementAdvice {

	public PolicyBasedPreInvocationEnforcementAdvice(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintHandlerService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory);
	}

	@Override
	public boolean before(Authentication authentication, MethodInvocation mi,
			PolicyBasedPreInvocationEnforcementAttribute attr) {
		// Lazy loading to decouple infrastructure initialization from domain
		// initialization. Else, beans may become non eligible for BeanPostProcessors
		lazyLoadDepdendencies();

		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, mi);

		Object subject = retrieveSubjet(authentication, attr, ctx);
		Object action = retrieveAction(mi, attr, ctx);
		Object resource = retrieveResource(mi, attr, ctx);
		Object environment = retrieveEnvironment(attr, ctx);

		Request request = new Request(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));

		Response response = pdp.decide(request).blockFirst();

		LOGGER.debug("REQUEST  : ACTION={} RESOURCE={} SUBJ={} ENV={}", request.getAction(), request.getResource(),
				request.getSubject(), request.getEnvironment());
		LOGGER.debug("RESPONSE : {} - {}", response == null ? "null" : response.getDecision(), response);

		if (response != null && response.getResource().isPresent()) {
			LOGGER.warn("Cannot handle a response declaring a new resource in @PreEnforce. Deny access!");
			return false;
		}
		if (response == null || response.getDecision() != Decision.PERMIT) {
			return false;
		}

		try {
			constraintHandlers.handleObligations(response);
		} catch (AccessDeniedException e) {
			return false;
		}
		constraintHandlers.handleAdvices(response);
		return true;
	}
}
