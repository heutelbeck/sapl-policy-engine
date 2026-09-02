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

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeStoreConnectionFactoryTests {
    private static final String DEFAULT_HOSTNAME = "localhost";
    private static final String DEFAULT_USERNAME = "testuser";
    private static final String DEFAULT_PASSWORD = "testsecret";

    @Test
    @DisplayName("builds the Postgres options in the storage class")
    void whenPostgresPropertiesGivenThenOptionsContainAllValues() {
        var properties = new AttributeStorageProperties.Postgres();
        properties.setHost(DEFAULT_HOSTNAME);
        properties.setPort(5433);
        properties.setUsername(DEFAULT_USERNAME);
        properties.setPassword(DEFAULT_PASSWORD);
        properties.setDatabase("saplDb");

        var options = AttributeStoreConnectionFactory.buildPostgresOptions(properties);

        assertThat(options.getValue(HOST)).isEqualTo(DEFAULT_HOSTNAME);
        assertThat(options.getValue(PORT)).isEqualTo(5433);
        assertThat(options.getValue(USER)).isEqualTo(DEFAULT_USERNAME);
        assertThat(options.getValue(DATABASE)).isEqualTo("saplDb");
    }

    @Test
    @DisplayName("Mongo URI without a given username, password or auth source has no defaults in the connection string")
    void whenNoCredentialsThenMongoHasNoSetDefaults() {
        var properties = new AttributeStorageProperties.Mongo();
        properties.setHost(DEFAULT_HOSTNAME);
        properties.setPort(27018);
        properties.setDatabase("saplDb");

        var uri = AttributeStoreConnectionFactory.buildMongoConnectionUri(properties);

        assertThat(uri).isEqualTo(
                "mongodb://" + DEFAULT_HOSTNAME + ":27018/saplDb?serverSelectionTimeoutMS=3000&connectTimeoutMS=3000");
    }

    @Test
    @DisplayName("Mongo URI with username,password and authdb encodes it properly in the connection string")
    void whenCredentialsGivenThenMongoUriIsEncodedProperly() {
        var properties = new AttributeStorageProperties.Mongo();
        properties.setHost(DEFAULT_HOSTNAME);
        properties.setPort(27017);
        properties.setDatabase("saplDb");
        properties.setUsername(DEFAULT_USERNAME);
        properties.setPassword(DEFAULT_PASSWORD);
        properties.setAuthDatabase("admin");

        var uri = AttributeStoreConnectionFactory.buildMongoConnectionUri(properties);

        assertThat(uri).isEqualTo("mongodb://" + DEFAULT_USERNAME + ":" + DEFAULT_PASSWORD
                + "@localhost:27017/saplDb?authSource=admin&serverSelectionTimeoutMS=3000&connectTimeoutMS=3000");
    }

    @Test
    @DisplayName("MongoDB without a password fails")
    void whenUsernameSetWithoutPasswordThenMongoFails() {
        var properties = new AttributeStorageProperties.Mongo();
        properties.setUsername(DEFAULT_USERNAME);

        assertThatThrownBy(() -> AttributeStoreConnectionFactory.buildMongoConnectionUri(properties))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("io.sapl.attributes.mongo.password");
    }

    @Test
    @DisplayName("Redis URI without a password has no default password set")
    void whenNoPasswordThenRedisUriHasNoPassword() {
        var properties = new AttributeStorageProperties.Redis();
        properties.setHost(DEFAULT_HOSTNAME);
        properties.setPort(6380);
        properties.setDatabase(2);

        var uri         = AttributeStoreConnectionFactory.buildRedisUri(properties);
        var credentials = uri.getCredentialsProvider().resolveCredentials().block();

        assertThat(uri.getHost()).isEqualTo(DEFAULT_HOSTNAME);
        assertThat(uri.getPort()).isEqualTo(6380);
        assertThat(credentials.getPassword()).isNull();
    }

    @Test
    @DisplayName("Redis URI contains the password for the connection")
    void whenPasswordGivenThenRedisUriContainsIt() {
        var properties = new AttributeStorageProperties.Redis();
        properties.setHost(DEFAULT_HOSTNAME);
        properties.setPort(6379);
        properties.setPassword(DEFAULT_PASSWORD);

        var uri         = AttributeStoreConnectionFactory.buildRedisUri(properties);
        var credentials = uri.getCredentialsProvider().resolveCredentials().block();

        assertThat(credentials.getPassword()).isEqualTo(DEFAULT_PASSWORD.toCharArray());
    }
}
