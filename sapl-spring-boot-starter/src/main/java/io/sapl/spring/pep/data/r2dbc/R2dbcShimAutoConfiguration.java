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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.r2dbc.core.DatabaseClient;

import io.sapl.spring.pep.constraints.providers.SqlQueryManipulationProvider;
import io.sapl.spring.pep.data.SaplContextPropagationActivator;

/**
 * Activates the R2DBC arm of the shim-signal architecture: registers the
 * {@code sql:queryManipulation} (alias {@code relational:queryManipulation})
 * constraint handler provider, declares {@code SqlShimSignal} as a supported
 * PEP signal, and wraps every {@code DatabaseClient} bean in a CGLIB proxy
 * via {@link DatabaseClientShimBeanPostProcessor} so all SQL paths fire the
 * shim before reaching the driver.
 * <p>
 * Active when Spring R2DBC is on the classpath and not explicitly disabled
 * by {@code io.sapl.method-security.r2dbc-shim.enabled=false}.
 * <p>
 * Wrapping at the {@code DatabaseClient} level requires that the active
 * {@code EnforcementPlan} is reachable from a synchronous Java method called
 * inside a Reactor flow. To satisfy that, the auto-configuration declares
 * {@link SaplContextPropagationActivator} which enables Reactor's automatic
 * context propagation. The hook is JVM-wide; opting out via the property
 * disables both the shim and the hook activation triggered by it.
 */
@AutoConfiguration
@ConditionalOnClass(DatabaseClient.class)
@ConditionalOnProperty(name = "io.sapl.method-security.r2dbc-shim.enabled", matchIfMissing = true)
public class R2dbcShimAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SqlQueryManipulationProvider sqlQueryManipulationProvider() {
        return new SqlQueryManipulationProvider();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DatabaseClientShimBeanPostProcessor databaseClientShimBeanPostProcessor() {
        return new DatabaseClientShimBeanPostProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    SaplContextPropagationActivator saplContextPropagationActivator() {
        return new SaplContextPropagationActivator();
    }
}
