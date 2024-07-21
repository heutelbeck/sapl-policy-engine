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

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geo.shared.ConnectionBase;
import io.sapl.geo.shared.GeoMapper;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class OwnTracksConnection extends ConnectionBase {

    private static final String ALTITUDE   = "alt";
    private static final String LASTUPDATE = "created_at";
    private static final String ACCURACY   = "acc";
    private static final String LATITUDE   = "lat";
    private static final String LONGITUDE  = "lon";

    protected static final String HTTP_BASIC_AUTH_USER = "httpUser";

    private ReactiveWebClient  client;
    private GeoMapper          geoMapper;
    private final ObjectMapper mapper;
    private final Logger       logger = LoggerFactory.getLogger(getClass());

    private int deviceId;

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
    public Flux<Val> connect(JsonNode settings) {

        deviceId  = getDeviceId(settings);
        client    = new ReactiveWebClient(mapper);
        geoMapper = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
        var url = String.format("%s://%s/api/0/last?user=%s&device=%s", getProtocol(settings), getServer(settings),
                getUser(settings), deviceId);

        try {
            return getFlux(getHttpBasicAuthUser(settings), getPassword(settings), url,
                    getResponseFormat(settings, mapper), mapper, getLatitudeFirst(settings)).map(Val::of);

        } catch (Exception e) {
            return Flux.just(Val.error(e.getMessage()));
        }

    }

    private Flux<ObjectNode> getFlux(String httpBasicAuthUser, String password, String url, GeoPipResponseFormat format,
            ObjectMapper mapper, boolean latitudeFirst) {

        var html1 = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s"
                """;

        html1 = String.format(html1, url, MediaType.APPLICATION_JSON_VALUE);

        if (!httpBasicAuthUser.equals("none") && !password.equals("none")) {

            var valueToEncode   = String.format("%s:%s", httpBasicAuthUser, password);
            var basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());

            var html2 = """
                        ,
                        "headers" : {
                           Authorization": "%s"
                        }
                      }
                    """;
            html2 = String.format(html2, basicAuthHeader);
            html1 = html1.concat(html2);
        } else {
            html1 = html1.concat("}");

        }

        Val request;
        try {
            request = Val.ofJson(html1);

        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        var flux = client.httpRequest(HttpMethod.GET, request)
                .flatMap(v -> mapPosition(v.get(), format, mapper, latitudeFirst))
                .map(res -> mapper.convertValue(res, ObjectNode.class));
        logger.info("OwnTracks-Client connected.");
        return flux;
    }

    public Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) {

        var response = geoMapper.mapPosition(deviceId, in.get(0), format, latitudeFirst);
        var res      = in.findValue("inregions");

        response.setGeoFences(geoMapper.mapOwnTracksInRegions(res, mapper));

        return Flux.just(response);

    }

    private static String getHttpBasicAuthUser(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(HTTP_BASIC_AUTH_USER)) {
            return requestSettings.findValue(HTTP_BASIC_AUTH_USER).asText();
        } else {
            return "none";

        }

    }

    protected static String getPassword(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PASSWORD_CONST)) {
            return requestSettings.findValue(PASSWORD_CONST).asText();
        } else {

            return "none";
        }

    }
}
