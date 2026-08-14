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
package io.sapl.attributeapigui.client;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.attributeapi.AttributeApiApplication;
import io.sapl.attributeapigui.connection.ConnectionMode;
import io.sapl.attributeapigui.connection.ConnectionSettings;
import io.sapl.attributeapigui.connection.ConnectionSettingsHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AttributeApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "io.sapl.attribute-api.enabled=true", "io.sapl.attribute-api.allow-no-auth=true",
        "io.sapl.attribute-api.allow-basic-auth=false", "io.sapl.attribute-api.allow-api-key-auth=false",
        "io.sapl.attribute-api.allow-oauth2-auth=false", "io.sapl.attributes.storage=postgres" })

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
class AttributeApiClientIntegrationTests {
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

    private AttributeApiClient client;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("io.sapl.attributes.postgres.host", postgres::getHost);
        registry.add("io.sapl.attributes.postgres.port", () -> postgres.getMappedPort(5432));
        registry.add("io.sapl.attributes.postgres.database", postgres::getDatabaseName);
        registry.add("io.sapl.attributes.postgres.username", postgres::getUsername);
        registry.add("io.sapl.attributes.postgres.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        var config = PostgresqlConnectionConfiguration.builder().host(postgres.getHost())
                .port(postgres.getMappedPort(5432)).database(postgres.getDatabaseName())
                .username(postgres.getUsername()).password(postgres.getPassword()).build();

        var dbClient = DatabaseClient.create(new PostgresqlConnectionFactory(config));
        dbClient.sql(CREATE_TABLE).then().block();
        dbClient.sql("TRUNCATE TABLE attributes").then().block();

        var settings = new ConnectionSettings(ConnectionMode.NONE, "http://localhost:" + port, null, null, null);
        var holder   = mock(ConnectionSettingsHolder.class);
        when(holder.get()).thenReturn(settings);
        client = new AttributeApiClient(holder);
    }

    @Test
    @DisplayName("Publish attribute and get the published value in return")
    void whenAttributeIsPublishedThenGetReturnsCorrectValue() {
        var value = JsonNodeFactory.instance.stringNode("value");

        client.publishAttribute("test.entity", "sapl.test", value, 60L, List.of());
        var result = client.getAttribute("test.entity", "sapl.test", List.of());

        assertThat(result).contains("value");
    }

    @Test
    @DisplayName("Publish multiple attributes and get the correct amount via count function")
    void whenMultipleAttributesArePublishedThenCountIsCorrect() {
        for (int count = 0; count < 100; count++) {
            var value = JsonNodeFactory.instance.stringNode("value_" + count);
            client.publishAttribute("test.entity", "sapl.test" + count, value, 60L, List.of());
        }

        var result = client.getAttributeCount();
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("Publish multiple attributes and use limit and offset")
    void whenMultipleAttributesArePublishedThenLimitAndOffsetAreCorrect() {
        for (int count = 0; count < 100; count++) {
            var value = JsonNodeFactory.instance.stringNode("value_" + count);
            client.publishAttribute("test.entity", "sapl.test" + count, value, 60L, List.of());
        }

        var result = client.getAllAttributes(10, 0);
        assertThat(result).hasSize(10);
    }

    @Test
    @DisplayName("Publish global attribute and get the published value in return")
    void whenGlobalAttributeIsPublishedThenGetReturnsCorrectValue() {
        var value = JsonNodeFactory.instance.stringNode("value");

        client.publishAttribute(null, "sapl.test", value, 60L, List.of());
        var result = client.getAttribute(null, "sapl.test", List.of());

        assertThat(result).contains("value");
    }

}
