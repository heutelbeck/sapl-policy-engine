/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.security;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import lombok.val;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;

@Configuration
@EnableWebSecurity
@Conditional(SetupNotFinishedCondition.class)
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class HttpSecurityConfigurationSetupNotFinished {

    /**
     * Configures HTTP security for the setup-not-finished phase.
     * Denies access to REST and Xtext endpoints, permits PNG images under /images/,
     * requires authentication for any remaining request, and applies Vaadin’s
     * integration.
     *
     * @param http the HttpSecurity to configure.
     * @return the configured SecurityFilterChain.
     * @throws Exception if building the filter chain fails.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        val mvc = PathPatternRequestMatcher.withDefaults();

        // Apply Vaadin first so it can register its own request matchers before
        // anyRequest().
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> { /* defaults */ });

        // Add only specific matchers here; do not call anyRequest() to avoid conflicts.
        http.authorizeHttpRequests(authz -> authz.requestMatchers(mvc.matcher("/images/*.png")).permitAll()
                .requestMatchers(mvc.matcher("/api/**")).denyAll().requestMatchers(mvc.matcher("/xtext-service/**"))
                .denyAll());

        return http.build();
    }

}
