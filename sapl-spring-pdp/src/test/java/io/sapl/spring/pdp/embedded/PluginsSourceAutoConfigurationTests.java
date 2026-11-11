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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.pdp.plugins.PluginsSource;
import io.sapl.pdp.plugins.StaticPluginsSource;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("An embedded PDP starts with built in contributions")
class PluginsSourceAutoConfigurationTests {

    @Test
    @DisplayName("The default PDP starts without a plugin directory loader")
    void whenStartingDefaultPdpThenBuiltInContributionsAreAvailable() {
        new ApplicationContextRunner().withBean(JsonMapper.class, JsonMapper::new)
                .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class))
                .withPropertyValues("io.sapl.pdp.embedded.policies-path=/policies").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PluginsSource.class);
                    assertThat(context.getBean(PluginsSource.class)).isInstanceOf(StaticPluginsSource.class);
                });
    }
}
