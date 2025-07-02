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

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Conditional(SetupNotFinishedCondition.class)
public class HttpSecurityConfigurationSetupNotFinished extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher.Builder match = PathPatternRequestMatcher.withDefaults();
        http.authorizeHttpRequests(requests -> requests.requestMatchers(match.matcher("/images/*.png")).permitAll());

        // Icons from the line-awesome addon
        http.authorizeHttpRequests(
                requests -> requests.requestMatchers(match.matcher("/line-awesome/**/*.svg")).permitAll());

        // Deny API
        http.authorizeHttpRequests(requests -> requests.requestMatchers(match.matcher("/api/**")).denyAll());

        // Deny Xtext-Service
        http.authorizeHttpRequests(requests -> requests.requestMatchers(match.matcher("/xtext-service/**")).denyAll());

        super.configure(http);
    }

}
