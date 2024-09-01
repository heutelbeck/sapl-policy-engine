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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public final class TraccarPositions extends TraccarBase {

    private static final String DEVICE_ID = "deviceId";
    private static final String POSITIONS = "positions";

    /**
     * @param auth   a {@link JsonNode} containing the settings for authorization
     * @param mapper an {@link ObjectMapper}
     */
    public TraccarPositions(JsonNode auth, ObjectMapper mapper) {

        altitude   = "altitude";
        lastupdate = "fixTime";
        accuracy   = "accuracy";
        latitude   = "latitude";
        longitude  = "longitude";

        this.mapper = mapper;

        user     = getUser(auth);
        password = getPassword(auth);
        server   = getServer(auth);
        protocol = getProtocol(auth);
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     */
    public Flux<Val> getPositions(JsonNode settings) {

        var url = (String.format("ws://%s/api/socket", server));
        return establishSession(user, password, server, protocol).flatMapMany(cookie -> {
            try {
                return getPositionFlux(url, cookie, getResponseFormat(settings, mapper), getDeviceId(settings),
                        getLatitudeFirst(settings)).map(Val::of)

                        .doFinally(s -> disconnect());
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    private Flux<ObjectNode> getPositionFlux(String url, String cookie, GeoPipResponseFormat format, String deviceId,
            boolean latitudeFirst) throws JsonProcessingException {

        var client       = new ReactiveWebClient(mapper);
        var template     = """
                { "baseUrl" : "%s", "accept" : "%s", "headers" : { "cookie": "%s" } }
                """;
        var request      = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, cookie));
        var responseFlux = client.consumeWebSocket(request).map(Val::get).flatMap(msg -> {
                             try {
                                 return mapPosition(msg, format, latitudeFirst, deviceId);
                             } catch (JsonProcessingException e) {
                                 return Flux.error(e);
                             }
                         }).map(res -> mapper.convertValue(res, ObjectNode.class));

        log.info("Traccar-Client connected.");
        return responseFlux;
    }

    Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst, String deviceId)
            throws JsonProcessingException {
        var pos = getPositionFromMessage(in, deviceId);
        if (pos.has(DEVICE_ID)) {

            return Flux.just(mapPosition(deviceId, pos, format, latitudeFirst));
        }
        return Flux.just();
    }

    private JsonNode getPositionFromMessage(JsonNode in, String deviceId) {

        if (in.has(POSITIONS)) {
            var pos = (ArrayNode) in.findValue(POSITIONS);
            for (var p : pos) {
                if (p.findValue(DEVICE_ID).toPrettyString().equals(deviceId)) {
                    return p;
                }
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }
}
