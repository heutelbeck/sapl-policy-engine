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

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.ExceptionHandlingSpec;

import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * Reactive companion to
 * {@link io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer}. Wires
 * the SAPL reactive HTTP enforcement chain into a {@link ServerHttpSecurity}
 * build. Apply with
 * {@code http.with(SaplServerHttpSecurityConfigurer.saplServerHttp(), Customizer.withDefaults())}.
 * <p>
 * The configurer pulls the {@link ReactiveSaplAuthorizationManager}, the
 * {@link SaplServerAccessDeniedHandler}, and the
 * {@link SaplHttpPepWebFilter} from the application context. It routes
 * {@code anyExchange()} through the SAPL authorization manager, installs
 * the SAPL access-denied handler on the exception-handling branch, and
 * adds the SAPL HTTP PEP web filter immediately after Spring's
 * authorization filter position.
 * <p>
 * Unlike servlet's {@code AbstractHttpConfigurer}, reactive
 * {@link ServerHttpSecurity} does not expose a generic configurer
 * extension point of the same shape. The recommended idiom is therefore a
 * Spring bean that returns a {@code Customizer<ServerHttpSecurity>}; the
 * static factory below produces such a customiser, and the application
 * applies it via {@code http.apply(saplServerHttp(context))} or
 * {@code saplServerHttp(context).accept(http)}.
 */
@RequiredArgsConstructor
public final class SaplServerHttpSecurityConfigurer {

    private final ReactiveSaplAuthorizationManager manager;
    private final SaplServerAccessDeniedHandler    deniedHandler;
    private final SaplHttpPepWebFilter             webFilter;

    /**
     * Convenience static factory that pulls all three SAPL beans from the
     * given {@link ApplicationContext}.
     */
    public static SaplServerHttpSecurityConfigurer fromContext(ApplicationContext context) {
        return new SaplServerHttpSecurityConfigurer(context.getBean(ReactiveSaplAuthorizationManager.class),
                context.getBean(SaplServerAccessDeniedHandler.class), context.getBean(SaplHttpPepWebFilter.class));
    }

    /**
     * Applies SAPL wiring to the given {@link ServerHttpSecurity}. Use as
     * {@code SaplServerHttpSecurityConfigurer.fromContext(ctx).applyTo(http)}.
     */
    public ServerHttpSecurity applyTo(ServerHttpSecurity http) {
        http.authorizeExchange((AuthorizeExchangeSpec authorize) -> authorize.anyExchange().access(manager));
        http.exceptionHandling((ExceptionHandlingSpec exceptions) -> exceptions.accessDeniedHandler(deniedHandler));
        http.addFilterAt(webFilter, SecurityWebFiltersOrder.AUTHORIZATION);
        return http;
    }

    /**
     * Convenience that combines lookup and application:
     * {@code SaplServerHttpSecurityConfigurer.apply(http, ctx)}.
     */
    public static ServerHttpSecurity apply(ServerHttpSecurity http, ApplicationContext context) {
        val configurer = fromContext(context);
        return configurer.applyTo(http);
    }
}
