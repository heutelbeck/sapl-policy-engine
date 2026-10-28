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
package io.sapl.extensions.attributerepository.mongo;

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
 * Tests the MongoDB provider without a running database. The repository itself is tested in
 * {@link MongoAttributeRepositoryTests}.
 */
@DisplayName("MongoAttributeRepositoryProvider")
class MongoAttributeRepositoryProviderTests {
    private static final String PDP_ID = "test-pdp";

    private final MongoAttributeRepositoryProvider provider = new MongoAttributeRepositoryProvider();

    @Nested
    @DisplayName("when the provider is registered")
    class WhenProviderIsRegistered {

        @Test
        @DisplayName("then its type is mongo")
        void thenTypeIsMongo() {
            assertThat(provider.type()).isEqualTo("mongo");
        }

        @Test
        @DisplayName("then the service finds the service loader config")
        void thenServiceLoaderFindsTheProvider() {
            val types = ServiceLoader.load(AttributeRepositoryProvider.class).stream()
                    .map(loaded -> loaded.get().type()).toList();

            assertThat(types).contains("mongo");
        }
    }

    @Nested
    @DisplayName("when validate is called")
    class WhenValidateIsCalled {

        static Stream<Arguments> invalidConfigs() {
            return Stream.of(
                    arguments("without host",
                            Value.ofObject(Map.of("port", Value.of(27017), "database", Value.of("d")))),
                    arguments("without port", Value.ofObject(Map.of("host", Value.of("h"), "database", Value.of("d")))),
                    arguments("without database",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017)))));
        }

        @Test
        @DisplayName("then a complete Mongo config without credentials returns true")
        void whenMongoConfigHasNoCredentialsThenValidateReturnsTrue() {
            assertThat(provider.validate(minimalConfig().build())).isTrue();
        }

        @Test
        @DisplayName("then a complete Mongo config with username and password returns true")
        void whenMongoConfigHasUsernameAndPasswordThenValidateReturnsTrue() {
            val config = minimalConfig().put("username", Value.of("root")).put("password", Value.of("secret")).build();

            assertThat(provider.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then a config with username but without password returns false")
        void whenMongoConfigHasUsernameButNoPasswordThenValidateReturnsFalse() {
            val config = minimalConfig().put("username", Value.of("root")).build();

            assertThat(provider.validate(config)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidConfigs")
        @DisplayName("then a config with a missing field that is required is invalid")
        void thenConfigWithMissingFieldIsInvalid(String description, ObjectValue config) {
            assertThat(provider.validate(config)).isFalse();
        }
    }

    @Nested
    @DisplayName("when create is called")
    class WhenCreateIsCalled {

        static Stream<Arguments> configsWithMissingField() {
            return Stream.of(
                    arguments("host", Value.ofObject(Map.of("port", Value.of(27017), "database", Value.of("d")))),
                    arguments("port", Value.ofObject(Map.of("host", Value.of("h"), "database", Value.of("d")))),
                    arguments("database", Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017)))),
                    arguments("password", Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017),
                            "database", Value.of("d"), "username", Value.of("u")))));
        }

        static Stream<Arguments> mongoConfigs() {
            return Stream.of(
                    arguments("without credentials",
                            Value.ofObject(
                                    Map.of("host", Value.of("h"), "port", Value.of(27017), "database", Value.of("d"))),
                            "attributes"),
                    arguments("credentials with auth database",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017), "database",
                                    Value.of("d"), "username", Value.of("sapl@user"), "password", Value.of("p:w/d"),
                                    "authDatabase", Value.of("admin"))),
                            "attributes"),
                    arguments("credentials without auth database",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017), "database",
                                    Value.of("d"), "username", Value.of("u"), "password", Value.of("p"))),
                            "attributes"),
                    arguments("blank username and custom collection",
                            Value.ofObject(Map.of("host", Value.of("h"), "port", Value.of(27017), "database",
                                    Value.of("d"), "username", Value.of("  "), "collectionName",
                                    Value.of("custom_collection"))),
                            "custom_collection"));
        }

        @ParameterizedTest(name = "without {0}")
        @MethodSource("configsWithMissingField")
        @DisplayName("then a missing required field names the field and the pdp id")
        void thenMissingRequiredFieldNamesTheFieldAndThePdpId(String missingField, ObjectValue config) {
            assertThatNullPointerException().isThrownBy(() -> provider.create(config, PDP_ID))
                    .withMessageContaining(missingField).withMessageContaining(PDP_ID);
        }

        @Test
        @DisplayName("then an invalid collection name is rejected before a Mongo connection is attempted")
        void thenInvalidCollectionNameThrows() {
            val config = minimalConfig().put("collectionName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> provider.create(config, PDP_ID)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("collectionName").hasMessageContaining(PDP_ID);
        }

        // Constructor arguments of MongoAttributeRepository: (mongoTemplate, pdpId, collection)
        @ParameterizedTest(name = "{0}")
        @MethodSource("mongoConfigs")
        @DisplayName("then Mongo configs with and without credentials create a Mongo repository")
        void thenMongoConfigsCreateMongoRepository(String description, ObjectValue config, String expectedCollection) {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(MongoAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = provider.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(1)).isEqualTo(PDP_ID);
                assertThat(constructorArguments.get(2)).isEqualTo(expectedCollection);
            }
        }
    }

    private static ObjectValue.Builder minimalConfig() {
        return ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(27017)).put("database",
                Value.of("sapl"));
    }
}
