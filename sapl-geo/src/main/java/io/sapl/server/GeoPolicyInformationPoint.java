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

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.geo.connection.mysql.MySqlConnection;
import io.sapl.geo.connection.owntracks.OwnTracksConnection;
import io.sapl.geo.connection.postgis.PostGisConnection;
import io.sapl.geo.connection.traccar.TraccarConnection;
import io.sapl.geo.fileimport.FileLoader;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@PolicyInformationPoint(name = GeoPolicyInformationPoint.NAME, description = GeoPolicyInformationPoint.DESCRIPTION)
public class GeoPolicyInformationPoint {

    public static final String NAME = "geo";

    public static final String DESCRIPTION = "PIP for geographical data.";

    private final ObjectMapper mapper;

    @Attribute(name = "traccar")
    public Flux<Val> connectToTraccar(Val leftHandValue, Val variables) {

        return new TraccarConnection(mapper).connect(variables.get());

    }

    @EnvironmentAttribute(name = "traccar")
    public Flux<Val> connectToTraccar(Val variables) {

        return new TraccarConnection(mapper).connect(variables.get());

    }

    @Attribute(name = "ownTracks")
    public Flux<Val> connectToOwnTracks(Val leftHandValue, Val variables) {

        return new OwnTracksConnection(mapper).connect(variables.get());

    }

    @EnvironmentAttribute(name = "ownTracks")
    public Flux<Val> connectToOwnTracks(Val variables) {

        return new OwnTracksConnection(mapper).connect(variables.get());

    }

//    @Attribute(name = "postGIS")
//    public Flux<Val> connectToPostGIS(Val leftHandValue, Val variables) {
//        return new PostGisConnection(variables.get(), new ObjectMapper()).connect(variables.get());
//
//    }
//
//    @EnvironmentAttribute(name = "postGIS")
//    public Flux<Val> connectToPostGIS(Val variables) {
//
//        return new PostGisConnection(variables.get(), new ObjectMapper()).connect(variables.get());
//
//    }

//    @Attribute(name = "mySQL")
//    public Flux<Val> connectToMySQL(Val leftHandValue, Val variables) {
//
//        return new MySqlConnection(variables.get(), new ObjectMapper()).connect(variables.get());
//
//    }
//
//    @EnvironmentAttribute(name = "mySQL")
//    public Flux<Val> connectToMySQL(Val variables) {
//
//        return new MySqlConnection(variables.get(), new ObjectMapper()).connect(variables.get());
//
//    }

    @Attribute(name = "file")
    public Flux<Val> loadFile(Val leftHandValue, Val variables) {

        return new FileLoader(mapper).connect(variables.get());

    }

    @EnvironmentAttribute(name = "file")
    public Flux<Val> loadFile(Val variables) {

        return new FileLoader(mapper).connect(variables.get());

    }

}
