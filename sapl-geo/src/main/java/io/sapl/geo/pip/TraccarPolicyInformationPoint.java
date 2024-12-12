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
package io.sapl.geo.pip;

import java.net.URISyntaxException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.geo.pip.traccar.TraccarGeofences;
import io.sapl.geo.pip.traccar.TraccarPositions;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TraccarPolicyInformationPoint.NAME, description = TraccarPolicyInformationPoint.DESCRIPTION)
public class TraccarPolicyInformationPoint {

    public static final String  NAME                   = "traccar";
    public static final String  DESCRIPTION            = "PIP for geographical data from traccar.";
    private static final String TRACCAR_DEFAULT_CONFIG = "TRACCAR_DEFAULT_CONFIG";
    private final ObjectMapper  mapper;

    @EnvironmentAttribute(name = "position")
    public Flux<Val> position(Map<String, Val> auth, @JsonObject Val variables) throws URISyntaxException {
        return new TraccarPositions(auth.get(TRACCAR_DEFAULT_CONFIG).get(), mapper).getPositions(variables.get());
    }

    @EnvironmentAttribute(name = "position")
    public Flux<Val> position(@JsonObject Val auth, @JsonObject Val variables) throws URISyntaxException {
        return new TraccarPositions(auth.get(), mapper).getPositions(variables.get());
    }

    @EnvironmentAttribute(name = "geofences")
    public Flux<Val> geofences(Map<String, Val> auth, @JsonObject Val variables) throws URISyntaxException {
        return new TraccarGeofences(auth.get(TRACCAR_DEFAULT_CONFIG).get(), mapper).getGeofences(variables.get());
    }

    @EnvironmentAttribute(name = "geofences")
    public Flux<Val> geofences(@JsonObject Val auth, @JsonObject Val variables) throws URISyntaxException {
        return new TraccarGeofences(auth.get(), mapper).getGeofences(variables.get());
    }
}
