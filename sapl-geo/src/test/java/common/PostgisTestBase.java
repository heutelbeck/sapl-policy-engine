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
package common;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;

public abstract class PostgisTestBase extends DatabaseTestBase {

    @Container
    protected static final PostgreSQLContainer<?> postgisContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))
            .withUsername("test").withPassword("test").withDatabaseName("test");

    protected void commonSetUp() throws Exception {

        authTemplate = String.format(authenticationTemplate, postgisContainer.getUsername(),
                postgisContainer.getPassword(), postgisContainer.getHost(), postgisContainer.getMappedPort(5432),
                postgisContainer.getDatabaseName());

        template = String.format(template1, postgisContainer.getUsername(), postgisContainer.getPassword(),
                postgisContainer.getHost(), postgisContainer.getMappedPort(5432));

        templateAll = template.concat(templateAll1);

        templatePoint = template.concat(templatePoint1);

        var connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder().host(postgisContainer.getHost())
                        .port(postgisContainer.getMappedPort(5432)).database(postgisContainer.getDatabaseName())
                        .username(postgisContainer.getUsername()).password(postgisContainer.getPassword()).build());

        createTable(connectionFactory);
        insert(connectionFactory);
    }

}
