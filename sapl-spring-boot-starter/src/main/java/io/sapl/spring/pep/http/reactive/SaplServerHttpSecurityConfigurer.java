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
package io.sapl.spring.pep.http.reactive;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.ExceptionHandlingSpec;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import lombok.val;

/**
 * Reactive companion to
 * {@link io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer}. Wires
 * the SAPL reactive HTTP enforcement chain into a {@link ServerHttpSecurity}
 * build.
 * <p>
 * The configurer pulls the {@link ReactiveSaplAuthorizationManager}, the
 * {@link SaplServerAccessDeniedHandler}, and the
 * {@link SaplHttpPepWebFilter} from the application context. It routes
 * {@code anyExchange()} through the SAPL authorization manager, installs
 * the SAPL access-denied handler on the exception-handling branch, and
 * adds the SAPL HTTP PEP web filter at Spring's authorization filter
 * position.
 * <p>
 * Two entry points:
 *
 * <pre>{@code
 * // defaults
 * SaplServerHttpSecurityConfigurer.apply(http, context);
 *
 * // customise per chain
 * SaplServerHttpSecurityConfigurer.apply(http, context,
 *         c -> c.subscriptionFactory((auth, exchange) -> Mono.just(AuthorizationSubscription.of(auth.getName(),
 *                 exchange.getRequest().getMethod().name(), exchange.getRequest().getURI().getPath(), mapper))));
 * }</pre>
 *
 * Use {@link #subscriptionFactory(ReactiveAuthorizationSubscriptionFactory)}
 * to replace only the subscription shape, or
 * {@link #authorizationManager(ReactiveSaplAuthorizationManager)} to
 * install a fully custom manager.
 */
public final class SaplServerHttpSecurityConfigurer {

    private final ApplicationContext context;

    private @Nullable ReactiveAuthorizationSubscriptionFactory subscriptionFactory;
    private @Nullable ReactiveSaplAuthorizationManager         authorizationManager;

    private SaplServerHttpSecurityConfigurer(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Convenience static factory that returns a configurer bound to the
     * given {@link ApplicationContext}.
     */
    public static SaplServerHttpSecurityConfigurer fromContext(ApplicationContext context) {
        return new SaplServerHttpSecurityConfigurer(context);
    }

    /**
     * Overrides the {@link ReactiveAuthorizationSubscriptionFactory} for
     * this filter chain. Ignored when an explicit
     * {@link #authorizationManager(ReactiveSaplAuthorizationManager)} is
     * also set.
     *
     * @param factory the factory to use for this chain.
     * @return this configurer for fluent chaining.
     */
    public SaplServerHttpSecurityConfigurer subscriptionFactory(ReactiveAuthorizationSubscriptionFactory factory) {
        this.subscriptionFactory = factory;
        return this;
    }

    /**
     * Replaces the {@link ReactiveSaplAuthorizationManager} for this filter
     * chain in its entirety. When set,
     * {@link #subscriptionFactory(ReactiveAuthorizationSubscriptionFactory)}
     * is ignored.
     *
     * @param manager the manager to use for this chain.
     * @return this configurer for fluent chaining.
     */
    public SaplServerHttpSecurityConfigurer authorizationManager(ReactiveSaplAuthorizationManager manager) {
        this.authorizationManager = manager;
        return this;
    }

    /**
     * Applies SAPL wiring to the given {@link ServerHttpSecurity}.
     */
    public ServerHttpSecurity applyTo(ServerHttpSecurity http) {
        val manager       = resolveManager();
        val deniedHandler = context.getBean(SaplServerAccessDeniedHandler.class);
        val webFilter     = context.getBean(SaplHttpPepWebFilter.class);
        http.authorizeExchange((AuthorizeExchangeSpec authorize) -> authorize.anyExchange().access(manager));
        http.exceptionHandling((ExceptionHandlingSpec exceptions) -> exceptions.accessDeniedHandler(deniedHandler));
        http.addFilterAt(webFilter, SecurityWebFiltersOrder.AUTHORIZATION);
        return http;
    }

    /**
     * Convenience that combines lookup and application with default
     * settings:
     * {@code SaplServerHttpSecurityConfigurer.apply(http, ctx)}.
     */
    public static ServerHttpSecurity apply(ServerHttpSecurity http, ApplicationContext context) {
        return fromContext(context).applyTo(http);
    }

    /**
     * Convenience that combines lookup, customisation, and application:
     * {@code SaplServerHttpSecurityConfigurer.apply(http, ctx, c -> c.subscriptionFactory(...))}.
     */
    public static ServerHttpSecurity apply(ServerHttpSecurity http, ApplicationContext context,
            Consumer<SaplServerHttpSecurityConfigurer> customizer) {
        val configurer = fromContext(context);
        customizer.accept(configurer);
        return configurer.applyTo(http);
    }

    private ReactiveSaplAuthorizationManager resolveManager() {
        if (authorizationManager != null) {
            return authorizationManager;
        }
        if (subscriptionFactory != null) {
            return new ReactiveSaplAuthorizationManager(context.getBean(PolicyDecisionPoint.class),
                    context.getBean(EnforcementPlanner.class), subscriptionFactory);
        }
        return context.getBean(ReactiveSaplAuthorizationManager.class);
    }
}
