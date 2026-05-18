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

import io.sapl.pdp.configuration.PdpVoterSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdpHealthIndicatorAutoConfiguration")
class PdpHealthIndicatorAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, JsonMapper::new).withConfiguration(
                    AutoConfigurations.of(PDPAutoConfiguration.class, PdpHealthIndicatorAutoConfiguration.class));

    @Test
    @DisplayName("when actuator on classpath then health indicator bean is created")
    void whenActuatorOnClasspathThenHealthIndicatorBeanIsCreated() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(PdpHealthIndicator.class)
                        .hasSingleBean(PdpVoterSource.class));
    }

    @Test
    @DisplayName("when PDP disabled then no health indicator bean")
    void whenPdpDisabledThenNoHealthIndicatorBean() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(HealthIndicator.class));
    }

}
