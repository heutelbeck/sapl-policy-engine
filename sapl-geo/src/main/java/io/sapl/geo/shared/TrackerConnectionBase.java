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
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;

public abstract class TrackerConnectionBase extends ConnectionBase {

    protected String              altitude;
    protected String              lastupdate;
    protected String              accuracy;
    protected String              latitude;
    protected String              longitude;
    protected String              baseUrl;
    protected static final String DEVICEID_CONST = "deviceId";
    private static final int      WGS84          = 4326;

    protected GeoPipResponse mapPosition(String deviceId, JsonNode in, GeoPipResponseFormat format,
            boolean latitudeFirst) throws JsonProcessingException {

        var   geometryFactory = new GeometryFactory(new PrecisionModel(), WGS84);
        Point position;
        var   lat             = in.findValue(latitude).asDouble();
        var   lon             = in.findValue(longitude).asDouble();
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

    protected Val createRequestTemplate(String baseUrl, String path, String mediaType, String[] headers,
            String[] urlParameters, Long pollingInterval, Long repetitions) throws JsonProcessingException {

        var template = new StringBuilder(String.format("{\"baseUrl\" : \"%s\", \"path\" : \"%s\", \"accept\" : \"%s\"",
                baseUrl, path, mediaType));

        appendHeaders(template, headers);
        appendPollingInterval(template, pollingInterval);
        appendRepetitions(template, repetitions);
        appendUrlParameters(template, urlParameters);

        template.append('}');
        return Val.ofJson(template.toString());
    }

    private void appendHeaders(StringBuilder template, String[] headers) {
        if (headers != null && headers.length > 0) {
            template.append(", \"headers\" : {");
            for (int i = 0; i < headers.length; i++) {
                template.append(headers[i]);
                if (i < headers.length - 1) {
                    template.append(", ");
                }
            }
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
        if (urlParameters != null && urlParameters.length > 0) {
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

    protected String getDeviceId(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asText();
        } else {
            throw new PolicyEvaluationException("No Device ID found");
        }
    }

    protected String getProtocol(JsonNode requestSettings) {
        if (requestSettings.has(PROTOCOL_CONST)) {
            return requestSettings.findValue(PROTOCOL_CONST).asText();
        } else {
            return "https";
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
