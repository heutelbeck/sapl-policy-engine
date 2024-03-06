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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.model.traccar.TraccarGeofence;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geofunctions.GeometryConverter;
import io.sapl.geofunctions.WktConverter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TraccarSessionHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEVICEID    = "deviceId";
    private static final String POSITIONS   = "positions";
    private static final String ALTITUDE    = "altitude";
    private static final String FIXTIME     = "fixTime";
    private static final String ACCURACY    = "accuracy";
    private static final String FENCENAME   = "name";
    private static final String AREA        = "area";
    private static final String LATITUDE    = "latitude";
    private static final String LONGITUDE   = "longitude";
    private static final String ATTRIBUTES  = "attributes";
    private static final String DESCRIPTION = "description";
    private static final String CALENDARID  = "calendarId";
    private static final String ID          = "id";
    // private final Logger logger = LoggerFactory.getLogger(getClass());
    private TraccarRestManager rest;

    public TraccarSessionHandler(String sessionCookie, String serverName, String protocol, ObjectMapper mapper) {

        this.rest = new TraccarRestManager(sessionCookie, serverName, protocol, mapper);
    }

    public Flux<GeoPipResponse> mapPosition(JsonNode in, int deviceId, GeoPipResponseFormat format) {
        JsonNode pos = getPositionFromMessage(in, deviceId);

        if (pos.has(DEVICEID)) {

            GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
            var             position        = geometryFactory.createPoint(
                    new Coordinate(pos.findValue(LATITUDE).asDouble(), pos.findValue(LONGITUDE).asDouble()));
            JsonNode        posRes          = MAPPER.createObjectNode();
            switch (format) {
            case GEOJSON:

                posRes = GeometryConverter.geometryToGeoJsonNode(position).get();
                break;

            case WKT:
                posRes = GeometryConverter.geometryToWKT(position).get();
                break;

            case GML:
                posRes = GeometryConverter.geometryToGML(position).get();
                break;

            case KML:
                posRes = GeometryConverter.geometryToKML(position).get();
                break;

            default:

                break;
            }

            return Flux.just(GeoPipResponse.builder().deviceId(deviceId).position(posRes)
                    .altitude(pos.findValue(ALTITUDE).asDouble()).lastUpdate(pos.findValue(FIXTIME).asText())
                    .accuracy(pos.findValue(ACCURACY).asDouble()).build());
        }

        return Flux.just();
    }

    private JsonNode getPositionFromMessage(JsonNode in, int deviceId) {

        if (in.has(POSITIONS)) {
            ArrayNode pos1 = (ArrayNode) in.findValue(POSITIONS);
            for (var p : pos1) {
                if (p.findValue(DEVICEID).toPrettyString().equals(Integer.toString(deviceId))) {
                    return p;
                }
            }
        }

        return MAPPER.createObjectNode();

    }

    public Mono<GeoPipResponse> getGeofences(GeoPipResponse response, int deviceId, GeoPipResponseFormat format) {

        if (response.getDeviceId() != 0) {
            return rest.getGeofences(Integer.toString(deviceId))
                    .flatMap(fences -> mapGeofences(response, format, fences));
        }
        return Mono.just(response);
    }

    private Mono<GeoPipResponse> mapGeofences(GeoPipResponse response, GeoPipResponseFormat format, JsonNode in) {
        JsonNode              fences   = MAPPER.createArrayNode();
        List<TraccarGeofence> fenceRes = new ArrayList<>();

        try {
            fences = MAPPER.readTree(in.toString());

            for (JsonNode geoFence : fences) {
                var      factory = new GeometryFactory(new PrecisionModel(), 4326);
                Geometry geo     = WktConverter.wktToGeometry(Val.of(geoFence.findValue(AREA).asText()), factory);
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

                default:

                    break;
                }

            }

        } catch (Exception e) {
            return Mono.error(e);
        }

        response.setGeoFences(fenceRes);
        return Mono.just(response);

    }

    private TraccarGeofence mapFence(JsonNode geoFence, JsonNode area) {

        return TraccarGeofence.builder().id(geoFence.findValue(ID).asInt()).attributes(geoFence.findValue(ATTRIBUTES))
                .calendarId(geoFence.findValue(CALENDARID).asInt()).name(geoFence.findValue(FENCENAME).asText())
                .description(geoFence.findValue(DESCRIPTION).asText()).area(area).build();
    }

}
