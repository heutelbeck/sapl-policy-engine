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
import io.sapl.pdp.VoteInterceptor;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class InterceptorAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(InterceptorAutoConfiguration.class));

    @Test
    void whenPrintTraceIsEnabled_thenReportingInterceptorIsCreated() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.print-trace=true", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VoteInterceptor.class)
                            .hasSingleBean(ReportingDecisionInterceptor.class);
                });
    }

    @Test
    void whenPrintJsonReportIsEnabled_thenReportingInterceptorIsCreated() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.print-json-report=true", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VoteInterceptor.class)
                            .hasSingleBean(ReportingDecisionInterceptor.class);
                });
    }

    @Test
    void whenPrintTextReportIsEnabled_thenReportingInterceptorIsCreated() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.print-text-report=true", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VoteInterceptor.class)
                            .hasSingleBean(ReportingDecisionInterceptor.class);
                });
    }

    @Test
    void whenNoPrintOptionsAreEnabled_thenNoInterceptorIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ReportingDecisionInterceptor.class);
        });
    }

}
