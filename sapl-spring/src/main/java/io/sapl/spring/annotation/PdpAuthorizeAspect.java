package io.sapl.spring.annotation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.spring.SAPLAuthorizator;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.action.SimpleAction;
import io.sapl.spring.marshall.resource.StringResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class PdpAuthorizeAspect {

	private static final String DEFAULT = "default";

	private final SAPLAuthorizator pep;

	@Autowired
	public PdpAuthorizeAspect(SAPLAuthorizator pep) {
		super();
		this.pep = pep;
	}

	@Around("@annotation(pdpAuthorize) && execution(* *(..))")
	public Object around(ProceedingJoinPoint pjp, PdpAuthorize pdpAuthorize) throws Throwable {
		SimpleAction action;
		Subject subject;
		StringResource resource;

		if ((DEFAULT).equals(pdpAuthorize.subject())) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			subject = new AuthenticationSubject(authentication);
		} else {
			Authentication authentication = new UsernamePasswordAuthenticationToken(pdpAuthorize.subject(),
					pdpAuthorize.subject());
			subject = new AuthenticationSubject(authentication);
		}

		if ((DEFAULT).equals(pdpAuthorize.action())) {
			action = new SimpleAction(pjp.getSignature().getName());
		} else {
			action = new SimpleAction(pdpAuthorize.action());
		}

		if ((DEFAULT).equals(pdpAuthorize.resource())) {
			resource = new StringResource(pjp.getTarget().getClass().getSimpleName());
		} else {
			resource = new StringResource(pdpAuthorize.resource());
		}

		LOGGER.debug("Getting pdp Response...");
		Response response = pep.getResponse(subject, action, resource);

		if (response.getDecision() == Decision.DENY) {
			LOGGER.debug("Access denied");
			throw new AccessDeniedException("Insufficient Permission");

		}

		LOGGER.debug("Access granted");
		return pjp.proceed();
	}

}
