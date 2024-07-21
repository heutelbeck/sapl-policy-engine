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

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geo.shared.GeoMapper;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TraccarGeofences extends TraccarBase {

    private GeoMapper geoMapper;

    public TraccarGeofences(JsonNode auth, ObjectMapper mapper) {

        super(mapper);
        user     = getUser(auth);
        password = getPassword(auth);
        server   = getServer(auth);
        protocol = getProtocol(auth);
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
    public Flux<Val> getGeofences(JsonNode settings) {

        var deviceId = getDeviceId(settings);
        geoMapper = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);

        return establishSession(user, password, server, protocol).flatMapMany(cookie ->

        getFlux(getResponseFormat(settings, mapper), deviceId, protocol, server, getPollingInterval(settings),
                getRepetitions(settings), getLatitudeFirst(settings)).map(Val::of)
                .onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect()));

    }

    private Flux<JsonNode> getFlux(GeoPipResponseFormat format, Integer deviceId, String protocol, String server,
            Long pollingInterval, Long repetitions, boolean latitudeFirst) throws PolicyEvaluationException {

        try {

            var flux = getGeofences1(format, deviceId, protocol, server, pollingInterval, repetitions, latitudeFirst)
                    .map(res -> mapper.convertValue(res, JsonNode.class));

            logger.info("Traccar-Client connected.");
            return flux;

        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }

    private Flux<List<Geofence>> getGeofences1(GeoPipResponseFormat format, Integer deviceId, String protocol,
            String server, Long pollingInterval, Long repetitions, boolean latitudeFirst) {

        return getGeofences(deviceId, protocol, server, pollingInterval, repetitions)
                .flatMap(fences -> mapGeofences(format, fences, latitudeFirst));

    }

    private Flux<JsonNode> getGeofences(Integer deviceId, String protocol, String server, Long pollingInterval,
            Long repetitions) {

        var webClient = new ReactiveWebClient(mapper);
        var baseURL   = protocol + "://" + server;

        var template = """
                {
                    "baseUrl" : "%s",
                    "path" : "%s",
                    "accept" : "%s",
                    "headers" : {
                        "cookie" : "%s"
                    }
                """;
        template = String.format(template, baseURL, "api/geofences", MediaType.APPLICATION_JSON_VALUE, sessionCookie);
        if (pollingInterval != null) {
            template = template.concat("""
                    ,"pollingIntervalMs" : %s
                    """);
            template = String.format(template, pollingInterval);
        }

        if (repetitions != null) {
            template = template.concat("""
                    ,"repetitions" : %s
                    """);
            template = String.format(template, repetitions);
        }

        if (deviceId != null) {
            template = template.concat("""
                    ,
                    "urlParameters" : {
                        "deviceId":%s
                    }
                    """);
            template = String.format(template, deviceId);
        }

        template = template.concat("}");

        Val request = Val.of("");
        try {

            request = Val.ofJson(template);
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        return webClient.httpRequest(HttpMethod.GET, request).map(Val::get);
    }

    private Mono<List<Geofence>> mapGeofences(GeoPipResponseFormat format, JsonNode in, boolean latitudeFirst) {
        List<Geofence> fenceRes = new ArrayList<>();

        try {

            fenceRes = geoMapper.mapTraccarGeoFences(in, format, mapper, latitudeFirst);

        } catch (Exception e) {
            return Mono.error(e);
        }

        return Mono.just(fenceRes);

    }

    protected static Integer getDeviceId(JsonNode requestSettings) {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asInt();
        } else {

            return null;
        }
    }

    protected static Long getPollingInterval(JsonNode requestSettings) {
        if (requestSettings.has(POLLING_INTERVAL_CONST)) {
            return requestSettings.findValue(POLLING_INTERVAL_CONST).asLong();
        } else {

            return null;
        }
    }

    protected static Long getRepetitions(JsonNode requestSettings) {
        if (requestSettings.has(REPEAT_TIMES_CONST)) {
            return requestSettings.findValue(REPEAT_TIMES_CONST).asLong();
        } else {

            return null;
        }
    }

}
