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
package io.sapl.geo.fileimport;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;

import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.GmlConverter;
import io.sapl.geo.functions.JsonConverter;
import io.sapl.geo.functions.KmlConverter;
import io.sapl.geo.functions.WktConverter;
import io.sapl.geo.pip.GeoPipResponseFormat;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

@RequiredArgsConstructor
public class FileLoader extends ConnectionBase {

    private static final String PATH = "path";
    private static final String CRS  = "crs";

    private final ObjectMapper mapper;

    private BufferedReader reader;

    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
    public Flux<Val> connect(JsonNode settings) {

        try {
            reader = new BufferedReader(new FileReader(getPath(settings)));
            return getFlux(getResponseFormat(settings, mapper), getCrs(settings),
                    longOrDefault(settings, REPEAT_TIMES, DEFAULT_REPETITIONS),
                    longOrDefault(settings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS), mapper).map(Val::of)
                    .onErrorResume(e -> Flux.just(Val.error(e)));

        } catch (Exception e) {

            return Flux.just(Val.error(e));
        }
    }

    public Flux<JsonNode> getFlux(GeoPipResponseFormat format, int crs, long repeatTimes, long pollingInterval,
            ObjectMapper mapper) {
        try {
            return poll(Mono.just(importGeoData(format, crs, mapper)), repeatTimes, pollingInterval);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private JsonNode importGeoData(GeoPipResponseFormat format, int crs, ObjectMapper mapper)
            throws PolicyEvaluationException {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), crs);
        try {
            Geometry geometries = convertToGeometry(format, geometryFactory);

            int count = geometries.getNumGeometries();
            if (count > 1) {
                return createArrayNodeForMultipleGeometries(geometries, format, mapper);
            } else {
                return convertGeometryToOutput(geometries, format);
            }
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }
    }

    private Geometry convertToGeometry(GeoPipResponseFormat format, GeometryFactory geometryFactory)
            throws ParseException, IOException, SAXException, ParserConfigurationException {
        switch (format) {
        case KML:
            return KmlConverter.kmlToGeometry(readFile(reader), geometryFactory);
        case GML:
            return GmlConverter.gmlToGeometry(readFile(reader), geometryFactory);
        case GEOJSON:
            return JsonConverter.geoJsonToGeometry(readFile(reader), geometryFactory);
        case WKT:
            return WktConverter.wktToGeometry(readFile(reader), geometryFactory);
        default:
            throw new PolicyEvaluationException("Unsupported format: " + format);
        }
    }

    private JsonNode createArrayNodeForMultipleGeometries(Geometry geometries, GeoPipResponseFormat format,
            ObjectMapper mapper) {
        ArrayNode arrayNode = mapper.createArrayNode();
        for (int i = 0; i < geometries.getNumGeometries(); i++) {
            Geometry geometry = geometries.getGeometryN(i);
            arrayNode.add(convertGeometryToOutput(geometry, format));
        }
        return arrayNode;
    }

    private JsonNode convertGeometryToOutput(Geometry geometry, GeoPipResponseFormat format) {
        switch (format) {
        case KML:
            return GeometryConverter.geometryToKML(geometry).get();
        case GML:
            return GeometryConverter.geometryToGML(geometry).get();
        case GEOJSON:
            return GeometryConverter.geometryToGeoJsonNode(geometry).get();
        case WKT:
            return GeometryConverter.geometryToWKT(geometry).get();
        default:
            throw new PolicyEvaluationException("Unsupported format: " + format);
        }
    }

    private String readFile(BufferedReader reader) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        String        line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }

    private Flux<JsonNode> poll(Mono<JsonNode> mono, long repeatTimes, long pollingInterval) {
        return mono.onErrorResume(Mono::error)
                .repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))));
    }

    private static String getPath(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PATH)) {
            return requestSettings.findValue(PATH).asText();
        } else {
            throw new PolicyEvaluationException("No filepath found");
        }
    }

    private static int getCrs(JsonNode requestSettings) {
        if (requestSettings.has(CRS)) {
            return requestSettings.findValue(CRS).asInt();
        } else {
            return 4326;
        }
    }

}
