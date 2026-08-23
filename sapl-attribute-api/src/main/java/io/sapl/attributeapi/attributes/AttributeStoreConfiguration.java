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

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
@EnableConfigurationProperties(AttributeStorageProperties.class)
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
public class AttributeStoreConfiguration {

    @Bean("attributeApiConnectionFactory")
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    ConnectionFactory attributeApiConnectionFactory(AttributeStorageProperties properties) {
        return ConnectionFactories.get(AttributeStoreConnectionFactory.buildPostgresOptions(properties.getPostgres()));
    }

    @Bean("attributeApiDatabaseClient")
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    DatabaseClient attributeApiDatabaseClient(
            @Qualifier("attributeApiConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    AttributeStore postgresAttributeStore(@Qualifier("attributeApiDatabaseClient") DatabaseClient client,
            AttributeStorageProperties properties) {
        return new PostgresAttributeStore(client, properties.getPostgres().getTableName(), true);
    }

    @Bean("attributeApiMongoTemplate")
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    ReactiveMongoTemplate attributeApiMongoTemplate(AttributeStorageProperties properties) {
        val connection = new ConnectionString(
                AttributeStoreConnectionFactory.buildMongoConnectionUri(properties.getMongo()));
        return new ReactiveMongoTemplate(MongoClients.create(connection), connection.getDatabase());
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    AttributeStore mongoAttributeStore(@Qualifier("attributeApiMongoTemplate") ReactiveMongoTemplate template,
            AttributeStorageProperties properties) {
        return new MongoAttributeStore(template, properties.getMongo().getCollectionName());
    }

    @Bean("attributeApiRedisClient")
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    RedisClient attributeApiRedisClient(AttributeStorageProperties properties) {
        return RedisClient.create(AttributeStoreConnectionFactory.buildRedisUri(properties.getRedis()));
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    AttributeStore redisAttributeStore(@Qualifier("attributeApiRedisClient") RedisClient client) {
        return new RedisAttributeStore(client);
    }
}
