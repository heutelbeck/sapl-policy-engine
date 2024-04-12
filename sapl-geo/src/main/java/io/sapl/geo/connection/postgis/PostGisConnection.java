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

import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.DatabaseConnection;


import reactor.core.publisher.Flux;


public class PostGisConnection extends DatabaseConnection {

    private PostGisConnection(String user, String password, String serverName, int port, String dataBase,
            ObjectMapper mapper) {
       
    	super(mapper);
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder().username(user)
                .password(password).host(serverName).port(port).database(dataBase).build());

    }

    public static PostGisConnection getNew(String user, String password, String server, int port, String dataBase,
            ObjectMapper mapper) {

        return new PostGisConnection(user, password, server, port, dataBase, mapper);
    }

    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            instance = getNew(getUser(settings), getPassword(settings), getServer(settings), getPort(settings),
                    getDataBase(settings), mapper);
            var columns    = getColumns(settings, mapper);
            return instance
                    .getFlux(getResponseFormat(settings, mapper),
                            buildSql(getGeoColumn(settings), columns, getTable(settings), getWhere(settings)), columns,
                            getSingleResult(settings), getDefaultCRS(settings),
                            longOrDefault(settings, REPEAT_TIMES, DEFAULT_REPETITIONS),
                            longOrDefault(settings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS), 
                            getLatitudeFirst(settings))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e)));

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }

}
