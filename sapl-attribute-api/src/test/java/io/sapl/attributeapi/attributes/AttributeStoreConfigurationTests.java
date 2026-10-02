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
package io.sapl.attributeapi.attributes;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributeapi.attributes.AttributeStorageProperties.BackendType;

class AttributeStoreConfigurationTests {
    @Test
    @DisplayName("when a valid Postgres configuration is given and the optional table name is missing then the default is used")
    void whenPostgresConfigWithoutTableNameThenDefaultIsUsed() {
        var node = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).put("username", Value.of("sapl"))
                .put("password", Value.of("password")).build();

        var config = new AttributeStoreConfiguration().toBackendConfig(node);

        assertThat(config.getType()).isEqualTo(BackendType.POSTGRES);
        assertThat(config.getPostgres().getTableName()).isEqualTo("attributes");
    }

    @Test
    @DisplayName("when a valid Mongo configuration is given and the optional collection name is missing then the default is used")
    void whenMongoConfigWithoutCollectionNameThenDefaultIsUsed() {
        var node = ObjectValue.builder().put("type", Value.of("mongo")).put("host", Value.of("localhost"))
                .put("port", Value.of(27001)).put("database", Value.of("sapl")).put("username", Value.of("sapl"))
                .put("password", Value.of("password")).build();

        var config = new AttributeStoreConfiguration().toBackendConfig(node);

        assertThat(config.getType()).isEqualTo(BackendType.MONGO);
        assertThat(config.getMongo().getCollectionName()).isEqualTo("attributes");
    }

    @Test
    @DisplayName("when a valid Redis configuration is given and the optional db is missing then the default is used")
    void whenRedisConfigWithoutDbThenDefaultIsUsed() {
        var node = ObjectValue.builder().put("type", Value.of("redis")).put("host", Value.of("localhost"))
                .put("port", Value.of(6379)).put("password", Value.of("password")).build();

        var config = new AttributeStoreConfiguration().toBackendConfig(node);

        assertThat(config.getType()).isEqualTo(BackendType.REDIS);
        assertThat(config.getRedis().getDatabase()).isZero();
    }
}
