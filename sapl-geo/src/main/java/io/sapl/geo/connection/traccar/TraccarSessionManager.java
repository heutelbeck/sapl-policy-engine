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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.Getter;
import reactor.core.publisher.Mono;

public class TraccarSessionManager {

    private final String user;
    private final String password;
    private URI          uri;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ObjectMapper mapper;

    private int    sessionId;
    @Getter
    private String sessionCookie;

//    String getSessionCookie() {
//        return sessionCookie;
//    }

    // private TraccarSession session;

//    TraccarSession getSession() {
//        return session;
//    }

    TraccarSessionManager(String user, String password, ObjectMapper mapper) throws PolicyEvaluationException {
        this.user     = user;
        this.password = password;
        this.mapper   = mapper;

    }

//    private void establishSession(String serverName, String protocol) throws PolicyEvaluationException {
//
//        HttpResponse<String> res;
//
//        try {
//            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));
//
//            Map<String, String> bodyProperties = new HashMap<String, String>() {
//                private static final long serialVersionUID = 1L;
//
//            };
//
//            bodyProperties.put("email", user);
//            bodyProperties.put("password", password);
//
//            String form = bodyProperties.entrySet().stream()
//                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
//                    .collect(Collectors.joining("&"));
//
//            HttpRequest req = null;
//
//            req = HttpRequest.newBuilder().uri(uri).headers("Content-Type", "application/x-www-form-urlencoded")
//                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
//
//            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
//
//            res = client.send(req, BodyHandlers.ofString());
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new PolicyEvaluationException(e);
//        } catch (Exception e) {
//            throw new PolicyEvaluationException(e);
//        }
//
//        if (res.statusCode() == 200) {
//
//            res.headers().firstValue("set-cookie").ifPresent(v -> sessionCookie = v);
//            session = createTraccarSession(res.body());
//            var msg = String.format("Traccar Session %s established", session.getId());
//            logger.info(msg);
//        } else {
//            throw new PolicyEvaluationException(
//                    "Session could not be established. Server respondet with " + res.statusCode());
//        }
//
//    }

//    public Mono<Void> establishSession(String serverName, String protocol) throws PolicyEvaluationException {
//
//        try {
//            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));
//
//            Map<String, String> bodyProperties = new HashMap<String, String>() {
//                private static final long serialVersionUID = 1L;
//
//            };
//
//            bodyProperties.put("email", user);
//            bodyProperties.put("password", password);
//
//            String form = bodyProperties.entrySet().stream()
//                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
//                    .collect(Collectors.joining("&"));
//
//            WebClient client = WebClient.builder()
//                    //.baseUrl(uri.toString())
//                    .build();
//
//            return client.post()
//                    .uri(uri)
//                    .header("Content-Type", "application/x-www-form-urlencoded")
//                    .bodyValue(form)
//                    .retrieve()
//                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
//                        return Mono.error(new PolicyEvaluationException("Session could not be established. Server responded with " + response.statusCode().value()));
//                    })
//                    .toEntity(String.class)
//                    .doOnNext(x->{
//                    	var a = x;
//                    })
//                    .flatMap(response -> {
//                        if (response.getStatusCode().is2xxSuccessful()) {
//                            String setCookieHeader = response.getHeaders().getFirst("set-cookie");
//                            if (setCookieHeader != null) {
//                                sessionCookie = setCookieHeader;
//                            }
//                            session = createTraccarSession(response.getBody());
//                            String msg = String.format("Traccar Session %s established", session.getId());
//                            logger.info(msg);
//                            return Mono.empty();
//                        } else {
//                            return Mono.error(new PolicyEvaluationException("Session could not be established. Server responded with " + response.getStatusCode().value()));
//                        }
//                    });
//        } catch (Exception e) {
//            throw new PolicyEvaluationException(e);
//        }
//
//    }

    public Mono<String> establishSession(String serverName, String protocol) throws PolicyEvaluationException {

        try {
            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));

            var bodyProperties = new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;

            };

            bodyProperties.put("email", user);
            bodyProperties.put("password", password);

            var form = bodyProperties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            var client = WebClient.builder().build();

            return client.post().uri(uri).header("Content-Type", "application/x-www-form-urlencoded").bodyValue(form)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> Mono.error(new PolicyEvaluationException(
                                    "Session could not be established. Server responded with "
                                            + response.statusCode().value())))
                    .toEntity(String.class).flatMap(response -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            String setCookieHeader = response.getHeaders().getFirst("set-cookie");
                            if (setCookieHeader != null) {
                                sessionCookie = setCookieHeader;
                                // session = createTraccarSession(response.getBody());
                                setSessionId(response.getBody());
                                logger.info("Traccar Session {} established.", sessionId);
                                return Mono.just(setCookieHeader);
                            } else {
                                return Mono.error(
                                        new PolicyEvaluationException("No session cookie found in the response."));
                            }
                        } else {
                            return Mono.error(new PolicyEvaluationException(
                                    "Session could not be established. Server responded with "
                                            + response.getStatusCode().value()));
                        }
                    });
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }

    private void setSessionId(String json) {
        try {
            var sessionJson = mapper.readTree(json);
            if (sessionJson.has("id")) {
                this.sessionId = sessionJson.get("id").asInt();
            }
        } catch (Exception e) {

            throw new PolicyEvaluationException(e);
        }
    }

//    private TraccarSession createTraccarSession(String json) throws PolicyEvaluationException {
//
//        try {      
//            return mapper.convertValue(mapper.readTree(json), TraccarSession.class);
//        } catch (Exception e) {
//            throw new PolicyEvaluationException(e);
//        }
//    }

    public Mono<Boolean> closeTraccarSession() throws PolicyEvaluationException {

        var client = WebClient.builder().defaultHeader("cookie", sessionCookie).build();

        return client.delete().uri(uri).retrieve().toBodilessEntity().flatMap(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Traccar Session {} closed successfully.", sessionId);
                return Mono.just(true);
            } else {
                logger.error(String.format("Failed to close Traccar Session %s. Status code: %s", sessionId,
                        response.getStatusCode()));
                return Mono.just(false);
            }
        });

    }
//    Boolean closeTraccarSession() throws PolicyEvaluationException {
//
//        HttpRequest req = null;
//        req = HttpRequest.newBuilder().uri(uri).header("cookie", sessionCookie).DELETE().build();
//
//        HttpResponse<String> res;
//        var                  client = HttpClient.newHttpClient();
//        try {
//            res = client.send(req, BodyHandlers.ofString());
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new PolicyEvaluationException(e);
//        } catch (Exception e) {
//            throw new PolicyEvaluationException(e);
//        }
//        if (res.statusCode() == 204) {
//            var msg = String.format("Traccar Session %s closed", session.getId());
//            logger.info(msg);
//            return true;
//        }
//        var msg = String.format("Traccar Session %s could not be closed", session.getId());
//        logger.info(msg);
//        return false;
//
//    }

}
