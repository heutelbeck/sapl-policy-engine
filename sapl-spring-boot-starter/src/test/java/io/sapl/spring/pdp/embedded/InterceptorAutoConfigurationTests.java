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

import io.sapl.pdp.VoteInterceptor;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterceptorAutoConfiguration")
class InterceptorAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(InterceptorAutoConfiguration.class));

    @ParameterizedTest(name = "when {0} is enabled then interceptor is created")
    @ValueSource(strings = { "print-trace", "print-json-report", "print-text-report", "print-subscription-events",
            "print-unsubscription-events" })
    void whenReportingPropertyEnabledThenInterceptorIsCreated(String property) {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded." + property + "=true", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VoteInterceptor.class)
                            .hasSingleBean(ReportingDecisionInterceptor.class);
                });
    }

    @Test
    @DisplayName("no interceptor created when no reporting properties are enabled")
    void whenNoPrintOptionsAreEnabledThenNoInterceptorIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ReportingDecisionInterceptor.class);
        });
    }

}
