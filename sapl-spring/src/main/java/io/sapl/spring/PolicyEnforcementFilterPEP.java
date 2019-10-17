package io.sapl.spring;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.AuthDecision;
import io.sapl.spring.constraints.ConstraintHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PolicyEnforcementFilterPEP extends GenericFilterBean {

	private final PolicyDecisionPoint pdp;

	private final ConstraintHandlerService constraintHandlers;

	private final ObjectMapper mapper;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		AuthDecision authDecision = pdp.decide(buildRequest(authentication, req, req)).blockFirst();

		LOGGER.trace("PDP decision: {}", authDecision);

		if (authDecision == null || authDecision.getDecision() != Decision.PERMIT) {
			LOGGER.trace("User was not authorized for this action. Decision was: {}",
					authDecision == null ? "null" : authDecision.getDecision());
			throw new AccessDeniedException("Current User may not perform this action.");
		}
		constraintHandlers.handleObligations(authDecision);
		constraintHandlers.handleAdvices(authDecision);
		chain.doFilter(req, response);
	}

	private AuthSubscription buildRequest(Object subject, Object action, Object resource) {
		return new AuthSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), null);
	}

}
