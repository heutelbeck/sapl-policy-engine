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
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.r2dbc.spi.ConnectionFactories;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.NumberValue;
import io.sapl.attributeapi.attributes.AttributeStorageProperties.BackendConfig;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;
import io.sapl.attributeapi.attributes.backend.RoutingAttributeStore;
import jakarta.annotation.Nullable;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Qualifier;
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

    private static final String ERROR_UNKNOWN_PDPID = "No attribute backend configured for pdpId '%s'.";
    private static final String FIELD_TYPE          = "type";
    private static final String FIELD_HOST          = "host";
    private static final String FIELD_PORT          = "port";
    private static final String FIELD_DATABASE      = "database";
    private static final String FIELD_USERNAME      = "username";
    private static final String FIELD_PASSWORD      = "password";
    private static final String FIELD_TABLE_NAME    = "tableName";
    private static final String FIELD_AUTH_DATABASE = "authDatabase";
    private static final String FIELD_COLLECTION    = "collectionName";

    private record CachedEntry(Value config, AttributeStore store) {}

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
    @ConditionalOnProperty(name = "io.sapl.attribute-api.embedded", havingValue = "false", matchIfMissing = true)
    AttributeStore routingAttributeStore(Map<String, BackendHandle> attributeBackendHandlesByName,
            AttributeStorageProperties properties) {
        return RoutingAttributeStore.forBackends(attributeBackendHandlesByName, properties.getTenants());
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attribute-api.embedded", havingValue = "true")
    AttributeStore embeddedRoutingAttributeStore(
            @Qualifier("attributeRepositoryConfigResolver") Function<String, Optional<Value>> configResolver) {
        var cache = new ConcurrentHashMap<String, CachedEntry>();

        Function<String, AttributeStore> resolver = pdpId -> {
            // 1. Get the current configuration via the bridge bean in the SAPL node. If missing, exception.
            var node = configResolver.apply(pdpId)
                    .orElseThrow(() -> new IllegalArgumentException(ERROR_UNKNOWN_PDPID.formatted(pdpId)));

            // 2. If store for this configuration exists and configuration didn't change: ignore!
            var cached = cache.get(pdpId);

            if (cached != null && cached.config().equals(node)) {
                return cached.store();
            }

            // 3. Otherwise: Build new store with the give configuration
            var store    = buildStore(toBackendConfig((ObjectValue) node));
            var previous = cache.put(pdpId, new CachedEntry(node, store));

            // 4. Close old stores if they are not existing anymore
            if (previous != null) {
                previous.store().close();
            }
            return store;
        };

        // Apply logic to the current RoutingAttributeStore class
        return new RoutingAttributeStore(resolver, pdpId -> {}, () -> cache.values().forEach(e -> e.store().close()));
    }

    AttributeStore buildStore(BackendConfig config) {
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
        val connectionString = new ConnectionString(AttributeStoreConnectionFactory.buildMongoConnectionUri(mongo));
        val credential       = AttributeStoreConnectionFactory.buildMongoCredential(mongo);
        val settingsBuilder  = MongoClientSettings.builder().applyConnectionString(connectionString);

        if (credential != null) {
            settingsBuilder.credential(credential);
        }

        val template = new ReactiveMongoTemplate(MongoClients.create(settingsBuilder.build()),
                connectionString.getDatabase());

        // Quick ping to check if if the backend is available because the MongoDB driver never does it by it's own
        template.executeCommand("{ ping: 1 }").block();

        return new MongoAttributeStore(template, mongo.getCollectionName());
    }

    private AttributeStore buildRedisStore(AttributeStorageProperties.Redis redis) {
        val client = RedisClient.create(AttributeStoreConnectionFactory.buildRedisUri(redis));
        return new RedisAttributeStore(client);
    }

    // Generated the backend configuration for the right backend
    BackendConfig toBackendConfig(ObjectValue node) {
        var type   = Objects.requireNonNull(text(node, FIELD_TYPE));
        var config = new BackendConfig();
        config.setType(AttributeStorageProperties.BackendType.valueOf(type.toUpperCase(Locale.ROOT)));

        switch (config.getType()) {
        case POSTGRES -> {
            var postgres = new AttributeStorageProperties.Postgres();
            postgres.setHost(text(node, FIELD_HOST));
            postgres.setPort(Objects.requireNonNull(number(node, FIELD_PORT)));
            postgres.setDatabase(text(node, FIELD_DATABASE));
            postgres.setUsername(text(node, FIELD_USERNAME));
            postgres.setPassword(text(node, FIELD_PASSWORD));
            Optional.ofNullable(text(node, FIELD_TABLE_NAME)).ifPresent(postgres::setTableName);
            config.setPostgres(postgres);
        }
        case MONGO    -> {
            var mongo    = new AttributeStorageProperties.Mongo();
            var database = text(node, FIELD_DATABASE);

            mongo.setHost(text(node, FIELD_HOST));
            mongo.setPort(Objects.requireNonNull(number(node, FIELD_PORT)));
            mongo.setUsername(text(node, FIELD_USERNAME));
            mongo.setDatabase(database);
            mongo.setAuthDatabase(Objects.requireNonNullElse(text(node, FIELD_AUTH_DATABASE), database));
            Optional.ofNullable(text(node, FIELD_COLLECTION)).ifPresent(mongo::setCollectionName);
            mongo.setPassword(text(node, FIELD_PASSWORD));
            config.setMongo(mongo);
        }
        case REDIS    -> {
            var redis = new AttributeStorageProperties.Redis();
            redis.setHost(text(node, FIELD_HOST));
            redis.setPort(Objects.requireNonNull(number(node, FIELD_PORT)));
            redis.setDatabase(Objects.requireNonNullElse(number(node, FIELD_DATABASE), 0));
            redis.setPassword(text(node, FIELD_PASSWORD));
            config.setRedis(redis);
        }
        }
        return config;
    }

    private static @Nullable String text(ObjectValue node, String key) {
        return node.get(key) instanceof TextValue(String value) ? value : null;
    }

    private static @Nullable Integer number(ObjectValue node, String key) {
        return node.get(key) instanceof NumberValue(BigDecimal value) ? value.intValue() : null;
    }
}
