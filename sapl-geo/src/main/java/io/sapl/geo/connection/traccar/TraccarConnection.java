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
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.api.interpreter.PolicyEvaluationException;

import reactor.core.publisher.Flux;

public class TraccarConnection extends ConnectionBase {

    private static final String DEVICEID_CONST = "deviceId";
    private static final String PROTOCOL       = "protocol";
//    private Disposable           subscription;
//    private WebSocketSession     session;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int deviceId;

    public int getDeviceId() {
        return deviceId;
    }

    private final TraccarSessionManager sessionManager;
    private String                      url;

    private TraccarSessionHandler handler;

    private TraccarConnection(String user, String password, String serverName, String protocol, int deviceId,
            ObjectMapper mapper) throws PolicyEvaluationException {

        url = "ws://" + serverName + "/api/socket";

        this.sessionManager = new TraccarSessionManager(user, password, serverName, mapper);
        this.deviceId       = deviceId;
        this.handler        = new TraccarSessionHandler(sessionManager.getSessionCookie(), serverName, protocol,
                mapper);
    }

    public static TraccarConnection getNew(String user, String password, String server, String protocol, int deviceId,
            ObjectMapper mapper) throws PolicyEvaluationException {

        return new TraccarConnection(user, password, server, protocol, deviceId, mapper);
    }

    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            var socketManager = getNew(getUser(settings), getPassword(settings), getServer(settings),
                    getProtocol(settings), getDeviceId(settings), mapper);
            return socketManager.getFlux(getResponseFormat(settings, mapper), mapper).map(Val::of)
                    .onErrorResume(e -> Flux.just(Val.error(e))).doFinally(s -> socketManager.disconnect());

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }

    public Flux<ObjectNode> getFlux(GeoPipResponseFormat format, ObjectMapper mapper) throws PolicyEvaluationException {

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
            request = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, getSessionCookie()));
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        var flux = client.consumeWebSocket(request).map(Val::get)
                .flatMap(msg -> handler.mapPosition(msg, deviceId, format))
                .flatMap(res -> handler.getGeofences(res, deviceId, format))
                .map(res -> mapper.convertValue(res, ObjectNode.class));

        logger.info("Traccar-Client connected.");
        return flux;
    }

    public void disconnect() throws PolicyEvaluationException {
        
        try {
            if (this.sessionManager.closeTraccarSession()) {
            	
            	logger.info("Traccar-Client disconnected.");
            } else {
                throw new PolicyEvaluationException();
            }
        } catch (Exception e) {
            throw new PolicyEvaluationException("Traccar-Client could not be disconnected");
        }
    }

//    public Optional<WebSocketSession> getSession() {
//        return Optional.ofNullable(session);
//    }

    public String getSessionCookie() {

        return sessionManager.getSessionCookie();

    }

    private static int getDeviceId(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asInt();
        } else {

            throw new PolicyEvaluationException("No Device ID found");
        }

    }

    private static String getProtocol(JsonNode requestSettings) {
        if (requestSettings.has(PROTOCOL)) {
            return requestSettings.findValue(PROTOCOL).asText();
        } else {

            return "https";
        }

    }

}
