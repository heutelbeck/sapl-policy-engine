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
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;
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
			throws URISyntaxException {

		uri = new URI(String.format("%s://%s/api/session", protocol, serverName));
		final var bodyProperties = new HashMap<String, String>() {
			private static final long serialVersionUID = 1L;
		};

		bodyProperties.put("email", user);
		bodyProperties.put("password", password);
		final var form = bodyProperties.entrySet().stream()
				.map(e -> String.format("%s=%s", e.getKey(), URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)))
				.collect(Collectors.joining("&"));
		final var client = WebClient.builder().build();
		return client.post().uri(uri).header("Content-Type", "application/x-www-form-urlencoded").bodyValue(form)
				.retrieve().toEntity(String.class).flatMap(response -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						final var setCookieHeader = response.getHeaders().getFirst("set-cookie");
						sessionCookie = setCookieHeader;
						try {
							setSessionId(response.getBody());
						} catch (JsonProcessingException e) {
							throw new PolicyEvaluationException(e);
						}
						return Mono.just(setCookieHeader);

					} else {
						throw new PolicyEvaluationException("Session could not be established. Server responded with "
								+ response.getStatusCode().value());
					}
				});

	}

	private void setSessionId(String json) throws JsonProcessingException {

		var sessionJson = mapper.readTree(json);
		this.sessionId = sessionJson.get("id").asInt();
	}

	protected void disconnect() throws PolicyEvaluationException {

		WebClient client = WebClient.builder().defaultHeader("cookie", sessionCookie).build();
		client.delete().uri(uri).retrieve().toBodilessEntity()
				.doOnError(error -> log.error("Failed to close Traccar Session {}: {}", sessionId, error.getMessage()))
				.subscribe();
	}
}
