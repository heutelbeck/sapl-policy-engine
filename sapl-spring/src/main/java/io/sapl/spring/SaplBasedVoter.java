package io.sapl.spring;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.spring.marshall.action.HttpAction;
import io.sapl.spring.marshall.resource.HttpResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SaplBasedVoter implements AccessDecisionVoter<Object> {

	private final StandardSAPLAuthorizator pep;

	private static final String LOGGER_FORMAT = "Decision from SAPLVoter is : {}";

	@Override
	public boolean supports(ConfigAttribute attribute) {
		// we wanna vote on every attribute
		// maybe this has to be limited in the future
		return true;
	}

	@Override
	public boolean supports(Class<?> arg0) {
		// we wanna vote on every secured object
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
		Response decision = pep.getResponse(new AuthenticationSubject(authentication), new HttpAction(request),
				new HttpResource(request));

		if (decision == null) {
			throw new IllegalArgumentException();
		}
		return mapDecisionToVoteResponse(decision.getDecision());
	}

	private int mapDecisionToVoteResponse(Decision decision) {
		int returnValue;
		switch (decision) {
		case PERMIT:
			LOGGER.info(LOGGER_FORMAT, "ACCESS_GRANTED");
			returnValue = ACCESS_GRANTED;
			break;
		case DENY:
			LOGGER.info(LOGGER_FORMAT, "ACCESS_DENIED");
			returnValue = ACCESS_DENIED;
			break;
		case INDETERMINATE:
		case NOT_APPLICABLE:
			LOGGER.info(LOGGER_FORMAT, "ACCESS_ABSTAIN");
			returnValue = ACCESS_ABSTAIN;
			break;
		default:
			returnValue = ACCESS_GRANTED;
			break;
		}
		return returnValue;
	}
}
