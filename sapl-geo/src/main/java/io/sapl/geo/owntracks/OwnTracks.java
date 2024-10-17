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
package io.sapl.geo.owntracks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geo.shared.TrackerConnectionBase;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Flux;

public final class OwnTracks extends TrackerConnectionBase {

	protected static final String HTTP_BASIC_AUTH_USER = "httpUser";
	private String deviceId;
	private String authHeader;
	private String server;
	private String protocol;

	/**
	 * @param auth   a {@link JsonNode} containing the settings for authorization
	 * @param mapper a {@link ObjectMapper}
	 */
	public OwnTracks(JsonNode auth, ObjectMapper mapper) {

		this.mapper = mapper;
		altitude = "alt";
		lastupdate = "created_at";
		accuracy = "acc";
		latitude = "lat";
		longitude = "lon";
		server = getServer(auth);
		protocol = getProtocol(auth);
		baseUrl = protocol + "://" + server;

		var authUser = getHttpBasicAuthUser(auth);
		var password = getPassword(auth);
		if (authUser != null && password != null) {
			var valueToEncode = String.format("%s:%s", authUser, password);
			var basicAuthHeader = "Basic "
					+ Base64.getEncoder().encodeToString(valueToEncode.getBytes(StandardCharsets.UTF_8));
			authHeader = String.format("\"Authorization\": \"%s\"", basicAuthHeader);
		}

	}

	/**
	 * @param settings a {@link JsonNode} containing the settings
	 * @throws JsonProcessingException
	 */
	public Flux<Val> getPositionWithInregions(JsonNode settings) throws JsonProcessingException {
		deviceId = getDeviceId(settings);
		return getOwnTracksResponse(getResponseFormat(settings, mapper), getUser(settings), getPollingInterval(settings),
				getRepetitions(settings), getLatitudeFirst(settings)).map(Val::of);
	}

	private Flux<ObjectNode> getOwnTracksResponse(GeoPipResponseFormat format, String user, Long pollingInterval, Long repetitions,
			boolean latitudeFirst) throws JsonProcessingException {

		var webClient = new ReactiveWebClient(mapper);
		var baseUrl = protocol + "://" + server;
		var urlParamUser = String.format("\"user\": \"%s\"", user);
		var urlParamDevice = String.format("\"device\": \"%s\"", deviceId);
		String[] urlParamArray = null;
		if (authHeader != null) {
			var urlParams = String.format("\"deviceId\": \"%s\"", deviceId);
			urlParamArray = new String[] { urlParams };
		}
		var requesttemplate = createRequestTemplate(baseUrl, "api/0/last", MediaType.APPLICATION_JSON_VALUE,
				urlParamArray, new String[] { urlParamUser, urlParamDevice }, pollingInterval, repetitions);

		return webClient.httpRequest(HttpMethod.GET, requesttemplate).flatMap(v -> {
			try {
				return mapResponse(v.get(), format, mapper, latitudeFirst);
			} catch (JsonProcessingException e) {
				return Flux.error(e);
			}
		}).map(res -> mapper.convertValue(res, ObjectNode.class));
	}

	private Flux<GeoPipResponse> mapResponse(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper,
			boolean latitudeFirst) throws JsonProcessingException {

		var response = mapPosition(deviceId, in.get(0), format, latitudeFirst);
		var res = in.findValue("inregions");
		response.setGeoFences(mapOwnTracksInRegions(res, mapper));
		return Flux.just(response);
	}

	private List<Geofence> mapOwnTracksInRegions(JsonNode in, ObjectMapper mapper) throws JsonProcessingException {

		List<Geofence> fenceRes = new ArrayList<>();
		var fences = mapper.readTree(in.toString());
		for (var geoFence : fences) {
			fenceRes.add(Geofence.builder().name(geoFence.asText()).build());
		}
		return fenceRes;
	}

	private String getHttpBasicAuthUser(JsonNode requestSettings) throws PolicyEvaluationException {
		if (requestSettings.has(HTTP_BASIC_AUTH_USER)) {
			return requestSettings.findValue(HTTP_BASIC_AUTH_USER).asText();
		} else {
			return null;
		}
	}

	@Override
	protected String getPassword(JsonNode requestSettings) throws PolicyEvaluationException {
		if (requestSettings.has(PASSWORD_CONST)) {
			return requestSettings.findValue(PASSWORD_CONST).asText();
		} else {
			return null;
		}
	}
}
