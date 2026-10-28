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
package io.sapl.extensions.attributerepository.postgres;

import io.sapl.api.model.ObjectValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.extensions.attributerepository.AttributeRepositoryProvider;
import lombok.val;
import io.sapl.api.model.TextValue;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.r2dbc.core.DatabaseClient;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import java.util.Objects;
import io.sapl.api.model.NumberValue;

/**
 * Provider for a PostgreSQL attribute repository with the type {@code postgres}. It's registered
 * in {@code META-INF/services/io.sapl.extensions.attributerepository.AttributeRepositoryProvider}
 * and found by {@link io.sapl.extensions.attributerepository.AttributeRepositoryFactory} via
 * {@link java.util.ServiceLoader}.
 * <p>
 * Required fields are {@code host}, {@code port}, {@code username}, {@code password} and {@code database}. The
 * optional field {@code tableName} defaults to {@code attributes} and must be a valid SQL identifier.
 *
 * @since 4.2.0
 */
public class PostgresAttributeRepositoryProvider implements AttributeRepositoryProvider {
    private static final String TYPE = "postgres";

    private static final String ERROR_INVALID_IDENTIFIER        = "attributerepository.%s='%s' for pdpId '%s' is invalid. "
            + "Must match ^[A-Za-z_][A-Za-z0-9_]*$.";
    private static final String ERROR_MISSING_STRING            = "attributerepository.%s is missing for pdpId '%s'.";
    private static final String ERROR_MISSING_OR_INVALID_NUMBER = "attributerepository.%s is missing or not a valid number for pdpId '%s'.";

    private static final String DEFAULT_NAME    = "attributes";
    private static final String HOST_FIELD      = "host";
    private static final String PORT_FIELD      = "port";
    private static final String PASSWORD_FIELD  = "password";
    private static final String DATABASE_FIELD  = "database";
    private static final String USERNAME_FIELD  = "username";
    private static final String TABLENAME_FIELD = "tableName";

    private static final String POSTGRES_DRIVER = "postgresql";

    private static final String REGEX_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean validate(ObjectValue config) {
        return stringValue(config, HOST_FIELD) != null && numberValue(config, PORT_FIELD) != null
                && stringValue(config, USERNAME_FIELD) != null && stringValue(config, PASSWORD_FIELD) != null
                && stringValue(config, DATABASE_FIELD) != null;
    }

    /**
     * Creates the PostgreSQL repository with the given fields and the R2DBC driver.
     *
     * @param config The configuration block of the extension.
     * @param pdpId The pdp id the repository is built for.
     * @return The PostgreSQL attribute repository.
     * @throws NullPointerException if a required field is missing. The message names the field and the pdp id.
     * @throws IllegalStateException if {@code tableName} is not a valid SQL identifier.
     */
    @Override
    public AttributeRepository create(ObjectValue config, String pdpId) {
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
                ConnectionFactoryOptions.builder().option(DRIVER, POSTGRES_DRIVER).option(HOST, host).option(PORT, port)
                        .option(USER, username).option(PASSWORD, password).option(DATABASE, database).build());

        val table = validIdentifier(config, TABLENAME_FIELD, pdpId);

        return new PostgresAttributeRepository(DatabaseClient.create(connectionFactory), connectionFactory, pdpId,
                table);
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

}
