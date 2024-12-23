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
import io.sapl.api.interpreter.Val;
import io.sapl.geo.library.GeometryConverter;
import io.sapl.geo.pip.owntracks.GeoPipResponse;
import io.sapl.geo.pip.owntracks.GeoPipResponseFormat;

public abstract class AbstractTrackerConnection extends ConnectionBase {

    private static final int             WGS84            = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    public static final String DEVICEID_CONST = "deviceId";

    public String altitude;
    public String lastupdate;
    public String accuracy;
    public String latitude;
    public String longitude;
    public String baseUrl;

    public GeoPipResponse mapPosition(String deviceId, JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst)
            throws JsonProcessingException {
        Point     position;
        final var lat = in.findValue(latitude).asDouble();
        final var lon = in.findValue(longitude).asDouble();
        if (!latitudeFirst) {
            position = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        } else {
            position = GEOMETRY_FACTORY.createPoint(new Coordinate(lat, lon));
        }
        var posRes = switch (format) {
        case GEOJSON -> GeometryConverter.geometryToGeoJsonNode(position).get();
        case WKT     -> GeometryConverter.geometryToWKT(position).get();
        case GML     -> GeometryConverter.geometryToGML(position).get();
        case KML     -> GeometryConverter.geometryToKML(position).get();
        };
        return GeoPipResponse.builder().deviceId(deviceId).position(posRes).altitude(in.findValue(altitude).asDouble())
                .lastUpdate(in.findValue(lastupdate).asText()).accuracy(in.findValue(accuracy).asDouble()).build();
    }

    public Val createRequestTemplate(String baseUrl, String path, String mediaType, String header,
            String[] urlParameters, Long pollingInterval, Long repetitions) throws JsonProcessingException {
        final var template = new StringBuilder(String
                .format("{\"baseUrl\" : \"%s\", \"path\" : \"%s\", \"accept\" : \"%s\"", baseUrl, path, mediaType));
        appendHeader(template, header);
        appendPollingInterval(template, pollingInterval);
        appendRepetitions(template, repetitions);
        appendUrlParameters(template, urlParameters);

        template.append('}');
        return Val.ofJson(template.toString());
    }

    private void appendHeader(StringBuilder template, String header) {
        if (header != null) {
            template.append(", \"headers\" : {");
            template.append(header);
            template.append('}');
        }
    }

    private void appendPollingInterval(StringBuilder template, Long pollingInterval) {
        if (pollingInterval != null) {
            template.append(String.format(", \"pollingIntervalMs\" : %s", pollingInterval));
        }
    }

    private void appendRepetitions(StringBuilder template, Long repetitions) {
        if (repetitions != null) {
            template.append(String.format(", \"repetitions\" : %s", repetitions));
        }
    }

    private void appendUrlParameters(StringBuilder template, String[] urlParameters) {
        if (urlParameters != null) {
            template.append(", \"urlParameters\" : {");
            for (int i = 0; i < urlParameters.length; i++) {
                template.append(urlParameters[i]);
                if (i < urlParameters.length - 1) {
                    template.append(", ");
                }
            }
            template.append('}');
        }
    }

    public String getDeviceId(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asText();
        } else {
            throw new PolicyEvaluationException("No Device ID found");
        }
    }

    public String getProtocol(JsonNode requestSettings) {
        if (requestSettings.has(PROTOCOL_CONST)) {
            return requestSettings.findValue(PROTOCOL_CONST).asText();
        } else {
            return "https";
        }
    }

    public Long getPollingInterval(JsonNode requestSettings) {
        if (requestSettings.has(POLLING_INTERVAL_CONST)) {
            return requestSettings.findValue(POLLING_INTERVAL_CONST).asLong();
        } else {
            return null;
        }
    }

    public Long getRepetitions(JsonNode requestSettings) {
        if (requestSettings.has(REPEAT_TIMES_CONST)) {
            return requestSettings.findValue(REPEAT_TIMES_CONST).asLong();
        } else {

            return null;
        }
    }
}
