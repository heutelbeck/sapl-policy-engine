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

import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.providers.ContentFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ContentFilteringProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import tools.jackson.databind.ObjectMapper;

/**
 * Sets up the default constraint handler provides.
 *
 * @since 2.0.0
 */
@AutoConfiguration
@Import(value = { ConstraintEnforcementService.class })
public class ConstraintsHandlerAutoconfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ContentFilteringProvider jsonNodeContentFilteringProvider(ObjectMapper objectMapper) {
        return new ContentFilteringProvider(objectMapper);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ContentFilterPredicateProvider contentFilterPredicateProvider(ObjectMapper objectMapper) {
        return new ContentFilterPredicateProvider(objectMapper);
    }
}
