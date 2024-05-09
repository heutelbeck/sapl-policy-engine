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
import io.sapl.geo.connection.mysql.MySqlConnection;
import io.sapl.geo.connection.postgis.PostGisConnection;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@PolicyInformationPoint(name = PostGisPolicyInformationPoint.NAME, description = PostGisPolicyInformationPoint.DESCRIPTION)
public class PostGisPolicyInformationPoint {

    public static final String NAME = "postGis";

    public static final String DESCRIPTION = "PIP for geographical data from PostGIS.";

    private final ObjectMapper mapper;

   

    @Attribute(name = "geometry")
    public Flux<Val> geometry(Val leftHandValue, Map<String, Val> auth,  Val variables) {

        var a = leftHandValue;
        return new PostGisConnection(variables.get(), mapper)
                .connect(variables.get());

    }

    @Attribute(name = "geometry")
    public Flux<Val> geometry(Val leftHandValue, Val auth,  Val variables) {

        return new PostGisConnection(variables.get(), mapper)
                .connect(variables.get());

    }
    
    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(Map<String, Val> auth, Val variables) {

        return new PostGisConnection(variables.get(), mapper)
                .connect(variables.get());

    }
    
    @EnvironmentAttribute(name = "geometry")
    public Flux<Val> geometry(Val auth, Val variables) {

        return new PostGisConnection(variables.get(), mapper)
                .connect(variables.get());

    }

}
