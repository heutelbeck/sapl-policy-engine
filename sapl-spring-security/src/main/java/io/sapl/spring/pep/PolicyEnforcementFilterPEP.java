/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.pep;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
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

		var req = (HttpServletRequest) request;
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		var subscription = buildRequest(authentication, req, req);

		log.debug("PEP filter for: '{}' - {} Subscription: {}", subscription.getResource().get("requestedURI"),
				subscription.getResource().get("requestURL"), subscription);
		var authzDecision = pdp.decide(subscription).blockFirst();
		log.debug("PDP decision  : '{}' {}", authzDecision != null ? authzDecision.getDecision() : null, authzDecision);

		constraintHandlers.handleAdvices(authzDecision);
		constraintHandlers.handleObligations(authzDecision);

		if (authzDecision == null)
			throw new AccessDeniedException("PDP decision enpty.");

		if (authzDecision.getDecision() != Decision.PERMIT)
			throw new AccessDeniedException(String.format("PDP decision: %s", authzDecision.getDecision()));

		chain.doFilter(req, response);
	}

	private AuthorizationSubscription buildRequest(Object subject, Object action, Object resource) {
		return new AuthorizationSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), null);
	}

}
