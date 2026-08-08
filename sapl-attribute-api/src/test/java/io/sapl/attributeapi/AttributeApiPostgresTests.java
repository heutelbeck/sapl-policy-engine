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
package io.sapl.attributeapi;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;
import lombok.val;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = AttributeApiApplication.class, properties = { "io.sapl.attribute-api.enabled=true",
        "io.sapl.attribute-api.allow-no-auth=true", "io.sapl.attribute-api.allow-basic-auth=false",
        "io.sapl.attribute-api.allow-api-key-auth=false", "io.sapl.attribute-api.allow-oauth2-auth=false",
        "io.sapl.attributes.storage=none" })
@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@Import(AttributeApiPostgresTests.Config.class)
class AttributeApiPostgresTests extends AbstractAttributeApiTests {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS attributes (
                pdp_id     TEXT        NOT NULL,
                name       TEXT        NOT NULL,
                entity     JSONB,
                arguments  JSONB       NOT NULL DEFAULT '[]',
                value      JSONB       NOT NULL,
                expires_at TIMESTAMPTZ,
                CONSTRAINT attributes_pdp_id_name_entity_arguments_key
                    UNIQUE NULLS NOT DISTINCT (pdp_id, name, entity, arguments)
            )
            """;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Override
    protected void cleanRepository() {
        val client = DatabaseClient.create(connectionFactory());
        client.sql(CREATE_TABLE).then().block();
        client.sql("TRUNCATE TABLE attributes").then().block();
    }

    private static PostgresqlConnectionFactory connectionFactory() {
        val config = PostgresqlConnectionConfiguration.builder().host(postgres.getHost())
                .port(postgres.getMappedPort(5432)).database(postgres.getDatabaseName())
                .username(postgres.getUsername()).password(postgres.getPassword()).build();
        return new PostgresqlConnectionFactory(config);
    }

    @TestConfiguration
    static class Config {
        @Bean
        AttributeStore attributeStore() {
            val client = DatabaseClient.create(connectionFactory());
            client.sql(CREATE_TABLE).then().block();
            return new PostgresAttributeStore(client, "attributes");
        }
    }
}
