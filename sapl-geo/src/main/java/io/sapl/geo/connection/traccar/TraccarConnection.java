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
package io.sapl.geo.connection.traccar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.RequiredArgsConstructor;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.api.interpreter.PolicyEvaluationException;

import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class TraccarConnection extends ConnectionBase {

    private final Logger       logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper mapper;

    private TraccarSessionManager sessionManager;
    private TraccarSessionHandler handler;

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
//    public Flux<Val> connect(JsonNode settings) {
//
//        var server   = getServer(settings);
//        var protocol = getProtocol(settings);
//        var url      = "ws://" + server + "/api/socket";
//
//        this.sessionManager = new TraccarSessionManager(getUser(settings), getPassword(settings), server, protocol,
//                mapper);
//
//
//
//        this.handler        = new TraccarSessionHandler(getDeviceId(settings), sessionManager.getSessionCookie(),
//                server, protocol, mapper);
//
//        try {
//            return getFlux(url, getResponseFormat(settings, mapper), mapper, getLatitudeFirst(settings)).map(Val::of)
//                    .onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
//
//        } catch (Exception e) {
//            return Flux.just(Val.error(e.getMessage()));
//        }
//
//    }

    public Flux<Val> connect(JsonNode settings) {

        var server   = getServer(settings);
        var protocol = getProtocol(settings);
        var url      = "ws://" + server + "/api/socket";

        this.sessionManager = new TraccarSessionManager(getUser(settings), getPassword(settings), server, protocol,
                mapper);

        return this.sessionManager.establishSession(server, protocol).flatMapMany(cookie -> {

            this.handler = new TraccarSessionHandler(getDeviceId(settings), cookie, server, protocol, mapper);

            return getFlux(url, cookie, getResponseFormat(settings, mapper), mapper, getLatitudeFirst(settings))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
        }).doFinally(s -> {
//            try {
//                disconnect();
//            } catch (PolicyEvaluationException e) {
//
//                logger.error("Error disconnecting Traccar session", e);
//            }
        });

    }

    private Flux<ObjectNode> getFlux(String url, String cookie, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) throws PolicyEvaluationException {

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
        Val request;
        try {
            request = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, cookie));

            var flux = client.consumeWebSocket(request).map(Val::get)
                    .flatMap(msg -> handler.mapPosition(msg, format, latitudeFirst))
                    .flatMap(res -> handler.getGeofences(res, format, latitudeFirst))
                    .map(res -> mapper.convertValue(res, ObjectNode.class));

            logger.info("Traccar-Client connected.");
            return flux;

        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }

    private void disconnect() throws PolicyEvaluationException {

        this.sessionManager.closeTraccarSession().subscribe(result -> {
            if (result) {
                logger.info("Traccar-Client disconnected.");
            } else {
                throw new PolicyEvaluationException("Traccar-Client could not be disconnected");
            }
        }, error -> {
            throw new PolicyEvaluationException("Traccar-Client could not be disconnected", error);
        });
    }

//    private void disconnect() throws PolicyEvaluationException {
//
//        try {
//            if (this.sessionManager.closeTraccarSession()) {
//
//                logger.info("Traccar-Client disconnected.");
//            } else {
//                throw new PolicyEvaluationException();
//            }
//        } catch (Exception e) {
//            throw new PolicyEvaluationException("Traccar-Client could not be disconnected");
//        }
//    }

//    public Optional<WebSocketSession> getSession() {
//        return Optional.ofNullable(session);
//    }

//    public String getSessionCookie() {
//
//        return sessionManager.getSessionCookie();
//
//    }

}
