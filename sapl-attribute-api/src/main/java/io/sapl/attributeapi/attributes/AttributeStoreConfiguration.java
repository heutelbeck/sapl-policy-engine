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
import io.sapl.attributeapi.attributes.AttributeStorageProperties.BackendConfig;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;
import io.sapl.attributeapi.attributes.backend.RoutingAttributeStore;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
@Slf4j
@EnableConfigurationProperties(AttributeStorageProperties.class)
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
public class AttributeStoreConfiguration {
    private static final String WARN_REPOSITORY_BACKEND_UNAVAILABLE = "The attribute backend '{}' is unavailable at startup. Reconnect will be tried again later on first request: {}";

    @Bean
    Map<String, BackendHandle> attributeStoreByBackendConfig(AttributeStorageProperties properties) {
        var handles = new HashMap<String, BackendHandle>();

        // Create the backend stores
        for (var entry : properties.getBackends().entrySet()) {
            var name   = entry.getKey();
            var config = entry.getValue();
            var handle = new BackendHandle(() -> buildStore(config));

            try {
                handle.resolveOrThrow(name);
            } catch (AttributeBackendUnavailableException e) {
                log.warn(WARN_REPOSITORY_BACKEND_UNAVAILABLE, name, e.getMessage());
            }
            handles.put(name, handle);
        }
        return handles;
    }

    @Bean
    @ConditionalOnMissingBean(AttributeStore.class)
    AttributeStore routingAttributeStore(Map<String, BackendHandle> attributeBackendHandlesByName,
            AttributeStorageProperties properties) {
        return new RoutingAttributeStore(attributeBackendHandlesByName, properties.getTenants());
    }

    private AttributeStore buildStore(BackendConfig config) {
        return switch (config.getType()) {
        case POSTGRES -> buildPostgresStore(config.getPostgres());
        case MONGO    -> buildMongoStore(config.getMongo());
        case REDIS    -> buildRedisStore(config.getRedis());
        };
    }

    private AttributeStore buildPostgresStore(AttributeStorageProperties.Postgres postgres) {
        val connectionFactory = ConnectionFactories.get(AttributeStoreConnectionFactory.buildPostgresOptions(postgres));
        val client            = DatabaseClient.create(connectionFactory);
        return new PostgresAttributeStore(client, postgres.getTableName(), true);
    }

    private AttributeStore buildMongoStore(AttributeStorageProperties.Mongo mongo) {
        val connection = new ConnectionString(AttributeStoreConnectionFactory.buildMongoConnectionUri(mongo));
        val template   = new ReactiveMongoTemplate(MongoClients.create(connection), connection.getDatabase());

        // Quick ping to check if if the backend is available because the MongoDB driver never does it by it's own
        template.executeCommand("{ ping: 1 }").block();

        return new MongoAttributeStore(template, mongo.getCollectionName());
    }

    private AttributeStore buildRedisStore(AttributeStorageProperties.Redis redis) {
        val client = RedisClient.create(AttributeStoreConnectionFactory.buildRedisUri(redis));
        return new RedisAttributeStore(client);
    }
}
