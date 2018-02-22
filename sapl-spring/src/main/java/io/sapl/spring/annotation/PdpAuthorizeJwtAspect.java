package io.sapl.spring.annotation;

import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.spring.SAPLAuthorizator;
import io.sapl.spring.marshall.Subject;
import io.sapl.spring.marshall.action.HttpAction;
import io.sapl.spring.marshall.resource.HttpResource;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class PdpAuthorizeJwtAspect {

	@Autowired
	private SAPLAuthorizator pep;

	@Autowired
	private TokenStore tokenStore;

	@Around("@annotation(pdpAuthorizeJwt) && execution(* *(..))")
	public Object around(ProceedingJoinPoint pjp, PdpAuthorizeJwt pdpAuthorizeJwt) throws Throwable {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		JsonNode details = new ObjectMapper().convertValue(authentication.getDetails(), JsonNode.class);
		String token = details.findValue("tokenValue").textValue();
		OAuth2AccessToken parsedToken = tokenStore.readAccessToken(token);
		Map<String, Object> claims = parsedToken.getAdditionalInformation();
		for (Entry<String, Object> kvp : claims.entrySet()) {
			LOGGER.debug("Single claim: {} -> {}", kvp.getKey(), kvp.getValue());
		}
		LOGGER.debug("Retrieved token: {}", token);
		Subject subject = new AuthenticationSubject(authentication, claims);

		HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

		HttpAction action = new HttpAction(req);

		HttpResource resource = new HttpResource(req);
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
