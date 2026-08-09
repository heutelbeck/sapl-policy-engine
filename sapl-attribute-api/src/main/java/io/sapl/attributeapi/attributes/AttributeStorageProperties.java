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

import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    private String storage;

    private Postgres postgres = new Postgres();
    private Mongo    mongo    = new Mongo();
    private Redis    redis    = new Redis();

    private static final String ERROR_UNKNOWN_STORAGE           = "io.sapl.attributes.storage='%s' is not a supported value. Set it to one of: postgres, mongo, redis, none.";
    private static final String ERROR_MISSING_POSTGRES_PASSWORD = "io.sapl.attributes.storage=postgres but io.sapl.attributes.postgres.password is not set. Set it explicitly.";

    @Data
    public static class Postgres {
        private String host      = POSTGRES_DEFAULT_HOSTNAME;
        private int    port      = POSTGRES_DEFAULT_PORT;
        private String database  = POSTGRES_DEFAULT_DB;
        private String username  = POSTGRES_DEFAULT_USERNAME;
        private String password;
        private String tableName = POSTGRES_DEFAULT_TABLENAME;
    }

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

    @Data
    public static class Redis {
        private String host     = REDIS_DEFAULT_HOSTNAME;
        private int    port     = REDIS_DEFAULT_PORT;
        private String password;
        private int    database = REDIS_DEFAULT_DB;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (storage == null || !Set.of("postgres", "mongo", "redis", "none").contains(storage)) {
            throw new IllegalStateException(ERROR_UNKNOWN_STORAGE.formatted(storage));
        }
        if ("postgres".equals(storage) && (postgres.getPassword() == null || postgres.getPassword().isBlank())) {
            throw new IllegalStateException(ERROR_MISSING_POSTGRES_PASSWORD);
        }
    }
}
