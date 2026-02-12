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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;

@DisplayName("PdpSourceValidation")
class PdpSourceValidationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @EnableConfigurationProperties(EmbeddedPDPProperties.class)
    static class TestConfiguration {

    }

    @Nested
    @DisplayName("when RESOURCES is configured")
    class ResourcesRejection {

        @Test
        @DisplayName("rejects RESOURCES as PDP config type")
        void whenResourcesConfigured_thenContextFails() {
            contextRunner.withPropertyValues("io.sapl.pdp.embedded.pdp-config-type=RESOURCES")
                    .withBean(PdpSourceValidation.class)
                    .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(PdpSourceValidation.ERROR_RESOURCES_NOT_SUPPORTED));
        }

    }

    @Nested
    @DisplayName("when a supported type is configured")
    class SupportedTypes {

        @ParameterizedTest(name = "{0} is accepted")
        @MethodSource("supportedConfigTypes")
        @DisplayName("accepts supported PDP config types")
        void whenSupportedTypeConfigured_thenNoValidationError(String configType) {
            contextRunner.withPropertyValues("io.sapl.pdp.embedded.pdp-config-type=" + configType)
                    .withBean(PdpSourceValidation.class).run(context -> assertThat(context).hasNotFailed());
        }

        static Stream<String> supportedConfigTypes() {
            return Stream.of("DIRECTORY", "MULTI_DIRECTORY", "BUNDLES");
        }

    }

}
