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

import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class VariablesAndCombinatorSourceAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VariablesAndCombinatorSourceAutoConfiguration.class));

    @TempDir
    File tempDir;

    @Test
    void whenFilesystemIsSet_thenFilesystemSourceIsDeployed() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=FILESYSTEM",
                "io.sapl.pdp.embedded.configPath=" + tempDir).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VariablesAndCombinatorSource.class);
                    assertThat(context).hasSingleBean(FileSystemVariablesAndCombinatorSource.class);
                });
    }

    @Test
    void whenResourcesIsSet_thenResourcesSourceIsDeployed() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.pdpConfigType=RESOURCES", "io.sapl.pdp.embedded.configPath=/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VariablesAndCombinatorSource.class);
                    assertThat(context).hasSingleBean(ResourcesVariablesAndCombinatorSource.class);
                });
    }

}
