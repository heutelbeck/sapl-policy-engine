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
package io.sapl.pdp.configuration;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.MongoAttributeRepository;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.RedisAttributeRepository;
import io.sapl.pdp.configuration.source.PdpIdValidator;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@UtilityClass
public class AttributeRepositoryFactory {
    private static final String ERROR_INVALID_IDENTIFIER        = "attributeRepository.%s='%s' for pdpId '%s' is invalid. "
            + "Must match ^[A-Za-z_][A-Za-z0-9_]*$.";
    private static final String ERROR_UNKNOWN_TYPE              = "attributeRepository.type='%s' for pdpId '%s' is not "
            + "supported. Set it to one of: postgres, mongo, redis.";
    private static final String ERROR_MISSING_STRING            = "attributeRepository.%s is missing for pdpId '%s'.";
    private static final String ERROR_MISSING_OR_INVALID_NUMBER = "attributeRepository.%s is missing or not a valid number for pdpId '%s'.";

    private static final String DEFAULT_NAME     = "attributes";
    private static final String HOST_FIELD       = "host";
    private static final String PORT_FIELD       = "port";
    private static final String PASSWORD_FIELD   = "password";
    private static final String DATABASE_FIELD   = "database";
    private static final String USERNAME_FIELD   = "username";
    private static final String AUTH_DB_FIELD    = "authDatabase";
    private static final String TABLENAME_FIELD  = "tableName";
    private static final String COLLECTION_FIELD = "collectionName";
    private static final String TYPE_FIELD       = "type";

    private static final String REGEX_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private enum RepositoryType {
        POSTGRES,
        MONGO,
        REDIS;
    }

    // yield --> value goes back from a case to the switch expression and not "outside" the method like a return.
    // config, pdpId parameter is set via AttributeConfiguration class
    public AttributeRepository create(ObjectValue config, String pdpId) {
        PdpIdValidator.validatePdpId(pdpId);
        val type = stringValue(config, TYPE_FIELD);

        RepositoryType repositoryType;
        try {
            repositoryType = RepositoryType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(ERROR_UNKNOWN_TYPE.formatted(type, pdpId));
        }

        return switch (repositoryType) {
        case POSTGRES -> createPostgresRepository(config, pdpId);
        case MONGO    -> createMongoRepository(config, pdpId);
        case REDIS    -> createRedisRepository(config, pdpId);
        };
    }

    private AttributeRepository createPostgresRepository(ObjectValue config, String pdpId) {
        val host     = Objects.requireNonNull(stringValue(config, HOST_FIELD),
                () -> ERROR_MISSING_STRING.formatted(HOST_FIELD, pdpId));
        val port     = Objects.requireNonNull(numberValue(config, PORT_FIELD),
                () -> ERROR_MISSING_OR_INVALID_NUMBER.formatted(PORT_FIELD, pdpId));
        val username = Objects.requireNonNull(stringValue(config, USERNAME_FIELD),
                () -> ERROR_MISSING_STRING.formatted(USERNAME_FIELD, pdpId));
        val password = Objects.requireNonNull(stringValue(config, PASSWORD_FIELD),
                () -> ERROR_MISSING_STRING.formatted(PASSWORD_FIELD, pdpId));
        val database = Objects.requireNonNull(stringValue(config, DATABASE_FIELD),
                () -> ERROR_MISSING_STRING.formatted(DATABASE_FIELD, pdpId));

        val connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions.builder().option(DRIVER, "postgresql").option(HOST, host).option(PORT, port)
                        .option(USER, username).option(PASSWORD, password).option(DATABASE, database).build());

        val table = validIdentifier(config, TABLENAME_FIELD, pdpId);

        return new PostgresAttributeRepository(DatabaseClient.create(connectionFactory), connectionFactory, pdpId,
                table);
    }

    private AttributeRepository createMongoRepository(ObjectValue config, String pdpId) {
        val host       = Objects.requireNonNull(stringValue(config, HOST_FIELD),
                () -> ERROR_MISSING_STRING.formatted(HOST_FIELD, pdpId));
        val port       = Objects.requireNonNull(numberValue(config, PORT_FIELD),
                () -> ERROR_MISSING_OR_INVALID_NUMBER.formatted(PORT_FIELD, pdpId));
        val database   = Objects.requireNonNull(stringValue(config, DATABASE_FIELD),
                () -> ERROR_MISSING_STRING.formatted(DATABASE_FIELD, pdpId));
        val username   = stringValue(config, USERNAME_FIELD);
        val authDb     = stringValue(config, AUTH_DB_FIELD);
        val collection = validIdentifier(config, COLLECTION_FIELD, pdpId);

        val hasCredentials  = username != null && !username.isBlank();
        val password        = hasCredentials ? Objects.requireNonNull(stringValue(config, PASSWORD_FIELD),
                () -> ERROR_MISSING_STRING.formatted(PASSWORD_FIELD, pdpId)) : null;
        val credentials     = hasCredentials ? encode(username) + ":" + encode(password) + "@" : "";
        val authSourceQuery = hasCredentials ? "?authSource=" + (authDb != null ? authDb : database) : "";
        val cs              = new ConnectionString(
                "mongodb://" + credentials + host + ":" + port + "/" + database + authSourceQuery);

        return new MongoAttributeRepository(
                new ReactiveMongoTemplate(MongoClients.create(cs), Objects.requireNonNull(cs.getDatabase())), pdpId,
                collection);
    }

    private AttributeRepository createRedisRepository(ObjectValue config, String pdpId) {
        val host     = Objects.requireNonNull(stringValue(config, HOST_FIELD),
                () -> ERROR_MISSING_STRING.formatted(HOST_FIELD, pdpId));
        val port     = Objects.requireNonNull(numberValue(config, PORT_FIELD),
                () -> ERROR_MISSING_OR_INVALID_NUMBER.formatted(PORT_FIELD, pdpId));
        val password = stringValue(config, PASSWORD_FIELD);
        // Redis' own default DB index is 0, so an absent field is a valid choice here, unlike a missing port.
        val db = Objects.requireNonNullElse(numberValue(config, DATABASE_FIELD), 0);

        val builder = RedisURI.Builder.redis(host, port).withDatabase(db);
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }

        return new RedisAttributeRepository(RedisClient.create(builder.build()), pdpId, db);
    }

    private String validIdentifier(ObjectValue config, String key, String pdpId) {
        val value = Objects.requireNonNullElse(stringValue(config, key), DEFAULT_NAME);
        if (!value.matches(REGEX_PATTERN)) {
            throw new IllegalStateException(ERROR_INVALID_IDENTIFIER.formatted(key, value, pdpId));
        }
        return value;
    }

    private String stringValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof TextValue(String value) ? value : null;
    }

    private Integer numberValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof NumberValue(java.math.BigDecimal value) ? value.intValue() : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
