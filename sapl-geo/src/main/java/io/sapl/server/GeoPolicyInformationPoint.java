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

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.geo.connection.traccar.TraccarSocketManager;
import reactor.core.publisher.Flux;

@Component
@PolicyInformationPoint(name = GeoPolicyInformationPoint.NAME, description = GeoPolicyInformationPoint.DESCRIPTION)
public class GeoPolicyInformationPoint {

    public static final String NAME = "geo";

    public static final String DESCRIPTION = "PIP for geographical data.";

   
    private final ObjectMapper mapper;

    public GeoPolicyInformationPoint(ObjectMapper _mapper) {

        this.mapper = _mapper;
    }

    @Attribute(name = "traccar")
    public Flux<Val> connectToTraccar(Val leftHandValue, Val variables) {

        try {
            return TraccarSocketManager.connectToTraccar(variables.get(), mapper);//.map(Val::of)
                    //.onErrorResume(e -> Flux.just(Val.error(e)));
        } catch (Exception e) {

            return Flux.just(Val.error(e));

        }
    }


    /*
     * public void disconnectTraccar(int deviceId) {
     *
     * traccarSockets.get(deviceId).disconnect(); }
     */
}
