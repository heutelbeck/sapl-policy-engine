package io.sapl.spring.annotation;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.spring.StandardSAPLAuthorizator;
import io.sapl.spring.marshall.Action;
import io.sapl.spring.marshall.Resource;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.action.HttpAction;
import io.sapl.spring.marshall.resource.HttpResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class PdpAuthorizeHttpAspect {

	private final StandardSAPLAuthorizator pep;

	@Autowired
	public PdpAuthorizeHttpAspect(StandardSAPLAuthorizator pep) {
		this.pep = pep;
	}

	@Around("@annotation(pdpAuthorizeHttp) && execution(* *(..)) && args(request,..)")
	public Object around(ProceedingJoinPoint pjp, PdpAuthorizeHttp pdpAuthorizeHttp, HttpServletRequest request)
			throws Throwable {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		Subject subject = new AuthenticationSubject(authentication);
		Action httpAction = new HttpAction(RequestMethod.valueOf(request.getMethod()));
		Resource httpResource = new HttpResource(request);

		LOGGER.debug("Getting pdp Response...");
		Response response = pep.getResponse(subject, httpAction, httpResource);

		if (response.getDecision() == Decision.DENY) {
			LOGGER.debug("Access denied");
			throw new AccessDeniedException("Insufficient Permission");

		}

		LOGGER.debug("Access granted");
		return pjp.proceed();
	}

}
