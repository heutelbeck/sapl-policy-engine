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
package io.sapl.pdp.configuration;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.MongoAttributeRepository;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.RedisAttributeRepository;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockConstruction;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("AttributeRepositoryFactory")
class AttributeRepositoryFactoryTests {
    @Nested
    @DisplayName("when attributeRepository.type is not supported")
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
    @DisplayName("when table or collection name is not a valid identifier")
    class WhenNameIsInvalid {
        @Test
        @DisplayName("then an invalid table name is rejected before a Postgres connection is attempted")
        void thenInvalidTableNameThrows() {
            val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                    .put("port", Value.of(5432)).put("username", Value.of("sapl")).put("password", Value.of("secret"))
                    .put("database", Value.of("sapl")).put("tableName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("tableName")
                    .hasMessageContaining("test-tenant");
        }

        @Test
        @DisplayName("then an invalid collection name is rejected before a Mongo connection is attempted")
        void thenInvalidCollectionNameThrows() {
            val config = ObjectValue.builder().put("type", Value.of("mongo")).put("host", Value.of("localhost"))
                    .put("port", Value.of(27017)).put("database", Value.of("sapl"))
                    .put("collectionName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("collectionName")
                    .hasMessageContaining("test-tenant");
        }
    }

    @Nested
    @DisplayName("when validate is called")
    class WhenValidateIsCalled {
        static Stream<Arguments> invalidConfigs() {
            return Stream.of(arguments("without type", Value.EMPTY_OBJECT),
                    arguments("postgres without host",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "port", Value.of(5432), "username",
                                    Value.of("u"), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres without port",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "username",
                                    Value.of("u"), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres with port as text",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of("5432"), "username", Value.of("u"), "password", Value.of("p"), "database",
                                    Value.of("d")))),
                    arguments("postgres without username",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of(5432), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres without database",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of(5432), "username", Value.of("u"), "password", Value.of("p")))),
                    arguments("mongo without host",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "port", Value.of(27017), "database",
                                    Value.of("d")))),
                    arguments("mongo without port",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "database",
                                    Value.of("d")))),
                    arguments("mongo without database",
                            Value.ofObject(
                                    Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port", Value.of(27017)))),
                    arguments("redis without host",
                            Value.ofObject(Map.of("type", Value.of("redis"), "port", Value.of(6379)))));
        }

        @Test
        @DisplayName("then a complete Postgres config returns true")
        void whenPostgresConfigIsValidThenValidateReturnsTrue() {
            val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                    .put("port", Value.of(5432)).put("username", Value.of("sapl")).put("password", Value.of("secret"))
                    .put("database", Value.of("sapl")).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then a invalid Postgres config returns false")
        void whenPostgresConfigIsInvalidThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                    .put("port", Value.of(5432)).put("username", Value.of("sapl")).put("database", Value.of("sapl"))
                    .build();

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }

        @Test
        @DisplayName("then a complete Mongo config returns true")
        void whenMongoConfigHasNoCredentialsThenValidateReturnsTrue() {
            val config = ObjectValue.builder().put("type", Value.of("mongo")).put("host", Value.of("localhost"))
                    .put("port", Value.of(27017)).put("database", Value.of("sapl")).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then a invalid Mongo config returns false")
        void whenMongoConfigHasUsernameButNoPasswordThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("type", Value.of("mongo")).put("host", Value.of("localhost"))
                    .put("port", Value.of(27017)).put("database", Value.of("sapl")).put("username", Value.of("root"))
                    .build();

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }

        @Test
        @DisplayName("then a complete Redis config returns true")
        void whenRedisConfigHasOnlyHostAndPortThenValidateReturnsTrue() {
            val config = ObjectValue.builder().put("type", Value.of("redis")).put("host", Value.of("localhost"))
                    .put("port", Value.of(6379)).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isTrue();
        }

