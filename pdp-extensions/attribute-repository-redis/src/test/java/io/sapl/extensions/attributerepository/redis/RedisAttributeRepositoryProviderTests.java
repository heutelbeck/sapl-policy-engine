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
package io.sapl.extensions.attributerepository.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mockConstruction;

import java.util.ArrayList;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.extensions.attributerepository.AttributeRepositoryProvider;
import lombok.val;

/**
 * Tests the Redis provider without a running Redis. The repository itself is tested in
 * {@link RedisAttributeRepositoryTests}.
 */
@DisplayName("RedisAttributeRepositoryProvider")
class RedisAttributeRepositoryProviderTests {
    private static final String PDP_ID = "test-pdp";

    private final RedisAttributeRepositoryProvider provider = new RedisAttributeRepositoryProvider();

    @Nested
    @DisplayName("when the provider is registered")
    class WhenProviderIsRegistered {

        @Test
        @DisplayName("then its type is redis")
        void thenTypeIsRedis() {
            assertThat(provider.type()).isEqualTo("redis");
        }

        @Test
        @DisplayName("then the ServiceLoader finds it via META-INF/services")
        void thenServiceLoaderFindsTheProvider() {
            val types = ServiceLoader.load(AttributeRepositoryProvider.class).stream()
                    .map(loaded -> loaded.get().type()).toList();

            assertThat(types).contains("redis");
        }
    }

    @Nested
    @DisplayName("when validate is called")
    class WhenValidateIsCalled {

        @Test
        @DisplayName("then a complete Redis config returns true")
        void whenRedisConfigHasOnlyHostAndPortThenValidateReturnsTrue() {
            val config = ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(6379)).build();

            assertThat(provider.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then a config without port returns false")
        void whenRedisConfigIsMissingPortThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("host", Value.of("localhost")).build();

            assertThat(provider.validate(config)).isFalse();
        }

        @Test
        @DisplayName("then a config without host returns false")
        void whenRedisConfigIsMissingHostThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("port", Value.of(6379)).build();

            assertThat(provider.validate(config)).isFalse();
        }

        @Test
        @DisplayName("then a port given as text returns false")
        void whenPortIsTextThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of("6379")).build();

            assertThat(provider.validate(config)).isFalse();
        }
    }

    @Nested
    @DisplayName("when create is called")
    class WhenCreateIsCalled {

        static Stream<Arguments> configsWithMissingField() {
            return Stream.of(arguments("host", Value.ofObject(Map.of("port", Value.of(6379)))),
                    arguments("port", Value.ofObject(Map.of("host", Value.of("h")))));
        }

        static Stream<Arguments> redisConfigs() {
            return Stream.of(
                    arguments("without database and password",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(6379))), 0),
                    arguments("with password and database",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(6379), "password",
                                    Value.of("secret"), "database", Value.of(3))),
                            3),
                    arguments("with blank password",
                            Value.ofObject(
                                    Map.of("host", Value.of("h"), "port", Value.of(6379), "password", Value.of("  "))),
                            0));
        }

        @ParameterizedTest(name = "without {0}")
        @MethodSource("configsWithMissingField")
        @DisplayName("then a missing required field names the field and the pdp id")
        void thenMissingRequiredFieldNamesTheFieldAndThePdpId(String missingField, ObjectValue config) {
            assertThatNullPointerException().isThrownBy(() -> provider.create(config, PDP_ID))
                    .withMessageContaining(missingField).withMessageContaining(PDP_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("redisConfigs")
        @DisplayName("then redis configs create a redis repository on the expected database")
        void thenRedisConfigsCreateRedisRepositoryOnExpectedDatabase(String description, ObjectValue config,
                int expectedDatabase) {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(RedisAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = provider.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(1)).isEqualTo(PDP_ID);
                assertThat(constructorArguments.get(2)).isEqualTo(expectedDatabase);
            }
        }
    }
}
