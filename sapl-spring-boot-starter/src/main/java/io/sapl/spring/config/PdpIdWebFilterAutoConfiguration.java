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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configures the {@link PdpIdWebFilter} when a
 * {@link PdpIdAuthenticationExtractor} bean is present in a reactive web
 * application.
 * <p>
 * This enables multi-tenant PDP routing for Spring WebFlux applications
 * using an embedded {@link io.sapl.api.pdp.MultiTenantPolicyDecisionPoint}.
 * The filter writes the extracted PDP ID to the Reactor Context on every
 * request.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnBean(PdpIdAuthenticationExtractor.class)
public class PdpIdWebFilterAutoConfiguration {

    @Bean
    PdpIdWebFilter pdpIdWebFilter(PdpIdAuthenticationExtractor extractor) {
        log.info("Multi-tenant PDP ID web filter enabled");
        return new PdpIdWebFilter(extractor);
    }

}
