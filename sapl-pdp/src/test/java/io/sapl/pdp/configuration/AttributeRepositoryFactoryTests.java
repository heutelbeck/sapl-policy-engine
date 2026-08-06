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
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttributeRepositoryFactory")
class AttributeRepositoryFactoryTests {

    @Nested
    @DisplayName("when attributeRepository.type is not supported")
    class WhenTypeIsUnsupported {

        @Test
        @DisplayName("an unrecognized type fails fast instead of silently falling back to in-memory")
        void unrecognizedTypeThrows() {
            val config = ObjectValue.builder().put("type", Value.of("not-a-real-backend")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not-a-real-backend")
                    .hasMessageContaining("test-tenant");
        }

        @Test
        @DisplayName("a missing type fails fast instead of silently falling back to in-memory")
        void missingTypeThrows() {
            val config = Value.EMPTY_OBJECT;

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("test-tenant");
        }
    }

    @Nested
    @DisplayName("when tableName/collectionName is not a valid identifier")
    class WhenNameIsInvalid {

        @Test
        @DisplayName("an invalid tableName is rejected before a Postgres connection is attempted")
        void invalidTableNameThrows() {
            val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                    .put("port", Value.of(5432)).put("username", Value.of("sapl")).put("password", Value.of("secret"))
                    .put("database", Value.of("sapl")).put("tableName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("tableName")
                    .hasMessageContaining("test-tenant");
        }

        @Test
        @DisplayName("an invalid collectionName is rejected before a Mongo connection is attempted")
        void invalidCollectionNameThrows() {
            val config = ObjectValue.builder().put("type", Value.of("mongo")).put("host", Value.of("localhost"))
                    .put("port", Value.of(27017)).put("database", Value.of("sapl"))
                    .put("collectionName", Value.of("not a valid name")).build();

            assertThatThrownBy(() -> AttributeRepositoryFactory.create(config, "test-tenant"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("collectionName")
                    .hasMessageContaining("test-tenant");
        }
    }
}
