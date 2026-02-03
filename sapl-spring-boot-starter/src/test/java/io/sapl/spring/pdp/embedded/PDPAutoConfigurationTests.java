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

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PDPAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, JsonMapper::new)
            .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class));

    @Test
    void whenContextLoads_thenPDPIsCreated() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DynamicPolicyDecisionPoint.class);
                });
    }

    @Test
    void whenAnotherPDPIsAlreadyPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PolicyDecisionPoint.class)
                            .doesNotHaveBean(DynamicPolicyDecisionPoint.class);
                });
    }

}
