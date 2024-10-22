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
package io.sapl.server;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.geo.databases.DataBaseTypes;
import io.sapl.geo.databases.DatabaseStreamQuery;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = MySqlPolicyInformationPoint.NAME, description = MySqlPolicyInformationPoint.DESCRIPTION)
public class MySqlPolicyInformationPoint {

    public static final String  NAME                 = "mySql";
    public static final String  DESCRIPTION          = "PIP for geographical data from MySQL.";
    private final ObjectMapper  mapper;
    private static final String MYSQL_DEFAULT_CONFIG = "MYSQL_DEFAULT_CONFIG";

    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(Map<String, Val> auth, @JsonObject Val variables) {
        return new DatabaseStreamQuery(auth.get(MYSQL_DEFAULT_CONFIG).get(), mapper, DataBaseTypes.MYSQL)
                .sendQuery(variables.get());
    }

    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(@JsonObject Val auth, @JsonObject Val variables) {
        return new DatabaseStreamQuery(auth.get(), mapper, DataBaseTypes.MYSQL).sendQuery(variables.get());
    }
}
