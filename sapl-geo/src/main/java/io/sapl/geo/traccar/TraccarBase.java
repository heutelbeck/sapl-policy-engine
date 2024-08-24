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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.shared.TrackerConnectionBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
abstract class TraccarBase extends TrackerConnectionBase {

    private int sessionId;
    private URI uri;

    protected String sessionCookie;
    protected String user;
    protected String password;
    protected String server;
    protected String protocol;

    protected Mono<String> establishSession(String user, String password, String serverName, String protocol)
            throws PolicyEvaluationException {

        try {
            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));

            var bodyProperties = new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;

            };

            bodyProperties.put("email", user);
            bodyProperties.put("password", password);

            var form = bodyProperties.entrySet().stream().map(
                    e -> String.format("%s=%s", e.getKey(), URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("&"));

            var client = WebClient.builder().build();

            return client.post().uri(uri).header("Content-Type", "application/x-www-form-urlencoded").bodyValue(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            response -> Mono.error(new PolicyEvaluationException(
                                    "Session could not be established. Server responded with "
                                            + response.statusCode().value())))
                    .toEntity(String.class).flatMap(response -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            var setCookieHeader = response.getHeaders().getFirst("set-cookie");
                            if (setCookieHeader != null) {
                                sessionCookie = setCookieHeader;
                                try {
                                    setSessionId(response.getBody());
                                } catch (Exception e) {
                                    return Mono.error(e);
                                }
                                log.info("Traccar Session {} established.", sessionId);
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
            return Mono.error(e);
        }

    }

    private void setSessionId(String json) throws JsonProcessingException {

        var sessionJson = mapper.readTree(json);
        if (sessionJson.has("id")) {
            this.sessionId = sessionJson.get("id").asInt();
        }

    }

    protected void disconnect() throws PolicyEvaluationException {

        WebClient client = WebClient.builder().defaultHeader("cookie", sessionCookie).build();
        client.delete().uri(uri).retrieve().toBodilessEntity().flatMap(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Traccar Session {} closed successfully.", sessionId);
                return Mono.empty();
            } else {
                var errorMsg2 = String.format("Failed to close Traccar Session %s. Status code: %s", sessionId,
                        response.getStatusCode());
                log.error(errorMsg2);
                throw (new PolicyEvaluationException(errorMsg2));
            }
        }).onErrorResume(error -> {
            var errorMsg1 = String.format("Failed to close Traccar Session %s", sessionId);
            log.error(errorMsg1);
            return Mono.just(error);
        })

                .subscribe();
    }

}
