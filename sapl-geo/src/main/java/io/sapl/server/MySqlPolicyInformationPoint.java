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
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.geo.connection.mysql.MySqlConnection;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@PolicyInformationPoint(name = MySqlPolicyInformationPoint.NAME, description = MySqlPolicyInformationPoint.DESCRIPTION)
public class MySqlPolicyInformationPoint {

    public static final String NAME = "mySql";

    public static final String DESCRIPTION = "PIP for geographical data from MySQL.";

    private final ObjectMapper mapper;

    private static final String MYSQL_DEFAULT_CONFIG = "MYSQL_DEFAULT_CONFIG";

    @Attribute(name = "geometry")
    public Flux<Val> geometry(Val leftHandValue, Map<String, Val> auth, @JsonObject Val variables) {

        return new MySqlConnection(auth.get(MYSQL_DEFAULT_CONFIG).get(), variables.get(), mapper)
                .connect(variables.get());

    }

    @Attribute(name = "geometry")
    public Flux<Val> geometry(Val leftHandValue, @JsonObject Val auth, @JsonObject Val variables) {

        return new MySqlConnection(auth.get(), variables.get(), mapper).connect(variables.get());

    }

    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(Map<String, Val> auth, @JsonObject Val variables) {

        return new MySqlConnection(auth.get(MYSQL_DEFAULT_CONFIG).get(), variables.get(), mapper)
                .connect(variables.get());

    }

    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(@JsonObject Val auth, @JsonObject Val variables) {

        return new MySqlConnection(auth.get(), variables.get(), mapper).connect(variables.get());

    }

}
