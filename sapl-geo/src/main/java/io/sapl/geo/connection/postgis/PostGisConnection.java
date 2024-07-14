/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.geo.connection.postgis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.connection.shared.DatabaseConnection;

public class PostGisConnection extends DatabaseConnection {

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @param mapper   a {@link ObjectMapper}
     */
    public PostGisConnection(JsonNode auth, ObjectMapper mapper) {

        super(mapper,
                new PostgresqlConnectionFactory(
                        PostgresqlConnectionConfiguration.builder().username(getUser(auth)).password(getPassword(auth))
                                .host(getServer(auth)).port(getPort(auth)).database(getDataBase(auth)).build()));

    }

    protected static int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) {
            return requestSettings.findValue(PORT).asInt();
        } else {

            return 5432;
        }
    }

}
