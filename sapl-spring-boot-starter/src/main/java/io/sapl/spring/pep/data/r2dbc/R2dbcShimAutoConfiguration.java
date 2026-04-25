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
package io.sapl.spring.pep.data.r2dbc;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import io.sapl.spring.pep.constraints.providers.RelationalQueryManipulationProvider;
import io.sapl.spring.pep.constraints.providers.SqlQueryManipulationProvider;

/**
 * Activates the R2DBC arm of the shim-signal architecture: registers the
 * {@code relational:queryManipulation} constraint handler provider, declares
 * the {@code RelationalQueryShimSignal} as a supported PEP signal, and wraps
 * every {@code R2dbcEntityTemplate} bean in a CGLIB proxy via
 * {@link R2dbcShimBeanPostProcessor} so Query-bearing operations fire the
 * shim. Active when Spring Data R2DBC is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(R2dbcRepository.class)
public class R2dbcShimAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RelationalQueryManipulationProvider relationalQueryManipulationProvider() {
        return new RelationalQueryManipulationProvider();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SqlQueryManipulationProvider sqlQueryManipulationProvider() {
        return new SqlQueryManipulationProvider();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    R2dbcShimBeanPostProcessor r2dbcShimBeanPostProcessor() {
        return new R2dbcShimBeanPostProcessor();
    }
}
