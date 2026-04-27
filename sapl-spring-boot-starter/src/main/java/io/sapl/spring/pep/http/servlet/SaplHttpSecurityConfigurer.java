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

import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import io.sapl.spring.pep.http.servlet.SaplAccessDeniedHandler;
import io.sapl.spring.pep.http.servlet.SaplAuthorizationManager;
import io.sapl.spring.pep.http.servlet.SaplHttpPepFilter;
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
 */
public final class SaplHttpSecurityConfigurer extends AbstractHttpConfigurer<SaplHttpSecurityConfigurer, HttpSecurity> {

    /**
     * Returns a fresh configurer instance for use with
     * {@link HttpSecurity#with(org.springframework.security.config.annotation.SecurityConfigurerAdapter, org.springframework.security.config.Customizer)}.
     */
    public static SaplHttpSecurityConfigurer saplHttp() {
        return new SaplHttpSecurityConfigurer();
    }

    @Override
    public void init(HttpSecurity http) {
        val manager = beanOf(http, SaplAuthorizationManager.class);
        val denied  = beanOf(http, SaplAccessDeniedHandler.class);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(manager))
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(denied));
    }

    @Override
    public void configure(HttpSecurity http) {
        val pep = beanOf(http, SaplHttpPepFilter.class);
        http.addFilterAfter(pep, AuthorizationFilter.class);
    }

    private static <T> T beanOf(HttpSecurity http, Class<T> type) {
        return http.getSharedObject(ApplicationContext.class).getBean(type);
    }
}
