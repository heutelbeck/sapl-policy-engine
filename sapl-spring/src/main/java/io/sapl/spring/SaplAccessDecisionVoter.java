package io.sapl.spring;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaplAccessDecisionVoter implements AccessDecisionVoter<Object> {

	private static final String LOGGER_FORMAT = "Decision from SAPLVoter is : {}";

	private final PolicyDecisionPoint pdp;
	private final ConstraintHandlerService constraintHandlers;
	private final ObjectMapper mapper;

	@Override
	public boolean supports(ConfigAttribute attribute) {
		// we want to vote on every attribute
		// maybe this has to be limited in the future
		return true;
	}

	@Override
	public boolean supports(Class<?> arg0) {
		// we want to vote on every secured object
		// maybe this has to be limited in the future
		return true;
	}

	@Override
	public int vote(Authentication authentication, Object object, Collection<ConfigAttribute> arg2) {
		FilterInvocation invocation = (FilterInvocation) object;
		HttpServletRequest request = invocation.getRequest();
		for (ConfigAttribute configAttribute : arg2) {
			LOGGER.info(configAttribute.toString());
		}

		Response decision = pdp.decide(buildRequest(authentication, request, request)).block();

		LOGGER.trace("PDP decision: {}", decision);

		try {
			constraintHandlers.handleObligations(decision);
		} catch (AccessDeniedException e) {
			LOGGER.trace("Access denied in filter due to obligation failure");
			return ACCESS_DENIED;
		}
		constraintHandlers.handleAdvices(decision);

		return mapDecisionToVoteResponse(decision.getDecision());
	}

	private Request buildRequest(Object subject, Object action, Object resource) {
		return new Request(mapper.valueToTree(subject), mapper.valueToTree(action), mapper.valueToTree(resource), null);
	}

	private int mapDecisionToVoteResponse(Decision decision) {
		int returnValue;
		switch (decision) {
		case PERMIT:
			LOGGER.trace(LOGGER_FORMAT, "ACCESS_GRANTED");
			returnValue = ACCESS_GRANTED;
			break;
		case DENY:
			LOGGER.trace(LOGGER_FORMAT, "ACCESS_DENIED");
			returnValue = ACCESS_DENIED;
			break;
		case INDETERMINATE:
		case NOT_APPLICABLE:
			LOGGER.trace(LOGGER_FORMAT, "ACCESS_ABSTAIN");
			returnValue = ACCESS_ABSTAIN;
			break;
		default:
			returnValue = ACCESS_GRANTED;
			break;
		}
		return returnValue;
	}
}
