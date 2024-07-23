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
package io.sapl.geo.shared;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;

public class TrackerConnectionBase extends ConnectionBase {

    protected static final String DEVICEID_CONST = "deviceId";

    protected String altitude;
    protected String lastupdate;
    protected String accuracy;
    protected String latitude;
    protected String longitude;

    protected GeoPipResponse mapPosition(int deviceId, JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst)
            throws JsonProcessingException {

        var   geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point position;

        var lat = in.findValue(latitude).asDouble();
        var lon = in.findValue(longitude).asDouble();

        if (!latitudeFirst) {
            position = geometryFactory.createPoint(new Coordinate(lon, lat));

        } else {
            position = geometryFactory.createPoint(new Coordinate(lat, lon));
        }

        var posRes = (JsonNode) mapper.createObjectNode();

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

        return GeoPipResponse.builder().deviceId(deviceId).position(posRes).altitude(in.findValue(altitude).asDouble())
                .lastUpdate(in.findValue(lastupdate).asText()).accuracy(in.findValue(accuracy).asDouble()).build();
    }

    protected static Integer getDeviceId(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asInt();
        } else {

            throw new PolicyEvaluationException("No Device ID found");
        }

    }

    protected static String getProtocol(JsonNode requestSettings) {
        if (requestSettings.has(PROTOCOL_CONST)) {
            return requestSettings.findValue(PROTOCOL_CONST).asText();
        } else {

            return "https";
        }
    }
}
