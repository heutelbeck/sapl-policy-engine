package io.sapl.spring.method.post;

import java.util.Optional;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintHandlerService;
import io.sapl.spring.method.AbstractPolicyBasedInvocationEnforcementAdvice;
import lombok.extern.slf4j.Slf4j;

/**
 * Method post-invocation handling based on a SAPL policy decision point.
 */
@Slf4j
public class PolicyBasedPostInvocationEnforcementAdvice extends AbstractPolicyBasedInvocationEnforcementAdvice
		implements PostInvocationEnforcementAdvice {

	public PolicyBasedPostInvocationEnforcementAdvice(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintHandlerService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory);
	}

	@Override
	@SuppressWarnings("unchecked") // is actually checked
	public Object after(Authentication authentication, MethodInvocation mi,
			PolicyBasedPostInvocationEnforcementAttribute pia, Object returnedObject) {
		// Lazy loading to decouple infrastructure initialization from domain
		// initialization.
		// Else, beans may become not eligible for BeanPostProcessors
		lazyLoadDepdendencies();
		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, mi);

		boolean returnOptional = false;
		Class<?> returnType;
		if (returnedObject instanceof Optional) {
			expressionHandler.setReturnObject(((Optional<Object>) returnedObject).get(), ctx);
			returnType = ((Optional<Object>) returnedObject).get().getClass();
			returnOptional = true;
		}
		else {
			returnType = mi.getMethod().getReturnType();
			expressionHandler.setReturnObject(returnedObject, ctx);
		}

		Object subject = retrieveSubjet(authentication, pia, ctx);
		Object action = retrieveAction(mi, pia, ctx);
		Object resource = retrieveResource(mi, pia, ctx);
		Object environment = retrieveEnvironment(pia, ctx);

		AuthSubscription authSubscription = new AuthSubscription(mapper.valueToTree(subject),
				mapper.valueToTree(action), mapper.valueToTree(resource), mapper.valueToTree(environment));

		AuthDecision authDecision = pdp.decide(authSubscription).blockFirst();

		LOGGER.debug("ATTRIBUTE: {} - {}", pia, pia.getClass());
		LOGGER.debug("SUBSCRIPTION  :\n - ACTION={}\n - RESOURCE={}\n - SUBJ={}\n - ENV={}",
				authSubscription.getAction(), authSubscription.getResource(), authSubscription.getSubject(),
				authSubscription.getEnvironment());
		LOGGER.debug("AUTH_DECISION : {} - {}", authDecision == null ? "null" : authDecision.getDecision(),
				authDecision);

		if (authDecision == null || authDecision.getDecision() != Decision.PERMIT) {
			throw new AccessDeniedException("Access denied by policy decision point.");
		}

		constraintHandlers.handleObligations(authDecision);
		constraintHandlers.handleAdvices(authDecision);

		if (authDecision.getResource().isPresent()) {
			try {
				Object returnValue = mapper.treeToValue(authDecision.getResource().get(), returnType);
				if (returnOptional) {
					return Optional.of(returnValue);
				}
				else {
					return returnValue;
				}
			}
			catch (JsonProcessingException e) {
				LOGGER.trace("Transformed result cannot be mapped to expected return type. {}",
						authDecision.getResource().get());
				throw new AccessDeniedException(
						"Returned resource of authDecision cannot be mapped back to return value. Access not permitted by policy enforcement point.",
						e);
			}
		}
		else {
			return returnedObject;
		}

	}

}
