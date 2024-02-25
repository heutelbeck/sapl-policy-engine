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

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
//import io.sapl.server.RequestSettingException;
import reactor.core.publisher.Flux;

public class TraccarSocketManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String DEVICEID       = "deviceId";
    private static final String USER           = "user";
    private static final String PASSWORD       = "password";
    private static final String SERVER         = "server";
    private static final String PROTOCOL       = "protocol";
    private static final String RESPONSEFORMAT = "responseFormat";

//    private Disposable           subscription;
//    private WebSocketSession     session;

    private int               deviceId;
    private ReactiveWebClient client;

    public int getDeviceId() {
        return deviceId;
    }

    private final TraccarSessionManager sessionManager;
//    private URI                         uri;
    private String              url;
    private static ObjectMapper mapper;

    private TraccarSessionHandler handler;

    private TraccarSocketManager(String user, String password, String serverName, String protocol, int deviceId,
            ObjectMapper mapper) {

        url = "ws://" + serverName + "/api/socket";

        TraccarSocketManager.mapper = mapper;
        this.sessionManager         = new TraccarSessionManager(user, password, serverName);

        this.deviceId = deviceId;
        this.handler  = new TraccarSessionHandler(sessionManager.getSessionCookie(), serverName, protocol, mapper);
    }

    public static TraccarSocketManager getNew(String user, String password, String server, String protocol,
            int deviceId, ObjectMapper mapper) {

        return new TraccarSocketManager(user, password, server, protocol, deviceId, mapper);
    }

    public static Flux<ObjectNode> connectToTraccar(JsonNode settings, ObjectMapper mapper)
            // throws RequestSettingException {
            throws Exception {
        var socketManager = getNew(getUser(settings), getPassword(settings), getServer(settings), getProtocol(settings),
                getDeviceId(settings), mapper);
        return socketManager.connect(getResponseFormat(settings));

    }

    public String getSessionCookie() {

        return sessionManager.getSessionCookie();

    }

    public Flux<ObjectNode> connect(GeoPipResponseFormat format) {

        client = new ReactiveWebClient(mapper);
        var headers = new HashMap<String, String>();
        headers.put("cookie", getSessionCookie());

        var param = Val.of("");

        var template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "headers" : {
                        "cookie": "%s"
                    }
                }
                """;
        Val request  = Val.of("");
        try {
            request = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, getSessionCookie()));
        } catch (JsonProcessingException e) {
            System.out.println("TraccarSocketManager.connect");
            e.printStackTrace();
        }

        var flux = client.consumeWebSocket(request)
        		.map(v -> v.get())
                .flatMap(msg -> handler.mapPosition(msg, deviceId, format))
                .flatMap(res -> handler.getGeofences(res, deviceId, format))
                .map(res -> mapper.convertValue(res, ObjectNode.class));

        System.out.println("Traccar-Client connected.");
        return flux;
    }

//    public void disconnect() {
//
//        client.disconnectSocket();
//        logger.info("Client disconnected.");
//
//    }

//    public Optional<WebSocketSession> getSession() {
//        return Optional.ofNullable(session);
//    }

    private static String getUser(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(USER)) {
            return requestSettings.findValue(USER).asText();
        } else {
            throw new Exception("No User found");
            // throw new RequestSettingException("No User found");
        }

    }

    private static String getPassword(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(PASSWORD)) {
            return requestSettings.findValue(PASSWORD).asText();
        } else {

            throw new Exception("No Password found");
//            throw new RequestSettingException("No Password found");
        }

    }

    private static String getServer(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(SERVER)) {
            return requestSettings.findValue(SERVER).asText();
        } else {
            throw new Exception("No Server found");
//            throw new RequestSettingException("No Server found");
        }

    }

    private static int getDeviceId(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(DEVICEID)) {
            return requestSettings.findValue(DEVICEID).asInt();
        } else {

//            throw new RequestSettingException("No Device ID found");
            throw new Exception("No Device ID found");
        }

    }

    private static String getProtocol(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(PROTOCOL)) {

            return requestSettings.findValue(PROTOCOL).asText();
        } else {

            return "https";
        }

    }

    private static GeoPipResponseFormat getResponseFormat(JsonNode requestSettings) throws Exception {
        if (requestSettings.has(RESPONSEFORMAT)) {
            return mapper.convertValue(requestSettings.findValue(RESPONSEFORMAT), GeoPipResponseFormat.class);
        } else {

//            throw new RequestSettingException("No Response Format found");
            throw new Exception("No Response Format found");
        }

    }

}
