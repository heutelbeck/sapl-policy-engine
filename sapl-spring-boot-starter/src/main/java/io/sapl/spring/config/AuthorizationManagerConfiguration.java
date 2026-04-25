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
package io.sapl.spring.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.manager.ReactiveSaplAuthorizationManager;
import io.sapl.spring.manager.SaplAuthorizationManager;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Deploys an AuthorizationManager for the HTTP filter chain. Servlet runtime
 * gets the blocking {@link SaplAuthorizationManager}; reactive runtime gets
 * {@link ReactiveSaplAuthorizationManager}. Both share the same
 * {@link EnforcementPlanner} bean (defined by the method-security
 * configuration).
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
public class AuthorizationManagerConfiguration {

    private final PolicyDecisionPoint pdp;
    private final EnforcementPlanner  enforcementPlanner;
    private final ObjectMapper        mapper;

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SaplAuthorizationManager saplAuthorizationManager() {
        log.debug("Servlet-based environment detected. Deploy SaplAuthorizationManager.");
        return new SaplAuthorizationManager(pdp, enforcementPlanner, mapper);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    ReactiveSaplAuthorizationManager reactiveSaplAuthorizationManager() {
        log.debug("Webflux environment detected. Deploy ReactiveSaplAuthorizationManager.");
        return new ReactiveSaplAuthorizationManager(pdp, enforcementPlanner, mapper);
    }
}
