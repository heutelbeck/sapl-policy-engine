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
package io.sapl.extensions.attributerepository.mongo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClients;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.extensions.attributerepository.AttributeRepositoryProvider;
import lombok.val;

/**
 * Provider for a MongoDB attribute repository with the type {@code mongo}. It's registered
 * in {@code META-INF/services/io.sapl.extensions.attributerepository.AttributeRepositoryProvider}
 * and found by {@link io.sapl.extensions.attributerepository.AttributeRepositoryFactory} via
 * {@link java.util.ServiceLoader}.
 * <p>
 * Required fields are {@code host}, {@code port} and {@code database}. The optional fields
 * {@code username}, {@code password}, {@code authDatabase} defaults to {@code attributes}
 * and {@code collectionName} defaults to {@code attributes}.
 *
 * @since 4.2.0
 */
public class MongoAttributeRepositoryProvider implements AttributeRepositoryProvider {
    private static final String TYPE = "mongo";

    private static final String ERROR_INVALID_IDENTIFIER        = "attributerepository.%s='%s' for pdpId '%s' is invalid. "
            + "Must match ^[A-Za-z_][A-Za-z0-9_]*$.";
    private static final String ERROR_MISSING_STRING            = "attributerepository.%s is missing for pdpId '%s'.";
    private static final String ERROR_MISSING_OR_INVALID_NUMBER = "attributerepository.%s is missing or not a valid number for pdpId '%s'.";

    private static final String DEFAULT_NAME     = "attributes";
    private static final String HOST_FIELD       = "host";
    private static final String PORT_FIELD       = "port";
    private static final String PASSWORD_FIELD   = "password";
    private static final String DATABASE_FIELD   = "database";
    private static final String USERNAME_FIELD   = "username";
    private static final String AUTH_DB_FIELD    = "authDatabase";
    private static final String COLLECTION_FIELD = "collectionName";
    private static final String REGEX_PATTERN    = "[A-Za-z_][A-Za-z0-9_]*";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean validate(ObjectValue config) {
        if (stringValue(config, HOST_FIELD) == null || numberValue(config, PORT_FIELD) == null
                || stringValue(config, DATABASE_FIELD) == null) {
            return false;
        }
        val username       = stringValue(config, USERNAME_FIELD);
        val hasCredentials = username != null && !username.isBlank();
        return !hasCredentials || stringValue(config, PASSWORD_FIELD) != null;
    }

    /**
     * Creates the MongoDB repository with the given fields and the reactive Mongo driver.
     *
     * @param config The configuration block of the extension.
     * @param pdpId The pdp id the repository is built for.
     * @return The MongoDB attribute repository.
     * @throws NullPointerException if a required field is missing. The message names the field and the pdp id.
     * @throws IllegalStateException if {@code collectionName}
     */
    @Override
    public AttributeRepository create(ObjectValue config, String pdpId) {
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
        val effectiveAuthDb = authDb != null ? authDb : database;
        val authSourceQuery = hasCredentials ? "?authSource=" + effectiveAuthDb : "";
        val cs              = new ConnectionString(
                "mongodb://" + credentials + host + ":" + port + "/" + database + authSourceQuery);
        val clientSettings  = MongoClientSettings.builder().applyConnectionString(cs)
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToServerSettings(b -> b.heartbeatFrequency(500, TimeUnit.MILLISECONDS)).build();

        return new MongoAttributeRepository(new ReactiveMongoTemplate(MongoClients.create(clientSettings),
                Objects.requireNonNull(cs.getDatabase())), pdpId, collection);
    }

    private String stringValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof TextValue(String value) ? value : null;
    }

    private Integer numberValue(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof NumberValue number ? number.value().intValue() : null;
    }

    private String validIdentifier(ObjectValue config, String key, String pdpId) {
        val value = Objects.requireNonNullElse(stringValue(config, key), DEFAULT_NAME);
        if (!value.matches(REGEX_PATTERN)) {
            throw new IllegalStateException(ERROR_INVALID_IDENTIFIER.formatted(key, value, pdpId));
        }
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
