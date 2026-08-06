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
    private String storage;

    private Postgres postgres = new Postgres();
    private Mongo    mongo    = new Mongo();
    private Redis    redis    = new Redis();

    private static final String ERROR_UNKNOWN_STORAGE           = "io.sapl.attributes.storage='%s' is not a supported value. Set it to one of: postgres, mongo, redis, none.";
    private static final String ERROR_MISSING_POSTGRES_PASSWORD = "io.sapl.attributes.storage=postgres but io.sapl.attributes.postgres.password is not set. Set it explicitly.";

    @Data
    public static class Postgres {
        private String host      = "localhost";
        private int    port      = 5432;
        private String database  = "sapl";
        private String username  = "sapl";
        private String password;
        private String tableName = "attributes";
    }

    @Data
    public static class Mongo {
        private String host           = "localhost";
        private int    port           = 27017;
        private String database       = "sapl";
        private String username;
        private String password;
        private String authDatabase   = "admin";
        private String collectionName = "attributes";
    }

    @Data
    public static class Redis {
        private String host     = "localhost";
        private int    port     = 6379;
        private String password;
        private int    database = 0;
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
