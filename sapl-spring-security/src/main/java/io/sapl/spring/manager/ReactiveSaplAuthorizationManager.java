/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.spring.manager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
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
 * &#64;Bean
 * public SecurityWebFilterChain configureChain(ServerHttpSecurity http,
 *         ReactiveAuthorizationManager<AuthorizationContext> pepAuthorizationManager) {
 *     return http.authorizeExchange().anyExchange().access(pepAuthorizationManager).and().build();
 * }
 * }
 * </pre>
 *
 * The {@link #check check} method is then called by the Spring Security
 * framework whenever a request needs to be authorized.
 */
@RequiredArgsConstructor
public class ReactiveSaplAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {
    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final PolicyDecisionPoint          pdp;
    private final ConstraintEnforcementService constraintEnforcementService;
    private final ObjectMapper                 mapper;

    /**
     * Determines if access is granted for a specific authentication and context
     * <p>
     * The incoming authentication is mapped to the decision of a Policy Decision
     * Point (PDP). <br>
     * The PDP returns its decision as a Flux which may change over time, but the
     * reactive Spring Security web filter framework only accepts a Mono. <br>
     * Consequently, only the first PDP decision is used, meaning the request is
     * only authorized according to the status of the authentication and context at
     * this moment in time.
     *
     * @param authentication the Authentication to check
     * @param context the context to check
     * @return a decision
     */
    @Override
    @SuppressWarnings("deprecation") // Must implement as interface still refers to deprecated method from authorize
    public Mono<org.springframework.security.authorization.AuthorizationDecision> check(
            Mono<Authentication> authentication, AuthorizationContext context) {
        return reactiveConstructAuthorizationSubscription(authentication, context).flatMap(this::isPermitted)
                .map(org.springframework.security.authorization.AuthorizationDecision::new);
    }

    private Mono<Boolean> isPermitted(AuthorizationSubscription authzSubscription) {
        return pdp.decide(authzSubscription).next().defaultIfEmpty(AuthorizationDecision.DENY)
                .map(this::enforceDecision);
    }

    private boolean enforceDecision(AuthorizationDecision authzDecision) {
        if (authzDecision.getResource().isPresent())
            return false;

        try {
            constraintEnforcementService.accessManagerBundleFor(authzDecision).handleOnDecisionConstraints();
        } catch (AccessDeniedException e) {
            return false;
        }

        return authzDecision.getDecision() == Decision.PERMIT;
    }

    private Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(
            Mono<Authentication> authentication, AuthorizationContext context) {
        final var request = context.getExchange().getRequest();
        return authentication.defaultIfEmpty(ANONYMOUS)
                .map(authn -> AuthorizationSubscription.of(authn, request, request, mapper));
    }

}
