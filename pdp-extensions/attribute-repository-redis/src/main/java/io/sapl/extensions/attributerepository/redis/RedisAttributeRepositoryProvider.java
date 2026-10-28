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
package io.sapl.extensions.attributerepository.redis;

import io.sapl.api.model.ObjectValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.extensions.attributerepository.AttributeRepositoryProvider;
import lombok.val;

import java.util.Objects;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;

public class RedisAttributeRepositoryProvider implements AttributeRepositoryProvider {
    private static String       TYPE           = "redis";
    private static final String HOST_FIELD     = "host";
    private static final String PORT_FIELD     = "port";
    private static final String DATABASE_FIELD = "database";
    private static final String PASSWORD_FIELD = "password";

    private static final String ERROR_MISSING_OR_INVALID_NUMBER = "attributerepository.%s is missing or not a valid number for pdpId '%s'.";
    private static final String ERROR_MISSING_STRING            = "attributerepository.%s is missing for pdpId '%s'.";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean validate(ObjectValue config) {
        return stringValue(config, HOST_FIELD) != null && numberValue(config, PORT_FIELD) != null;
    }

    @Override
    public AttributeRepository create(ObjectValue config, String pdpId) {
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

    private String stringValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof TextValue(String value) ? value : null;
    }

    private Integer numberValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof NumberValue number ? number.value().intValue() : null;
    }
}
