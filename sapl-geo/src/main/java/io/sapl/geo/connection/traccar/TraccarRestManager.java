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

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Mono;

public class TraccarRestManager {

    private static final String     COOKIE  = "cookie";
    private final String            sessionCookie;
    private ReactiveWebClient       webClient;
    private String                  baseURL;
    private HashMap<String, String> headers = new HashMap<>();

    public TraccarRestManager(String sessionCookie, String serverName, String protocol, ObjectMapper mapper) {
        baseURL            = protocol + "://" + serverName;
        this.sessionCookie = sessionCookie;
        headers.put(COOKIE, sessionCookie);
        webClient = new ReactiveWebClient(mapper);
    }

    public Mono<JsonNode> getGeofences(String deviceId) {

        var params = new HashMap<String, String>();
        params.put("deviceId", deviceId);

        var template = """
                {
                    "baseUrl" : "%s",
                    "path" : "%s",
                    "accept" : "%s",
                    "repetitions" : 1,
                    "headers" : {
                        "cookie" : "%s",
                        "deviceId" : "%s"
                    }
                }
                """;
        Val request  = Val.of("");
        try {
            request = Val.ofJson(String.format(template, baseURL, "api/geofences", MediaType.APPLICATION_JSON_VALUE,
                    sessionCookie, deviceId));
        } catch (Exception e) {
            System.out.println("getGeofences");
            e.printStackTrace();
        }

        return webClient.httpRequest(HttpMethod.GET, request).next().map(v -> v.get());      
    }

}