        @Test
        @DisplayName("then a invalid Redis config returns false")
        void whenRedisConfigIsMissingPortThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("type", Value.of("redis")).put("host", Value.of("localhost"))
                    .build();

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }

        @Test
        @DisplayName("an unknown type repository type is invalid")
        void whenTypeIsUnknownThenValidateReturnsFalse() {
            val config = ObjectValue.builder().put("type", Value.of("fake-backend")).build();

            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidConfigs")
        @DisplayName("then a config with a missing or malformed field that is required is invalid")
        void thenConfigWithMissingOrMalformedFieldIsRequiredOrInvalid(String description, ObjectValue config) {
            assertThat(AttributeRepositoryFactory.validate(config)).isFalse();
        }
    }

    @Nested
    @DisplayName("when create() is called")
    class WhenCreateIsCalled {
        private static final String PDP_ID = "test";

        static Stream<Arguments> configsWithMissingField() {
            return Stream.of(
                    arguments("postgres", "host",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "port", Value.of(5432), "username",
                                    Value.of("u"), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres", "port",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "username",
                                    Value.of("u"), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres", "username",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of(5432), "password", Value.of("p"), "database", Value.of("d")))),
                    arguments("postgres", "password",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of(5432), "username", Value.of("u"), "database", Value.of("d")))),
                    arguments("postgres", "database",
                            Value.ofObject(Map.of("type", Value.of("postgres"), "host", Value.of("h"), "port",
                                    Value.of(5432), "username", Value.of("u"), "password", Value.of("p")))),
                    arguments("mongo", "host",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "port", Value.of(27017), "database",
                                    Value.of("d")))),
                    arguments("mongo", "port",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "database",
                                    Value.of("d")))),
                    arguments("mongo", "database",
                            Value.ofObject(
                                    Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port", Value.of(27017)))),
                    arguments("mongo", "password",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port",
                                    Value.of(27017), "database", Value.of("d"), "username", Value.of("u")))),
                    arguments("redis", "host",
                            Value.ofObject(Map.of("type", Value.of("redis"), "port", Value.of(6379)))),
                    arguments("redis", "port",
                            Value.ofObject(Map.of("type", Value.of("redis"), "host", Value.of("h")))));
        }

        static Stream<Arguments> mongoConfigs() {
            return Stream.of(
                    arguments("without credentials",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port",
                                    Value.of(27017), "database", Value.of("d"))),
                            "attributes"),
                    arguments("credentials with auth database",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port",
                                    Value.of(27017), "database", Value.of("d"), "username", Value.of("sapl@user"),
                                    "password", Value.of("p:w/d"), "authDatabase", Value.of("admin"))),
                            "attributes"),
                    arguments("credentials without auth database",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port",
                                    Value.of(27017), "database", Value.of("d"), "username", Value.of("u"), "password",
                                    Value.of("p"))),
                            "attributes"),
                    arguments("blank username and custom collection",
                            Value.ofObject(Map.of("type", Value.of("mongo"), "host", Value.of("h"), "port",
                                    Value.of(27017), "database", Value.of("d"), "username", Value.of("  "),
                                    "collectionName", Value.of("custom_collection"))),
                            "custom_collection"));
        }

        static Stream<Arguments> redisConfigs() {
            return Stream.of(
                    arguments("without database and password",
                            Value.ofObject(
                                    Map.of("type", Value.of("redis"), "host", Value.of("h"), "port", Value.of(6379))),
                            0),
                    arguments("with password and database",
                            Value.ofObject(Map.of("type", Value.of("redis"), "host", Value.of("h"), "port",
                                    Value.of(6379), "password", Value.of("secret"), "database", Value.of(3))),
                            3),
                    arguments("with blank password", Value.ofObject(Map.of("type", Value.of("redis"), "host",
                            Value.of("h"), "port", Value.of(6379), "password", Value.of("  "))), 0));
        }

        @ParameterizedTest(name = "{0} without {1}")
        @MethodSource("configsWithMissingField")
        @DisplayName("then a missing required field names the field and the pdp id")
        void thenMissingRequiredFieldNamesAreNamedWithThePdpId(String type, String missingField, ObjectValue config) {
            assertThatNullPointerException().isThrownBy(() -> AttributeRepositoryFactory.create(config, PDP_ID))
                    .withMessageContainingAll(missingField).withMessageContaining(PDP_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("mongoConfigs")
        @DisplayName("then Mongo configs with and without credentials create a Mongo repository")
        void thenMongoConfigsCreateMongoRepository(String description, ObjectValue config, String expectedCollection) {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(MongoAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = AttributeRepositoryFactory.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(1)).isEqualTo(PDP_ID);
                assertThat(constructorArguments.get(2)).isEqualTo(expectedCollection);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("redisConfigs")
        @DisplayName("then redis configs create a redis repository on the expected database")
        void thenRedisConfigsCreateRedisRepositoryOnExpectedDatabase(String description, ObjectValue config,
                int expectedDatabase) {
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(RedisAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = AttributeRepositoryFactory.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(1)).isEqualTo(PDP_ID);
                assertThat(constructorArguments.get(2)).isEqualTo(expectedDatabase);
            }
        }

        @Test
        @DisplayName("then Postgres repository is returned")
        void thenPostgresRepositoryIsReturned() {
            val config               = ObjectValue.builder().put("type", Value.of("postgres"))
                    .put("host", Value.of("localhost")).put("port", Value.of(5432)).put("username", Value.of("sapl"))
                    .put("password", Value.of("secret")).put("database", Value.of("sapl")).build();
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(PostgresAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = AttributeRepositoryFactory.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(2)).isEqualTo(PDP_ID);
                assertThat(constructorArguments.get(3)).isEqualTo("attributes");
            }
        }

        @Test
        @DisplayName("then Mongo repository is returned")
        void thenMongoRepositoryIsReturned() {
            val config               = ObjectValue.builder().put("type", Value.of("mongo"))
                    .put("host", Value.of("localhost")).put("port", Value.of(27017)).put("database", Value.of("sapl"))
                    .build();
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(MongoAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = AttributeRepositoryFactory.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(2)).isEqualTo("attributes");
            }
        }

        @Test
        @DisplayName("then Redis repository is returned")
        void thenRedisRepositoryIsReturned() {
            val config               = ObjectValue.builder().put("type", Value.of("redis"))
                    .put("host", Value.of("localhost")).put("port", Value.of(6379)).build();
            val constructorArguments = new ArrayList<Object>();

            try (val constructed = mockConstruction(RedisAttributeRepository.class,
                    (mock, context) -> constructorArguments.addAll(context.arguments()))) {
                val repository = AttributeRepositoryFactory.create(config, PDP_ID);

                assertThat(repository).isSameAs(constructed.constructed().getFirst());
                assertThat(constructorArguments.get(2).toString()).contains("0");
            }
        }
    }
}
