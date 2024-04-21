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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.geo.connection.shared.GeoMapper;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TraccarSessionHandler {

    private ObjectMapper mapper;
    private GeoMapper    geoMapper;
    private int          deviceId;

    private static final String DEVICE_ID  = "deviceId";
    private static final String POSITIONS  = "positions";
    private static final String ALTITUDE   = "altitude";
    private static final String LASTUPDATE = "fixTime";
    private static final String ACCURACY   = "accuracy";
    private static final String LATITUDE   = "latitude";
    private static final String LONGITUDE  = "longitude";

    private TraccarRestManager rest;

    TraccarSessionHandler(int deviceId, String sessionCookie, String serverName, String protocol, ObjectMapper mapper) {
        this.deviceId = deviceId;
        this.mapper   = mapper;
        geoMapper     = new GeoMapper(deviceId, LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
        this.rest     = new TraccarRestManager(sessionCookie, serverName, protocol, mapper);
    }

    Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst) {
        JsonNode pos = getPositionFromMessage(in, deviceId);

        if (pos.has(DEVICE_ID)) {

            return Flux.just(geoMapper.mapPosition(pos, format, latitudeFirst));
        }

        return Flux.just();
    }

    private JsonNode getPositionFromMessage(JsonNode in, int deviceId) {

        if (in.has(POSITIONS)) {
            ArrayNode pos1 = (ArrayNode) in.findValue(POSITIONS);
            for (var p : pos1) {
                if (p.findValue(DEVICE_ID).toPrettyString().equals(Integer.toString(deviceId))) {
                    return p;
                }
            }
        }

        return mapper.createObjectNode();

    }

    Mono<GeoPipResponse> getGeofences(GeoPipResponse response, GeoPipResponseFormat format, boolean latitudeFirst) {

        if (response.getDeviceId() != 0) {
            return rest.getGeofences(Integer.toString(deviceId))
                    .flatMap(fences -> mapGeofences(response, format, fences, latitudeFirst));
        }
        return Mono.just(response);
    }

    Mono<GeoPipResponse> mapGeofences(GeoPipResponse response, GeoPipResponseFormat format, JsonNode in,
            boolean latitudeFirst) {
        List<Geofence> fenceRes = new ArrayList<>();

        try {

            fenceRes = geoMapper.mapTraccarGeoFences(in, format, mapper, latitudeFirst);

        } catch (Exception e) {
            return Mono.error(e);
        }

        response.setGeoFences(fenceRes);
        return Mono.just(response);

    }

}
