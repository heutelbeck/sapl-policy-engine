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
package io.sapl.spring.pdp.embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.internal.TracedDecisionInterceptor;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Role;

/**
 * Auto-configuration for the {@link ReportingDecisionInterceptor}.
 * <p>
 * This interceptor logs authorization decisions in various formats based on
 * configuration properties:
 * <ul>
 * <li>{@code io.sapl.pdp.embedded.print-trace} - Log the full JSON trace</li>
 * <li>{@code io.sapl.pdp.embedded.print-json-report} - Log a condensed JSON
 * report</li>
 * <li>{@code io.sapl.pdp.embedded.print-text-report} - Log a human-readable
 * text report</li>
 * <li>{@code io.sapl.pdp.embedded.pretty-print-reports} - Pretty-print JSON
 * output</li>
 * </ul>
 * <p>
 * The interceptor is only created if at least one of the print options is
 * enabled.
 */
@RequiredArgsConstructor
@AutoConfiguration(before = PDPAutoConfiguration.class)
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
@ConditionalOnClass(name = "io.sapl.pdp.PolicyDecisionPointBuilder")
@ConditionalOnProperty(prefix = "io.sapl.pdp.embedded", name = "enabled", havingValue = "true")
public class InterceptorAutoConfiguration {

    private final ObjectMapper          mapper;
    private final EmbeddedPDPProperties properties;

    /**
     * Creates the reporting decision interceptor if any reporting is enabled.
     * <p>
     * The interceptor uses {@code Integer.MAX_VALUE} priority to ensure it runs
     * last and captures all modifications made by other interceptors.
     *
     * @return the reporting decision interceptor
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Conditional(ReportingEnabledCondition.class)
    @ConditionalOnMissingBean(ReportingDecisionInterceptor.class)
    TracedDecisionInterceptor reportingDecisionInterceptor() {
        return new ReportingDecisionInterceptor(mapper, properties.isPrettyPrintReports(), properties.isPrintTrace(),
                properties.isPrintJsonReport(), properties.isPrintTextReport());
    }

}
