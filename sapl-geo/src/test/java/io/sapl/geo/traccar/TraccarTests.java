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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
class TraccarTests {

	private MockWebServer mockWebServer;
	private ObjectMapper mapper;
	private String serverUrl;
	private String authenticationTemp;

	@BeforeEach
	public void setUp() throws Exception {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		serverUrl = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
		mapper = new ObjectMapper();
		authenticationTemp = String.format("{\"user\":\"test\",\"password\":\"test\",\"server\":\"%s\"}",
				mockWebServer.getHostName() + ":" + mockWebServer.getPort());
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (mockWebServer != null) {
			mockWebServer.shutdown();
		}
	}

	@Test
	void testEstablishSessionPolicyEvaluationExceptionServerResponse()
			throws JsonProcessingException, URISyntaxException {

		mockWebServer.enqueue(new MockResponse().setResponseCode(300));
		var geofences = new TraccarGeofences(Val.ofJson(authenticationTemp).get(), mapper);
		Mono<String> session = geofences.establishSession("user", "password", serverUrl, "http");
		StepVerifier.create(session).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	void testEstablishSessionWithWebClientResponseException() throws JsonProcessingException, URISyntaxException {

		mockWebServer.enqueue(new MockResponse().setResponseCode(500));
		var geofences = new TraccarGeofences(Val.ofJson(authenticationTemp).get(), mapper);
		Mono<String> session = geofences.establishSession("user", "password", serverUrl, "http");
		StepVerifier.create(session).expectError(WebClientResponseException.class).verify();
	}

	@Test
	void testEstablishSessionPolicyEvaluationExceptionInvalidJsonInResponseGeoFences()
			throws JsonProcessingException, URISyntaxException {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("set-cookie", "some-cookie-value")
				.setBody("invalid_json"));
		var geofences = new TraccarGeofences(Val.ofJson(authenticationTemp).get(), mapper);
		Mono<String> sessionGeoFences = geofences.establishSession("user", "password", serverUrl, "http");
		StepVerifier.create(sessionGeoFences).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	void testEstablishSessionPolicyEvaluationExceptionInvalidJsonInResponsePositions()
			throws JsonProcessingException, URISyntaxException {

		mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("set-cookie", "some-cookie-value")
				.setBody("invalid_json"));
		var positions = new TraccarPositions(Val.ofJson(authenticationTemp).get(), mapper);
		Mono<String> sessionPosition = positions.establishSession("user", "password", serverUrl, "http");
		StepVerifier.create(sessionPosition).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	void testEstablishSessionUriSyntaxException() throws JsonProcessingException {

		var authenticationTemplate = "{\"user\":\"test\",\"password\":\"test\",\"server\":\"abc<>()\"}";
		var responseTemplate = "{\"responseFormat\":\"WKT\",\"repetitions\" : 3,\"pollingIntervalMs\" : 1000}";
		var val = Val.ofJson(responseTemplate);
		TraccarGeofences traccarGeofences = new TraccarGeofences(Val.ofJson(authenticationTemplate).get(),
				new ObjectMapper());
		assertThrows(URISyntaxException.class, () -> {
			traccarGeofences.getGeofences(val.get());
		});
	}
}
