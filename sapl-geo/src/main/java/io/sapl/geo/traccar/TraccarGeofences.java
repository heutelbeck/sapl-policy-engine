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

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeoProjector;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.WktConverter;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public final class TraccarGeofences extends TraccarBase {

    private static final String FENCENAME   = "name";
    private static final String AREA        = "area";
    private static final String ATTRIBUTES  = "attributes";
    private static final String DESCRIPTION = "description";
    private static final String CALENDARID  = "calendarId";
    private static final String ID          = "id";
    private static final String EPSG        = "EPSG:4326";

    /**
     * @param auth a {@link JsonNode} containing the settings for authorization
     */
    public TraccarGeofences(JsonNode auth, ObjectMapper mapper) {

        user        = getUser(auth);
        password    = getPassword(auth);
        server      = getServer(auth);
        protocol    = getProtocol(auth);
        this.mapper = mapper;
    }

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux} of {@link Val}
     */
    public Flux<Val> getGeofences(JsonNode settings) {

        var deviceId = getDeviceId(settings);

        return establishSession(user, password, server, protocol).flatMapMany(cookie ->

        {
            try {
                return getFlux(getResponseFormat(settings, mapper), deviceId, protocol, server,
                        getPollingInterval(settings), getRepetitions(settings), getLatitudeFirst(settings)).map(Val::of)
                        .onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
            } catch (JsonProcessingException e) {
                return Flux.error(e);
            }
        });
    }

    private Flux<JsonNode> getFlux(GeoPipResponseFormat format, String deviceId, String protocol, String server,
            Long pollingInterval, Long repetitions, boolean latitudeFirst) throws JsonProcessingException {

        var flux = getGeofences1(format, deviceId, protocol, server, pollingInterval, repetitions, latitudeFirst)
                .map(res -> mapper.convertValue(res, JsonNode.class));

        log.info("Traccar-Client connected.");
        return flux;

    }

    private Flux<List<Geofence>> getGeofences1(GeoPipResponseFormat format, String deviceId, String protocol,
            String server, Long pollingInterval, Long repetitions, boolean latitudeFirst)
            throws JsonProcessingException {

        return getGeofences(deviceId, protocol, server, pollingInterval, repetitions)
                .flatMap(fences -> mapGeofences(format, fences, latitudeFirst));

    }

    private Flux<JsonNode> getGeofences(String deviceId, String protocol, String server, Long pollingInterval,
            Long repetitions) throws JsonProcessingException {

        var webClient = new ReactiveWebClient(mapper);
        var baseURL   = protocol + "://" + server;

        var template = """
                {"baseUrl" : "%s", "path" : "%s", "accept" : "%s", "headers" : { "cookie" : "%s" }
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
                    , "urlParameters" : { "deviceId":%s }
                    """);
            template = String.format(template, deviceId);
        }

        template = template.concat("}");
        var request = Val.ofJson(template);

        return webClient.httpRequest(HttpMethod.GET, request).map(Val::get);
    }

    private Mono<List<Geofence>> mapGeofences(GeoPipResponseFormat format, JsonNode in, boolean latitudeFirst) {

        try {
            var fenceRes = mapTraccarGeoFences(in, format, mapper, latitudeFirst);
            return Mono.just(fenceRes);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    public List<Geofence> mapTraccarGeoFences(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) throws JsonProcessingException, ParseException, FactoryException,
            MismatchedDimensionException, TransformException {

        List<Geofence> fenceRes = new ArrayList<>();

        var fences = mapper.readTree(in.toString());

        for (JsonNode geoFence : fences) {
            var      factory = new GeometryFactory(new PrecisionModel(), 4326);
            Geometry geo     = WktConverter.wktToGeometry(Val.of(geoFence.findValue(AREA).asText()), factory);

            if (!latitudeFirst) {
                var geoProjector = new GeoProjector(EPSG, false, EPSG, true);
                geo = geoProjector.project(geo);
            }

            switch (format) {
            case GEOJSON:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToGeoJsonNode(geo).get()));
                break;

            case WKT:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToWKT(geo).get()));
                break;

            case GML:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToGML(geo).get()));
                break;

            case KML:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToKML(geo).get()));
                break;

            default:
                break;
            }
        }
        return fenceRes;
    }

    private Geofence mapFence(JsonNode geoFence, JsonNode area) {
        return Geofence.builder().id(geoFence.findValue(ID).asInt()).attributes(geoFence.findValue(ATTRIBUTES))
                .calendarId(geoFence.findValue(CALENDARID).asText()).name(geoFence.findValue(FENCENAME).asText())
                .description(geoFence.findValue(DESCRIPTION).asText()).area(area).build();
    }

    @Override
    protected String getDeviceId(JsonNode requestSettings) {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asText();
        } else {
            return null;
        }
    }

    protected Long getPollingInterval(JsonNode requestSettings) {
        if (requestSettings.has(POLLING_INTERVAL_CONST)) {
            return requestSettings.findValue(POLLING_INTERVAL_CONST).asLong();
        } else {
            return null;
        }
    }

    protected Long getRepetitions(JsonNode requestSettings) {
        if (requestSettings.has(REPEAT_TIMES_CONST)) {
            return requestSettings.findValue(REPEAT_TIMES_CONST).asLong();
        } else {

            return null;
        }
    }

}
