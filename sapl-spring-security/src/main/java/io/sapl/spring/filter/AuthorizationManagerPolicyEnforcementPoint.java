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

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import io.sapl.spring.pep.PolicyEnforcementPoint;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Policy Enforcement Point to authorize requests in the reactive Spring
 * Security web filter chain.
 * <p>
 * This
 * {@link org.springframework.security.authorization.ReactiveAuthorizationManager}
 * can be applied to the reactive Spring Security web filter chain as follows:
 *
 * <pre>
 * {@code
 *  @Bean
 * 	public SecurityWebFilterChain configureChain(ServerHttpSecurity http, ReactiveAuthorizationManager<AuthorizationContext> pepAuthorizationManager) {
 *		return http.authorizeExchange().anyExchange().access(pepAuthorizationManager).and().build();
 *	}
 * }
 * </pre>
 *
 * The {@link #check check} method is then called by the Spring Security
 * framework whenever a request needs to be authorized.
 *
 * @param <T> {@link org.springframework.security.web.server.authorization.AuthorizationContext}
 */
@RequiredArgsConstructor
public class AuthorizationManagerPolicyEnforcementPoint<T extends AuthorizationContext>
		implements ReactiveAuthorizationManager<T> {

	private final AuthorizationSubscriptionBuilderService subscriptionBuilder;
	private final PolicyEnforcementPoint pep;

	/**
	 * Determines if access is granted for a specific authentication and context
	 * <p>
	 * The incoming authentication is mapped to the decision of a Policy Decision
	 * Point (PDP). <br>
	 * The PDP returns its decision as a Flux which may change over time, but the
	 * reactive Spring Security web filter framework only accepts a Mono. <br>
	 * Consequently, only the PDP's first decision is used, meaning the request is
	 * only authorized according to the status of the authentication and context at
	 * this moment in time.
	 * 
	 * @param authentication the Authentication to check
	 * @param context        the context to check
	 * @return a decision
	 */
	@Override
	public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, T context) {
		return subscriptionBuilder.reactiveConstructAuthorizationSubscription(authentication, context)
				.flatMap(pep::isPermitted).map(AuthorizationDecision::new);
	}
}