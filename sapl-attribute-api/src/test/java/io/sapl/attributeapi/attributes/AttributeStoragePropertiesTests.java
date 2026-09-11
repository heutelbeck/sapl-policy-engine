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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeStoragePropertiesTests {
    @Test
    @DisplayName("When the backend type is missing then throw a general exception.")
    void whenBackendTypeIsMissingThenThrowException() {
        var properties = new AttributeStorageProperties();
        properties.getBackends().put("custom-backend", new AttributeStorageProperties.BackendConfig());

        assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type is missing. Set it to one of: postgres, mongo, redis.");
    }

    @Test
    @DisplayName("When the postgres password is missing then throw an general exception")
    void whenPostgreSQLIsMissingInBackendTypeThenThrowException() {
        var config = new AttributeStorageProperties.BackendConfig();
        config.setType(AttributeStorageProperties.BackendType.POSTGRES);

        var properties = new AttributeStorageProperties();
        properties.getBackends().put("custom-backend", config);

        assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "io.sapl.attributes.storage=postgres but io.sapl.attributes.postgres.password is not set. Set it explicitly.");
    }

    @Test
    @DisplayName("When the tenant does not exist then throw an exception")
    void whenTenantIsUnknownThenThrowException() {
        var config = new AttributeStorageProperties.BackendConfig();
        config.setType(AttributeStorageProperties.BackendType.REDIS);

        var properties = new AttributeStorageProperties();
        properties.getBackends().put("custom-backend", config);
        properties.getTenants().put("custom-tenant", "non-existing-tenant");

        assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match any entry under io.sapl.attributes.backends");
    }

    @Test
    @DisplayName("When a valid configuration is given then throw no exception and build it.")
    void whenConfigIsValidThenNoExceptionIsThrown() {
        var config = new AttributeStorageProperties.BackendConfig();
        config.setType(AttributeStorageProperties.BackendType.REDIS);

        var properties = new AttributeStorageProperties();
        properties.getBackends().put("custom-backend", config);
        properties.getTenants().put("custom-tenant", "custom-backend");

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
    }
}
