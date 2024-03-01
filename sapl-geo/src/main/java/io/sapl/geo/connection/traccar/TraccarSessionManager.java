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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;

public class TraccarSessionManager {

    private final String user;
    private final String password;
    private URI          uri;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String sessionCookie;

    public String getSessionCookie() {
        return sessionCookie;
    }

    private TraccarSession session;

    public TraccarSession getSession() {
        return session;
    }

    public TraccarSessionManager(String user, String password, String server) throws Exception {
        this.user     = user;
        this.password = password;

        establishSession(server);

    }

    private void establishSession(String serverName) throws Exception {

        uri = new URI("http://" + serverName + "/api/session");

        Map<String, String> bodyProperties = new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;
            {
                put("email", user);
                put("password", password);
            }
        };

        String form = bodyProperties.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = null;

        req = HttpRequest.newBuilder().uri(uri).headers("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        var res = client.send(req, BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            sessionCookie = res.headers().firstValue("set-cookie").get();
            session       = createTraccarSession(res.body());
        } else {
            throw new PolicyEvaluationException(
                    "Session could not be established. Server respondet with " + res.statusCode());
        }

        // 617662
    }

    private TraccarSession createTraccarSession(String json) throws Exception {

        ObjectMapper   mapper = new ObjectMapper();
        TraccarSession session;

        session = mapper.convertValue(mapper.readTree(json), TraccarSession.class);

        return session;

    }

    public Boolean closeTraccarSession() throws Exception {

        HttpRequest req = null;
        req = HttpRequest.newBuilder().uri(uri).header("cookie", sessionCookie).DELETE().build();

        var client = HttpClient.newHttpClient();
        var res    = client.send(req, BodyHandlers.ofString());
        if (res.statusCode() == 204) {
            logger.info("Traccar Session for DeviceId " + "" + "closed.");
            return true;
        }

        return false;

    }

}
