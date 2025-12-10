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
package io.sapl.spring.pdp.embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class AllAutoConfigurationsIntegrationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class))
            .withConfiguration(AutoConfigurations.of(InterceptorAutoConfiguration.class));

    @TempDir
    File tempDir;

    @Test
    void whenFilesystemPrpIsConfiguredAndTheEntireAutoconfigurationRuns_thenAPDPIsCreated() {
        contextRunner.withBean(ObjectMapper.class, new ObjectMapper())
                .withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM",
                        "io.sapl.pdp.embedded.enabled=true", "io.sapl.pdp.embedded.policiesPath=" + tempDir)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DynamicPolicyDecisionPoint.class);
                });
    }

    @Test
    void whenResourcesPrpIsConfiguredAndTheEntireAutoconfigurationRuns_thenAPDPIsCreated() {
        contextRunner.withBean(ObjectMapper.class, new ObjectMapper())
                .withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOURCES", "io.sapl.pdp.embedded.enabled=true",
                        "io.sapl.pdp.embedded.policiesPath=/policies")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DynamicPolicyDecisionPoint.class);
                });
    }

}
