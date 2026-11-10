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
package io.sapl.extensions.attributerepository;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.sapl.pdp.configuration.ExtensionsProcessor;

class AttributeRepositoryAutoConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AttributeRepositoryAutoConfiguration.class));

    @Test
    @DisplayName("when an auto configuration is built then routing attribute repository is built")
    void whenAutoConfigurationIsCreatedThenRoutingRepo() {
        var autoConfig = new AttributeRepositoryAutoConfiguration();
        assertThat(autoConfig.attributeRepository()).isInstanceOf(RoutingAttributeRepository.class);
    }

    @Test
    @DisplayName("when extension processor is called then extension processor class is built")
    void whenExtensionProcessorIsCalledThenExtensionProcessorClass() {
        var autoConfig = new AttributeRepositoryAutoConfiguration();
        assertThat(autoConfig.attributeRepositoryExtensionsProcessor(autoConfig.attributeRepository()))
                .isInstanceOf(ExtensionsProcessor.class);
    }

    @Test
    @DisplayName("when embedded activated then config resolver bean is returned")
    void whenEmbeddedThenConfigResolverIsReturned() {
        runner.withPropertyValues("io.sapl.attribute-api.enabled=true")
                .run(context -> assertThat(context).hasBean("attributeRepositoryConfigResolver"));
    }

    @Test
    @DisplayName("when embedded activated then config resolver bean is returned")
    void whenNotEmbeddedThenNoConfigResolverIsReturned() {
        runner.run(context -> assertThat(context).doesNotHaveBean("attributeRepositoryConfigResolver"));
    }

}
