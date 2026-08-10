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
import java.util.Objects;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@UtilityClass
public class AttributeRepositoryFactory {

    private static final String DEFAULT_NAME             = "attributes";
    private static final String ERROR_INVALID_IDENTIFIER = "attributeRepository.%s='%s' for pdpId '%s' is invalid. "
            + "Must match ^[A-Za-z_][A-Za-z0-9_]*$.";
    private static final String ERROR_UNKNOWN_TYPE       = "attributeRepository.type='%s' for pdpId '%s' is not "
            + "supported. Set it to one of: postgres, mongo, redis.";

    private static final String HOST_FIELD     = "host";
    private static final String PORT_FIELD     = "port";
    private static final String PASSWORD_FIELD = "password";
    private static final String DATABASE_FIELD = "database";

    // yield --> value goes back from a case to the switch expression and not "outside" the method like a return.
    // config, pdpId parameter is set via AttributeConfiguration class
    public AttributeRepository create(ObjectValue config, String pdpId) {
        PdpIdValidator.validatePdpId(pdpId);
        val type = str(config, "type");

        return switch (type != null ? type : "") {

        case "postgres" -> {
            val cf    = ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                    .option(HOST, Objects.requireNonNull(str(config, HOST_FIELD))).option(PORT, num(config, PORT_FIELD))
                    .option(USER, Objects.requireNonNull(str(config, "username")))
                    .option(PASSWORD, Objects.requireNonNull(str(config, PASSWORD_FIELD)))
                    .option(DATABASE, Objects.requireNonNull(str(config, DATABASE_FIELD))).build());
            val table = validIdentifier(config, "tableName", pdpId);

            yield new PostgresAttributeRepository(DatabaseClient.create(cf), cf, pdpId, table);
        }

        case "mongo" -> {
            val host            = str(config, HOST_FIELD);
            val port            = num(config, PORT_FIELD);
            val database        = str(config, DATABASE_FIELD);
            val username        = str(config, "username");
            val password        = str(config, PASSWORD_FIELD);
            val authDb          = str(config, "authDatabase");
            val collection      = validIdentifier(config, "collectionName", pdpId);
            val creds           = username == null || username.isBlank() ? ""
                    : encode(username) + ":" + encode(password) + "@";
            val effectiveAuthDb = authDb != null ? authDb : database;
            val auth            = username == null || username.isBlank() ? "" : "?authSource=" + effectiveAuthDb;
            val cs              = new ConnectionString(
                    "mongodb://" + creds + host + ":" + port + "/" + database + auth);

            yield new MongoAttributeRepository(
                    new ReactiveMongoTemplate(MongoClients.create(cs), Objects.requireNonNull(cs.getDatabase())), pdpId,
                    collection);
        }

        case "redis" -> {
            val host     = str(config, HOST_FIELD);
            val port     = num(config, PORT_FIELD);
            val password = str(config, PASSWORD_FIELD);
            val db       = num(config, DATABASE_FIELD);
            val builder  = RedisURI.Builder.redis(host, port).withDatabase(db);

            if (password != null && !password.isBlank()) {
                builder.withPassword(password.toCharArray());
            }

            yield new RedisAttributeRepository(RedisClient.create(builder.build()), pdpId, db);
        }

        default -> throw new IllegalStateException(ERROR_UNKNOWN_TYPE.formatted(type, pdpId));
        };
    }

    private String validIdentifier(ObjectValue config, String key, String pdpId) {
        val value = Objects.requireNonNullElse(str(config, key), DEFAULT_NAME);
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException(ERROR_INVALID_IDENTIFIER.formatted(key, value, pdpId));
        }
        return value;
    }

    private String str(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof TextValue(String value) ? value : null;
    }

    private int num(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof NumberValue(java.math.BigDecimal value) ? value.intValue() : 0;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
