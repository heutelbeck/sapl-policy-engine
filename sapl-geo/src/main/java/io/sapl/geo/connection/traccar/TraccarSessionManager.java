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

import java.net.URI;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;

public class TraccarSessionManager {

    private final String user;
    private final String password;
    private URI          uri;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ObjectMapper mapper;
    private String       sessionCookie;

    String getSessionCookie() {
        return sessionCookie;
    }

    private TraccarSession session;

    TraccarSession getSession() {
        return session;
    }

    TraccarSessionManager(String user, String password, String server, String protocol, ObjectMapper mapper)
            throws PolicyEvaluationException {
        this.user     = user;
        this.password = password;
        this.mapper   = mapper;
        establishSession(server, protocol);

    }

    private void establishSession(String serverName, String protocol) throws PolicyEvaluationException {

        HttpResponse<String> res;

        try {
            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));

            Map<String, String> bodyProperties = new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;

            };

            bodyProperties.put("email", user);
            bodyProperties.put("password", password);

            String form = bodyProperties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            HttpRequest req = null;

            req = HttpRequest.newBuilder().uri(uri).headers("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();

            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            res = client.send(req, BodyHandlers.ofString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyEvaluationException(e);
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        if (res.statusCode() == 200) {

            res.headers().firstValue("set-cookie").ifPresent(v -> sessionCookie = v);
            session = createTraccarSession(res.body());
            var msg = String.format("Traccar Session %s established", session.getId());
            logger.info(msg);
        } else {
            throw new PolicyEvaluationException(
                    "Session could not be established. Server respondet with " + res.statusCode());
        }
 
    }

    private TraccarSession createTraccarSession(String json) throws PolicyEvaluationException {

        try {
            return mapper.convertValue(mapper.readTree(json), TraccarSession.class);
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }
    }

    Boolean closeTraccarSession() throws PolicyEvaluationException {

        HttpRequest req = null;
        req = HttpRequest.newBuilder().uri(uri).header("cookie", sessionCookie).DELETE().build();

        HttpResponse<String> res;
        var                  client = HttpClient.newHttpClient();
        try {
            res = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyEvaluationException(e);
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }
        if (res.statusCode() == 204) {
            var msg = String.format("Traccar Session %s closed", session.getId());
            logger.info(msg);
            return true;
        }
        var msg = String.format("Traccar Session %s could not be closed", session.getId());
        logger.info(msg);
        return false;

    }

}
