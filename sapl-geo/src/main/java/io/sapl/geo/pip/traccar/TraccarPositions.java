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
package io.sapl.geo.pip.traccar;

import java.net.URISyntaxException;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.pip.model.GeoPipResponse;
import io.sapl.geo.pip.model.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class TraccarPositions extends TraccarConnection {

    private static final String DEVICE_ID = "deviceId";
    private static final String POSITIONS = "positions";

    /**
     * @param auth a {@link JsonNode} containing the settings for authorization
     * @param mapper an {@link ObjectMapper}
     */
    public TraccarPositions(JsonNode auth, ObjectMapper mapper) {
        altitude    = "altitude";
        lastupdate  = "fixTime";
        accuracy    = "accuracy";
        latitude    = "latitude";
        longitude   = "longitude";
        this.mapper = mapper;
        user        = getUser(auth);
        password    = getPassword(auth);
        server      = getServer(auth);
        protocol    = getProtocol(auth);
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @throws URISyntaxException
     * @throws PolicyEvaluationException
     */
    public Flux<Val> getPositions(JsonNode settings) throws URISyntaxException {
        final var baseUrl = (String.format("ws://%s", server));
        return establishSession(user, password, server, protocol).flatMapMany(cookie -> {
            try {
                return getTraccarResponse(baseUrl, cookie, getResponseFormat(settings, mapper), getDeviceId(settings),
                        getLatitudeFirst(settings)).map(Val::of).doFinally(s -> disconnect());
            } catch (JsonProcessingException e) {
                return Flux.error(e);
            }
        });
    }

    private Flux<ObjectNode> getTraccarResponse(String url, String cookie, GeoPipResponseFormat format, String deviceId,
            boolean latitudeFirst) throws JsonProcessingException {
        final var webClient       = new ReactiveWebClient(mapper);
        final var header          = String.format(COOKIE_HEADER_CONST, cookie);
        final var requestTemplate = createRequestTemplate(url, "/api/socket", MediaType.APPLICATION_JSON_VALUE, header,
                null, null, null);
        return webClient.consumeWebSocket(requestTemplate).flatMap(v -> {
            try {
                final var response = getPosition(v.get(), format, latitudeFirst, deviceId);
                return Mono.justOrEmpty(response).map(r -> mapper.convertValue(r, ObjectNode.class));
            } catch (JsonProcessingException e) {
                throw new PolicyEvaluationException(e);
            }
        });
    }

    private GeoPipResponse getPosition(JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst, String deviceId)
            throws JsonProcessingException {
        if (in.has(POSITIONS)) {
            final var posArray = (ArrayNode) in.get(POSITIONS);
            for (final var pos : posArray) {
                if (pos.get(DEVICE_ID).asText().equals(deviceId)) {
                    return mapPosition(deviceId, pos, format, latitudeFirst);
                }
            }
        }
        return null;
    }
}
