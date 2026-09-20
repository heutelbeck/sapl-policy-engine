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

import io.lettuce.core.RedisURI;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.attributeapi.attributes.AttributeStorageProperties.Mongo;
import lombok.val;
import lombok.experimental.UtilityClass;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import com.mongodb.MongoCredential;

@UtilityClass
class AttributeStoreConnectionFactory {
    private static final String ERROR_MONGO_PASSWORD_REQUIRED = "Property io.sapl.attributes.mongo.username is set "
            + "but io.sapl.attributes.mongo.password is missing.";

    private static final String POSTGRES_DRIVER = "postgresql";

    ConnectionFactoryOptions buildPostgresOptions(AttributeStorageProperties.Postgres properties) {
        return ConnectionFactoryOptions.builder().option(DRIVER, POSTGRES_DRIVER).option(HOST, properties.getHost())
                .option(PORT, properties.getPort()).option(USER, properties.getUsername())
                .option(PASSWORD, properties.getPassword()).option(DATABASE, properties.getDatabase()).build();

    }

    String buildMongoConnectionUri(AttributeStorageProperties.Mongo properties) {
        val hasCredentials = properties.getUsername() != null && !properties.getUsername().isBlank();
        val authSource     = hasCredentials ? "?authSource=" + properties.getAuthDatabase() : "";
        val timeoutParams  = (authSource.isEmpty() ? "?" : "&") + "serverSelectionTimeoutMS=3000&connectTimeoutMS=3000";

        return "mongodb://" + properties.getHost() + ":" + properties.getPort() + "/" + properties.getDatabase()
                + authSource + timeoutParams;
    }

    @Nullable
    MongoCredential buildMongoCredential(Mongo properties) {
        val hasCredentials = properties.getUsername() != null && !properties.getUsername().isBlank();

        if (!hasCredentials) {
            return null;
        }

        val password = Objects.requireNonNull(properties.getPassword(), ERROR_MONGO_PASSWORD_REQUIRED);
        return MongoCredential.createCredential(properties.getUsername(), properties.getAuthDatabase(),
                password.toCharArray());
    }

    RedisURI buildRedisUri(AttributeStorageProperties.Redis properties) {
        val builder = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                .withDatabase(properties.getDatabase());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            builder.withPassword(properties.getPassword().toCharArray());
        }
        return builder.build();
    }
}
