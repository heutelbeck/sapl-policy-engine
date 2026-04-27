/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pep.http.servlet;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import lombok.val;

/**
 * Spring Security configurer that wires the SAPL servlet HTTP enforcement
 * chain into an {@link HttpSecurity} build. Apply with
 * {@code http.with(SaplHttpSecurityConfigurer.saplHttp(), Customizer.withDefaults())}.
 * <p>
 * The configurer pulls the {@link SaplAuthorizationManager}, the
 * {@link SaplAccessDeniedHandler}, and the {@link SaplHttpPepFilter} from the
 * application context and:
 * <ul>
 * <li>routes {@code anyRequest()} through the SAPL authorization manager;</li>
 * <li>installs the SAPL access-denied handler on the exception-handling
 * branch;</li>
 * <li>adds the SAPL HTTP PEP filter immediately after Spring Security's
 * {@link AuthorizationFilter}.</li>
 * </ul>
 * Other {@code HttpSecurity} customisations (CSRF, login flavours, request
 * matchers beyond {@code anyRequest()}) remain the caller's responsibility.
 * <p>
 * Customisation hooks (use the customizer parameter of
 * {@link HttpSecurity#with}):
 *
 * <pre>{@code
 * http.with(saplHttp(), c -> c.subscriptionFactory(
 *         (auth, req) -> AuthorizationSubscription.of(auth.getName(), req.getMethod(), req.getRequestURI(), mapper)));
 * }</pre>
 *
 * Use {@link #subscriptionFactory(AuthorizationSubscriptionFactory)} to
 * replace only the subscription shape, or
 * {@link #authorizationManager(SaplAuthorizationManager)} to install a
 * fully custom manager (e.g. one that pre-resolves attributes).
 */
public final class SaplHttpSecurityConfigurer extends AbstractHttpConfigurer<SaplHttpSecurityConfigurer, HttpSecurity> {

    private @Nullable AuthorizationSubscriptionFactory subscriptionFactory;
    private @Nullable SaplAuthorizationManager         authorizationManager;

    /**
     * Returns a fresh configurer instance for use with
     * {@link HttpSecurity#with(org.springframework.security.config.annotation.SecurityConfigurerAdapter, org.springframework.security.config.Customizer)}.
     */
    public static SaplHttpSecurityConfigurer saplHttp() {
        return new SaplHttpSecurityConfigurer();
    }

    /**
     * Overrides the {@link AuthorizationSubscriptionFactory} used by this
     * filter chain. Ignored when an explicit
     * {@link #authorizationManager(SaplAuthorizationManager)} is also set.
     *
     * @param factory the factory to use for this chain.
     * @return this configurer for fluent chaining.
     */
    public SaplHttpSecurityConfigurer subscriptionFactory(AuthorizationSubscriptionFactory factory) {
        this.subscriptionFactory = factory;
        return this;
    }

    /**
     * Replaces the {@link SaplAuthorizationManager} for this filter chain in
     * its entirety. When set, {@link #subscriptionFactory} is ignored.
     *
     * @param manager the manager to use for this chain.
     * @return this configurer for fluent chaining.
     */
    public SaplHttpSecurityConfigurer authorizationManager(SaplAuthorizationManager manager) {
        this.authorizationManager = manager;
        return this;
    }

    @Override
    public void init(HttpSecurity http) {
        val context = http.getSharedObject(ApplicationContext.class);
        val manager = resolveManager(context);
        val denied  = context.getBean(SaplAccessDeniedHandler.class);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(manager))
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(denied));
    }

    @Override
    public void configure(HttpSecurity http) {
        val pep = http.getSharedObject(ApplicationContext.class).getBean(SaplHttpPepFilter.class);
        http.addFilterAfter(pep, AuthorizationFilter.class);
    }

    private SaplAuthorizationManager resolveManager(ApplicationContext context) {
        if (authorizationManager != null) {
            return authorizationManager;
        }
        if (subscriptionFactory != null) {
            return new SaplAuthorizationManager(context.getBean(PolicyDecisionPoint.class),
                    context.getBean(EnforcementPlanner.class), subscriptionFactory);
        }
        return context.getBean(SaplAuthorizationManager.class);
    }
}
