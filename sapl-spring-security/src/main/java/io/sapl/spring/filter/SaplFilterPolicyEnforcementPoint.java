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
package io.sapl.spring.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SaplFilterPolicyEnforcementPoint extends GenericFilterBean {

	private final PolicyDecisionPoint pdp;

	private final ConstraintEnforcementService constraintEnforcementService;

	private final ObjectMapper mapper;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		var authentication = SecurityContextHolder.getContext().getAuthentication();
		var subscription = AuthorizationSubscription.of(authentication, request, request, mapper);
		var authzDecision = pdp.decide(subscription).blockFirst();

		log.trace("Filter: contextPath='{}' decision='{}' subscription='{}' fullDecision='{}'",
				request.getServletContext().getContextPath(),
				authzDecision != null ? authzDecision.getDecision() : null, subscription, authzDecision);

		if (authzDecision == null)
			throw new AccessDeniedException("No decision from PDP.");

		if (authzDecision.getResource().isPresent())
			throw new AccessDeniedException("PDP requested resource replacement. This is not possible in the filter chain.");

		try {
			constraintEnforcementService.bundleFor(authzDecision, Object.class).wrap(Flux.empty()).blockLast();
		}
		catch (AccessDeniedException e) {
			throw new AccessDeniedException("Not all obligations could be handled.", e);
		}

		if (authzDecision.getDecision() != Decision.PERMIT)
			throw new AccessDeniedException("Access denied by PDP.");

		chain.doFilter(request, response);
	}

}
