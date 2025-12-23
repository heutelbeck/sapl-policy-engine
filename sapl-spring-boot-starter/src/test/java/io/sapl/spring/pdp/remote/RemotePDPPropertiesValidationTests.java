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
package io.sapl.spring.pdp.remote;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RemotePDPPropertiesValidationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnablePropertiesInApplicationTestRunnerConfiguration.class);

    @EnableConfigurationProperties(RemotePDPProperties.class)
    static class EnablePropertiesInApplicationTestRunnerConfiguration {

    }

    @Test
    void whenValidRsocketPropertiesPresent_thenConfigurationBeanIsPresent() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.rsocketHost=localhost",
                "io.sapl.pdp.remote.rsocketPort=7000", "io.sapl.pdp.remote.apiKey=aValidApiKey")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void whenValidHttpPropertiesPresent_thenConfigurationBeanIsPresent() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.type=http",
                "io.sapl.pdp.remote.host=https://localhost:8443", "io.sapl.pdp.remote.apiKey=aApiKey")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void whenInvalidHostPropertyPresent_thenConfigurationFails() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=http", "io.sapl.pdp.remote.host=ht tps://loc alhost:8443",
                        "io.sapl.pdp.remote.key=aKey", "io.sapl.pdp.remote.secret=aSecret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenHostPropertyMissing_thenConfigurationFails() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.rsocketHost=", "io.sapl.pdp.remote.key=aKey",
                "io.sapl.pdp.remote.secret=aSecret").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenKeyPropertyMissing_thenConfigurationFails() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.host=https://localhost:8443", "io.sapl.pdp.remote.key=",
                "io.sapl.pdp.remote.secret=aSecret").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenSecretPropertyMissing_thenConfigurationFails() {
        contextRunner.withPropertyValues("io.sapl.pdp.remote.host=https://localhost:8443",
                "io.sapl.pdp.remote.key=aKey", "io.sapl.pdp.remote.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void whenInvalidTypePropertyPresent_thenConfigurationFails() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.remote.type=other", "io.sapl.pdp.remote.host=https://localhost:8443",
                        "io.sapl.pdp.remote.key=aKey", "io.sapl.pdp.remote.secret=aSecret")
                .run(context -> assertThat(context).hasFailed());
    }
}
