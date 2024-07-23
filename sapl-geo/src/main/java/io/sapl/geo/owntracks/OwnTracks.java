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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class OwnTracks extends TrackerConnectionBase {

    protected static final String HTTP_BASIC_AUTH_USER = "httpUser";

    private ReactiveWebClient client;
    private final Logger      logger = LoggerFactory.getLogger(getClass());

    private int    deviceId;
    private String authSettings;

    /**
     * @param auth   a {@link JsonNode} containing the settings for authorization
     * @param mapper a {@link ObjectMapper}
     */
    public OwnTracks(JsonNode auth, ObjectMapper mapper) {

        this.mapper = mapper;
        altitude    = "alt";
        lastupdate  = "created_at";
        accuracy    = "acc";
        latitude    = "lat";
        longitude   = "lon";

        if (auth != null) {

            var valueToEncode   = String.format("%s:%s", getHttpBasicAuthUser(auth), getPassword(auth));
            var basicAuthHeader = "Basic "
                    + Base64.getEncoder().encodeToString(valueToEncode.getBytes(StandardCharsets.UTF_8));

            authSettings = """
                        ,
                        "headers" : {
                           Authorization": "%s"
                        }
                      }
                    """;
            authSettings = String.format(authSettings, basicAuthHeader);
        }
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     */
    public Flux<Val> connect(JsonNode settings) {

        deviceId = getDeviceId(settings);
        client   = new ReactiveWebClient(mapper);
        // geoMapper = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE,
        // ACCURACY, mapper);
        var url = String.format("%s://%s/api/0/last?user=%s&device=%s", getProtocol(settings), getServer(settings),
                getUser(settings), deviceId);

        try {

            var request = getRequest(settings, url);
            return getFlux(request, getResponseFormat(settings, mapper), mapper, getLatitudeFirst(settings))
                    .map(Val::of);

        } catch (Exception e) {
            return Flux.just(Val.error(e.getMessage()));
        }

    }

    private String getRequest(JsonNode auth, String url) {

        var settings = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s"
                """;

        settings = String.format(settings, url, MediaType.APPLICATION_JSON_VALUE);

//        if (getHttpBasicAuthUser(auth) != null && getPassword(auth) != null) {
//            var valueToEncode   = String.format("%s:%s", getHttpBasicAuthUser(auth), getPassword(auth));
//            var basicAuthHeader = "Basic "
//                    + Base64.getEncoder().encodeToString(valueToEncode.getBytes(StandardCharsets.UTF_8));
//
//            var authorizationSettings = """
//                        ,
//                        "headers" : {
//                           Authorization": "%s"
//                        }
//                      }
//                    """;
//            authorizationSettings = String.format(authorizationSettings, basicAuthHeader);

        if (authSettings != null) {

            settings = settings.concat(authSettings);

        } else {
            settings = settings.concat("}");

        }

        return settings;

    }

    private Flux<ObjectNode> getFlux(String requestString, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) {

        Val request;
        try {
            request = Val.ofJson(requestString);

        } catch (Exception e) {
            return Flux.error(e);
        }

        var flux = client.httpRequest(HttpMethod.GET, request).flatMap(v -> {
            try {
                return mapResponse(v.get(), format, mapper, latitudeFirst);
            } catch (JsonProcessingException e) {
                return Flux.error(e);
            }
        }).map(res -> mapper.convertValue(res, ObjectNode.class));
        logger.info("OwnTracks-Client connected.");
        return flux;
    }

    public Flux<GeoPipResponse> mapResponse(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) throws JsonProcessingException {

        var response = mapPosition(deviceId, in.get(0), format, latitudeFirst);
        var res      = in.findValue("inregions");

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

    private static String getHttpBasicAuthUser(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(HTTP_BASIC_AUTH_USER)) {
            return requestSettings.findValue(HTTP_BASIC_AUTH_USER).asText();
        } else {
            return null;

        }

    }

    protected static String getPassword(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PASSWORD_CONST)) {
            return requestSettings.findValue(PASSWORD_CONST).asText();
        } else {

            return null;
        }

    }
}
