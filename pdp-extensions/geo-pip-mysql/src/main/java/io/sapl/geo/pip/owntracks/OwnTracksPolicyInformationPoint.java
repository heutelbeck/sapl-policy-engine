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
package io.sapl.geo.pip.owntracks;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@PolicyInformationPoint(name = OwnTracksPolicyInformationPoint.NAME, description = OwnTracksPolicyInformationPoint.DESCRIPTION)
public class OwnTracksPolicyInformationPoint {

    public static final String  NAME                     = "owntracks";
    public static final String  DESCRIPTION              = "PIP for geographical data.";
    private static final String OWNTRACKS_DEFAULT_CONFIG = "OWNTRACKS_DEFAULT_CONFIG";

    private final ObjectMapper mapper;

    @EnvironmentAttribute(name = "positionAndFences")
    public Flux<Val> positionAndFences(Map<String, Val> auth, @JsonObject Val variables)
            throws JsonProcessingException {
        return new OwnTracks(auth.get(OWNTRACKS_DEFAULT_CONFIG).get(), mapper)
                .getPositionWithInregions(variables.get());
    }

    @EnvironmentAttribute(name = "positionAndFences")
    public Flux<Val> positionAndFences(@JsonObject Val auth, @JsonObject Val variables) throws JsonProcessingException {
        return new OwnTracks(auth.get(), mapper).getPositionWithInregions(variables.get());
    }
}
