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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AttributeRepositoryFactory")
class AttributeRepositoryFactoryTests {
    @Nested
    @DisplayName("when attributerepository.type is not supported")
    class WhenTypeIsUnsupported {

        @Test
        @DisplayName("then an unrecognized type fails fast instead of silently falling back to in-memory")
        void thenAnUnrecognizedTypeThrows() {
            val config = ObjectValue.builder().put("type", Value.of("not-a-real-backend")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not-a-real-backend")
                    .hasMessageContaining("test-tenant");
        }

        @Test
        @DisplayName("then a missing type fails fast instead of silently falling back to in-memory")
        void thenMissingTypeThrows() {
            val config = Value.EMPTY_OBJECT;

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("test-tenant");
        }

        @Test
        @DisplayName("an unknown type repository type is invalid")
        void whenTypeIsUnknownThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("type", Value.of("fake-backend")).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }
    }

    @Nested
    @DisplayName("when the pdp id is invalid")
    class WhenPdpIdIsInvalid {
        @ParameterizedTest
        @ValueSource(strings = { "", "bad pdp id" })
        @DisplayName("then the pdp id is rejected before the config is read")
        void thenPdpIdIsRejectedBeforeTheConfigIsRead(String pdpId) {
            val config = Value
                    .ofObject(Map.of("type", Value.of("redis"), "host", Value.of("h"), "port", Value.of(6379)));
            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, pdpId))
                    .isInstanceOf(PDPConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("when a provider for the type is on the classpath")
    class WhenProviderIsPresent {

        @Test
        @DisplayName("then create returns the repository of that provider")
        void thenCreateReturnsTheRepositoryOfTheProvider() {
            assertThat(AttributeRepositoryFactory.create(testConfig(), "test-pdp"))
                    .isInstanceOf(InMemoryAttributeRepository.class);
        }

        @Test
        @DisplayName("then the type is matched case-insensitively")
        void thenTypeIsMatchedCaseInsensitively() {
            val config = ObjectValue.builder().put("type", Value.of("TEST")).put("host", Value.of("localhost"))
                    .put("port", Value.of(5432)).put("password", Value.of("secret")).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then validate delegates to the provider")
        void thenValidateDelegatesToTheProvider() {
            val withoutPassword = ObjectValue.builder().put("type", Value.of(TestAttributeRepositoryProvider.TYPE))
                    .put("host", Value.of("localhost")).put("port", Value.of(5432)).build();

            assertThat(AttributeRepositoryFactory.validate(testConfig())).isTrue();
            assertThat(AttributeRepositoryFactory.validate(withoutPassword)).isFalse();
        }
    }

    @Nested
    @DisplayName("when the backend module is not on the classpath")
    class WhenBackendModuleIsMissing {

        @Test
        @DisplayName("then a known backend type is not supported and the message names the available types")
        void thenKnownTypeWithoutModuleThrows() {
            val config = Value
                    .ofObject(Map.of("type", Value.of("redis"), "host", Value.of("h"), "port", Value.of(6379)));

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-pdp"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("redis")
                    .hasMessageContaining("Available types: [test]");
        }

        @Test
        @DisplayName("then validate returns false for a known backend type")
        void thenValidateReturnsFalseForKnownType() {
            val config = Value
                    .ofObject(Map.of("type", Value.of("redis"), "host", Value.of("h"), "port", Value.of(6379)));

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }
    }

    private static ObjectValue testConfig() {
        return ObjectValue.builder().put("type", Value.of(TestAttributeRepositoryProvider.TYPE))
                .put("host", Value.of("localhost")).put("port", Value.of(5432)).put("password", Value.of("secret"))
                .build();
    }
}
