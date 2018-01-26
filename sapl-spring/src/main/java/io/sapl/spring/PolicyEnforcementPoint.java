package io.sapl.spring;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMethod;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Response;
import io.sapl.spring.marshall.Action;
import io.sapl.spring.marshall.Resource;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.action.HttpAction;
import io.sapl.spring.marshall.obligation.ObligationsHandlerService;
import io.sapl.spring.marshall.resource.HttpResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// @RequiredArgsConstructor
public class PolicyEnforcementPoint extends StandardSAPLAuthorizator {

	@Autowired
	public PolicyEnforcementPoint(PolicyDecisionPoint pdp, ObligationsHandlerService obs) {
		super(pdp, obs);
	}

	public boolean authorize(Object subject, Object action, Object resource) {
		LOGGER.trace(
				"Entering hasPermission (Object subject, Object action, Object resource) \n subject: {} \n action {} \n resource: {}",
				subject.getClass(), action.getClass(), resource.getClass());
		if (Authentication.class.isInstance(subject) && HttpServletRequest.class.isInstance(action)) {
			Authentication auth = (Authentication) subject;
			Subject authSubject = new AuthenticationSubject(auth);
			HttpServletRequest request = (HttpServletRequest) action;
			Action httpAction = new HttpAction(RequestMethod.valueOf(request.getMethod()));
			Resource httpResource = new HttpResource(request);
			return authorize(authSubject, httpAction, httpResource);
		}
		Response response = runPolicyCheck(subject, action, resource);
		return response.getDecision() == Decision.PERMIT;
	}

	public Response getResponse(Object subject, Object action, Object resource) {
		LOGGER.trace("Entering getResponse...");
		Response response = runPolicyCheck(subject, action, resource);
		return response;
	}

}
