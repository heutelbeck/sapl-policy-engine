package io.sapl.spring.annotation;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.pep.SAPLAuthorizer;
import io.sapl.spring.marshall.subject.AuthenticationSubject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
public class PdpAuthorizeAspect {

	private static final String DEFAULT = "default";

	private boolean tokenStoreInitialized;

	private final SAPLAuthorizer sapl;

	@Autowired
	private ApplicationContext applicationContext;

	private TokenStore tokenStore;

	public PdpAuthorizeAspect(SAPLAuthorizer sapl) {
		super();
		this.sapl = sapl;
	}

	@Around("@annotation(pdpAuthorize) && execution(* *(..))")
	public Object around(ProceedingJoinPoint pjp, PdpAuthorize pdpAuthorize) throws Throwable {
		LOGGER.debug("Annotated method: {} in class: {} called. Constructing SAPL request...",
				pjp.getSignature().getName(), pjp.getTarget().getClass().getSimpleName());

		if (!tokenStoreInitialized) {
			initializeTokenStore();
		}

		Object subject = pdpAuthorizeRetrieveSubject(pdpAuthorize);
		Object action = pdpAuthorizeRetrieveAction(pdpAuthorize, pjp);
		Object resource = pdpAuthorizeRetrieveResource(pdpAuthorize, pjp);

		Response response = sapl.getResponse(subject, action, resource).blockFirst();

		if (response.getDecision() == Decision.DENY) {
			LOGGER.debug("Access denied");
			throw new AccessDeniedException("Insufficient Permission");
		}

		LOGGER.debug("Access granted");
		return pjp.proceed();
	}

	private Object pdpAuthorizeRetrieveSubject(PdpAuthorize pdpAuthorize) {
		Object subject;

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<String> token = retrieveJwtTokenFrom(authentication);

		if (!DEFAULT.equals(pdpAuthorize.subject())) {
			LOGGER.debug("Using subject from manual input");
			subject = pdpAuthorize.subject();

		} else if (tokenStore != null && token.isPresent()) {
			LOGGER.debug("Using subject from JWT");
			OAuth2AccessToken parsedToken = tokenStore.readAccessToken(token.get());
			Map<String, Object> claims = parsedToken.getAdditionalInformation();
			for (Entry<String, Object> kvp : claims.entrySet()) {
				LOGGER.debug("Single claim: {} -> {}", kvp.getKey(), kvp.getValue());
			}
			LOGGER.debug("Retrieved token: {}", token);
			subject = new AuthenticationSubject(authentication, claims);

		} else {
			LOGGER.debug("Using default subject");
			subject = new AuthenticationSubject(authentication);
		}

		return subject;
	}

	private static Optional<String> retrieveJwtTokenFrom(Authentication authentication) {
        final JsonNode details = new ObjectMapper().convertValue(authentication.getDetails(), JsonNode.class);
        final String token = details.findValue("tokenValue") != null ? details.findValue("tokenValue").textValue() : null;
        return Optional.ofNullable(token);
    }

	private Object pdpAuthorizeRetrieveAction(PdpAuthorize pdpAuthorize, ProceedingJoinPoint pjp) {
		Object action;

        final Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();

        if (!DEFAULT.equals(pdpAuthorize.action())) {
			LOGGER.debug("Using action from manual input");
			action = pdpAuthorize.action();

		} else if (httpServletRequest.isPresent()) {
			LOGGER.debug("Using action from HttpServletRequest");
			action = httpServletRequest.get();

		} else {
			LOGGER.debug("Using default action");
			action = pjp.getSignature().getName();
		}
		return action;
	}

	private Object pdpAuthorizeRetrieveResource(PdpAuthorize pdpAuthorize, ProceedingJoinPoint pjp) {
        Object resource;

        final Optional<HttpServletRequest> httpServletRequest = retrieveRequestObject();

        if (!DEFAULT.equals(pdpAuthorize.resource())) {
			LOGGER.debug("Using resource from manual input");
			resource = pdpAuthorize.resource();

		} else if (httpServletRequest.isPresent()) {
			LOGGER.debug("Using resource from HttpServletRequest");
			resource = httpServletRequest.get();

		} else {
			LOGGER.debug("Using default resource");
			resource = pjp.getTarget().getClass().getSimpleName();
		}
		return resource;
	}

	private static Optional<HttpServletRequest> retrieveRequestObject() {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        final HttpServletRequest httpRequest = requestAttributes != null
                ? ((ServletRequestAttributes) requestAttributes).getRequest()
                : null;
        return Optional.ofNullable(httpRequest);
    }

	private void initializeTokenStore() {
		try {
			this.tokenStore = applicationContext.getBean(TokenStore.class);
		} catch (NoSuchBeanDefinitionException e) {
			// No Such Bean
		}
		tokenStoreInitialized = true;
	}
}
