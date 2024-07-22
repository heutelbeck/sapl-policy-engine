/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.geo.common;

import java.time.ZoneId;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;

public abstract class MySqlTestBase extends DatabaseTestBase {

    @Container
    protected static final MySQLContainer<?> mySqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.2.0")) // 8.3.0
                                                                                                                         // is
                                                                                                                         // buggy
            .withUsername("test").withPassword("test").withDatabaseName("test");

    protected void commonSetUp() {

        authTemplate = String.format(authenticationTemplate, mySqlContainer.getUsername(), mySqlContainer.getPassword(),
                mySqlContainer.getHost(), mySqlContainer.getMappedPort(3306), mySqlContainer.getDatabaseName());

        templateAll = template.concat(templateAll1);

        templatePoint = template.concat(templatePoint1);

        var connectionFactory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder()
                .username(mySqlContainer.getUsername()).password(mySqlContainer.getPassword())
                .host(mySqlContainer.getHost()).port(mySqlContainer.getMappedPort(3306))
                .database(mySqlContainer.getDatabaseName()).serverZoneId(ZoneId.of("UTC")).build());

        createTable(connectionFactory);
        insert(connectionFactory);
    }
}
