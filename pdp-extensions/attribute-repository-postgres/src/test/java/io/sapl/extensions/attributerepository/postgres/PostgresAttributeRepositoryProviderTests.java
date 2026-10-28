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
package io.sapl.extensions.attributerepository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Tests the PostgreSQL provider without a running database. The repository itself is tested in
 * {@link PostgresAttributeRepositoryTests}.
 */
@DisplayName("PostgresAttributeRepositoryProvider")
class PostgresAttributeRepositoryProviderTests {
    private static final String PDP_ID = "test-pdp";

    private final PostgresAttributeRepositoryProvider provider = new PostgresAttributeRepositoryProvider();

    @Nested
    @DisplayName("when the provider is registered")
    class WhenProviderIsRegistered {

        @Test
        @DisplayName("then its type is postgres")
        void thenTypeIsPostgres() {
            assertThat(provider.type()).isEqualTo("postgres");
        }

        @Test
        @DisplayName("then the service finds the service loader config")
        void thenServiceLoaderFindsTheProvider() {
            val types = ServiceLoader.load(AttributeRepositoryProvider.class).stream()
                    .map(loaded -> loaded.get().type()).toList();

            assertThat(types).contains("postgres");
        }
    }

    @Nested
    @DisplayName("when validate is called")
    class WhenValidateIsCalled {

        static Stream<Arguments> invalidConfigs() {
            return Stream.of(
                    arguments("without host",
                            Value.ofObject(Map.of("port", Value.of(5432), "username", Value.of("u"), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("without port",
                            Value.ofObject(Map.of("host", Value.of("h"), "username", Value.of("u"), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("with port as text",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of("5432"), "username",
                                    Value.of("u"), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("without username",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(5432), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("without database", Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(5432),
                            "username", Value.of("u"), "password", Value.of("p")))));
        }

        @Test
        @DisplayName("then a complete Postgres config returns true")
        void whenPostgresConfigIsValidThenValidateReturnsTrue() {
            assertThat(provider.validate(completeConfig().build())).isTrue();
        }

        @Test
        @DisplayName("then a config without password returns false")
        void whenPasswordIsMissingThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(5432))
                    .put("username", Value.of("sapl")).put("database", Value.of("sapl")).build();

            assertThat(provider.validate(config)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidConfigs")
        @DisplayName("then a config with a missing or malformed field that is required is invalid")
        void thenConfigWithMissingOrMalformedFieldIsInvalid(String description, ObjectValue config) {
            assertThat(provider.validate(config)).isFalse();
        }
    }

    @Nested
    @DisplayName("when create is called")
    class WhenCreateIsCalled {

        static Stream<Arguments> configsWithMissingField() {
            return Stream.of(
                    arguments("host",
                            Value.ofObject(Map.of("port", Value.of(5432), "username", Value.of("u"), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("port",
                            Value.ofObject(Map.of("host", Value.of("h"), "username", Value.of("u"), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("username",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(5432), "password",
                                    Value.of("p"), "database", Value.of("d")))),
                    arguments("password",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(5432), "username",
                                    Value.of("u"), "database", Value.of("d")))),
                    arguments("database", Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(5432),
                            "username", Value.of("u"), "password", Value.of("p")))));
        }

        @ParameterizedTest(name = "without {0}")
        @MethodSource("configsWithMissingField")
        @DisplayName("then a missing required field names the field and the pdp id")
        void thenMissingRequiredFieldNamesTheFieldAndThePdpId(String missingField, ObjectValue config) {
            assertThatNullPointerException().isThrownBy(() -> provider.create(config, PDP_ID))
                    .withMessageContaining(missingField).withMessageContaining(PDP_ID);
        }

        @Test
        @DisplayName("then an invalid table name is rejected before a Postgres connection is attempted")
        void thenInvalidTableNameThrows() {
            val config = completeConfig().put("tableName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> provider.create(config, PDP_ID)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tableName").hasMessageContaining(PDP_ID);
        }

        @Test
        @DisplayName("then Postgres repository is returned")
        void thenPostgresRepositoryIsReturned() {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(PostgresAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = provider.create(completeConfig().build(), PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(2)).isEqualTo(PDP_ID);
            }
        }

        @Test
        @DisplayName("then the table name defaults to attributes")
        void thenTableNameDefaultsToAttributes() {
            assertThat(tableNamePassedToRepository(completeConfig().build())).isEqualTo("attributes");
        }

        @Test
        @DisplayName("then a valid table name is passed to the repository")
        void thenValidTableNameIsPassedToTheRepository() {
            val config = completeConfig().put("tableName", Value.of("my_table")).build();

            assertThat(tableNamePassedToRepository(config)).isEqualTo("my_table");
        }

        // Constructor arguments of PostgresAttributeRepository: (client, connectionFactory, pdpId, table)
        private Object tableNamePassedToRepository(ObjectValue config) {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(PostgresAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                provider.create(config, PDP_ID);
                return constructorArguments.get(3);
            }
        }
    }

    private static ObjectValue.Builder completeConfig() {
        return ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(5432))
                .put("username", Value.of("sapl")).put("password", Value.of("secret"))
                .put("database", Value.of("sapl"));
    }
}
