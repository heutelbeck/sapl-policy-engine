package io.sapl.spring;

import java.io.Serializable;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.spring.marshall.Action;
import io.sapl.spring.marshall.Resource;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.action.HttpAction;
import io.sapl.spring.marshall.resource.HttpResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SAPLPermissionEvaluator implements PermissionEvaluator {

	private SAPLAuthorizator saplAuthorizer;

	@Autowired
	public SAPLPermissionEvaluator(SAPLAuthorizator saplAuthorizer) {
		this.saplAuthorizer = saplAuthorizer;
	}

	@Override
	public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
		return authorize(authentication, permission, targetDomainObject);

	}

	@Override
	public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
			Object permissionText) {
		return false;
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
			return saplAuthorizer.authorize(authSubject, httpAction, httpResource);
		}
		Response response = saplAuthorizer.runPolicyCheck(subject, action, resource, Optional.empty());
		return response.getDecision() == Decision.PERMIT;
	}

}
