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

import lombok.Data;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A storage class thats keeps the default properties for the Redis, Mongo or Postgres
 * attribute store. The default configuration is used when one of this configuration
 * items is missing in the configuration.
 */
@Data
@ConfigurationProperties(prefix = "io.sapl.attributes")
public class AttributeStorageProperties implements InitializingBean {
    private static final String POSTGRES_DEFAULT_HOSTNAME  = "localhost";
    private static final int    POSTGRES_DEFAULT_PORT      = 5432;
    private static final String POSTGRES_DEFAULT_DB        = "sapl";
    private static final String POSTGRES_DEFAULT_USERNAME  = "sapl";
    private static final String POSTGRES_DEFAULT_TABLENAME = "attributes";

    private static final String MONGO_DEFAULT_HOSTNAME   = "localhost";
    private static final int    MONGO_DEFAULT_PORT       = 27017;
    private static final String MONGO_DEFAULT_DB         = "sapl";
    private static final String MONGO_DEFAULT_AUTH_DB    = "admin";
    private static final String MONGO_DEFAULT_COLLECTION = "attributes";

    private static final String REDIS_DEFAULT_HOSTNAME = "localhost";
    private static final int    REDIS_DEFAULT_PORT     = 6379;
    private static final int    REDIS_DEFAULT_DB       = 0;

    // Used for the multi-tenant routing within the attribute api
    private Map<String, BackendConfig> backends = new HashMap<>();
    private Map<String, String>        tenants  = new HashMap<>();

    private static final String ERROR_MISSING_POSTGRES_PASSWORD = "io.sapl.attributes.storage=postgres but io.sapl.attributes.postgres.password is not set. Set it explicitly.";
    private static final String ERROR_UNKNOWN_TENANT_BACKEND    = "io.sapl.attributes.tenants.%s='%s' does not match any entry under io.sapl.attributes.backends.";
    private static final String ERROR_MISSING_BACKEND_TYPE      = "io.sapl.attributes.backends.%s.type is missing. Set it to one of: postgres, mongo, redis.";

    /**
     * Static class with Postgres default configuration
     */
    @Data
    public static class Postgres {
        private String host      = POSTGRES_DEFAULT_HOSTNAME;
        private int    port      = POSTGRES_DEFAULT_PORT;
        private String database  = POSTGRES_DEFAULT_DB;
        private String username  = POSTGRES_DEFAULT_USERNAME;
        private String password;
        private String tableName = POSTGRES_DEFAULT_TABLENAME;
    }

    /**
     * Static class with Mongo default configuration
     */
    @Data
    public static class Mongo {
        private String host           = MONGO_DEFAULT_HOSTNAME;
        private int    port           = MONGO_DEFAULT_PORT;
        private String database       = MONGO_DEFAULT_DB;
        private String username;
        private String password;
        private String authDatabase   = MONGO_DEFAULT_AUTH_DB;
        private String collectionName = MONGO_DEFAULT_COLLECTION;
    }

    /**
     * Static class with Redis default configuration
     */
    @Data
    public static class Redis {
        private String host     = REDIS_DEFAULT_HOSTNAME;
        private int    port     = REDIS_DEFAULT_PORT;
        private String password;
        private int    database = REDIS_DEFAULT_DB;
    }

    /**
     * Checks that are performed after the properties are set. Throws exceptions for an
     * unknown storage type or a missing Postgres password.
     *
     * @throws IllegalStateException
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        for (var entry : backends.entrySet()) {
            var name   = entry.getKey();
            var config = entry.getValue();

            if (config.getType() == null) {
                throw new IllegalStateException(ERROR_MISSING_BACKEND_TYPE.formatted(name));
            }
            if (config.getType() == BackendType.POSTGRES
                    && (config.getPostgres().getPassword() == null || config.getPostgres().getPassword().isBlank())) {
                throw new IllegalStateException(ERROR_MISSING_POSTGRES_PASSWORD.formatted(name, name));
            }
        }

        for (var entry : tenants.entrySet()) {
            if (!backends.containsKey(entry.getValue())) {
                throw new IllegalStateException(
                        ERROR_UNKNOWN_TENANT_BACKEND.formatted(entry.getKey(), entry.getValue()));
            }
        }
    }

    @Data
    public static class BackendConfig {
        private BackendType type;
        private Postgres    postgres = new Postgres();
        private Mongo       mongo    = new Mongo();
        private Redis       redis    = new Redis();
    }

    public enum BackendType {
        POSTGRES,
        MONGO,
        REDIS
    }
}
