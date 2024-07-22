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
package io.sapl.geo.traccar;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geo.shared.GeoMapper;
import io.sapl.pip.http.ReactiveWebClient;

import reactor.core.publisher.Flux;

public class TraccarPositions extends TraccarBase {

    private GeoMapper geoMapper;

    /**
     * @param auth a {@link JsonNode} containing the settings for authorization
     */
    public TraccarPositions(JsonNode auth, ObjectMapper mapper) {

        super(mapper);
        user     = getUser(auth);
        password = getPassword(auth);
        server   = getServer(auth);
        protocol = getProtocol(auth);
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     */
    public Flux<Val> getPositions(JsonNode settings) {

        geoMapper = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);

        var url = (String.format("ws://%s/api/socket", server));
        return establishSession(user, password, server, protocol).flatMapMany(cookie ->

        {
            try {
                return getFlux(url, cookie, getResponseFormat(settings, mapper), getDeviceId(settings), getLatitudeFirst(settings))
                        .map(Val::of)

                        .doFinally(s -> disconnect());
            } catch (JsonProcessingException | PolicyEvaluationException e) {
                return Flux.error(e);
            }
        });

    }

    private Flux<ObjectNode> getFlux(String url, String cookie, GeoPipResponseFormat format, int deviceId,
            boolean latitudeFirst) throws  JsonProcessingException {

        var client = new ReactiveWebClient(mapper);

        var template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "headers" : {
                        "cookie": "%s"
                    }
                }
                """;

        var request = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, cookie));

        var flux = client.consumeWebSocket(request).map(Val::get).flatMap(msg -> {
            try {
                return mapPosition(msg, format, latitudeFirst, deviceId);
            } catch (JsonProcessingException e) {
                return Flux.error(e);
            }
        }).map(res -> mapper.convertValue(res, ObjectNode.class));

        logger.info("Traccar-Client connected.");
        return flux;

        

    }

    Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst, int deviceId)
            throws JsonProcessingException {
        JsonNode pos = getPositionFromMessage(in, deviceId);

        if (pos.has(DEVICE_ID)) {

            return Flux.just(geoMapper.mapPosition(deviceId, pos, format, latitudeFirst));
        }

        return Flux.just();
    }

    private JsonNode getPositionFromMessage(JsonNode in, int deviceId) {

        if (in.has(POSITIONS)) {
            var pos1 = (ArrayNode) in.findValue(POSITIONS);
            for (var p : pos1) {
                if (p.findValue(DEVICE_ID).toPrettyString().equals(Integer.toString(deviceId))) {
                    return p;
                }
            }
        }

        return mapper.createObjectNode();

    }

}
