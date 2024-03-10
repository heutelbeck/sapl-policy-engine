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
package io.sapl.geo.connection.fileimport;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geofunctions.GeometryConverter;
import io.sapl.geofunctions.GmlConverter;
import io.sapl.geofunctions.JsonConverter;
import io.sapl.geofunctions.WktConverter;
import reactor.core.publisher.Flux;

public class FileLoader extends ConnectionBase {

    private static final String PATH = "path";
    private static final String CRS  = "crs";
    private BufferedReader      reader;

    private FileLoader(String path) throws IOException {
        reader = new BufferedReader(new FileReader(path));
    }

    public static FileLoader getNew(String path) throws IOException {
        return new FileLoader(path);
    }

    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
        	var sett = mapper.readTree(settings.asText());
            var loader = getNew(getPath(settings));
            return loader.getFlux(getResponseFormat(settings, mapper), getCrs(settings), mapper).map(Val::of)
                    .onErrorResume(e -> Flux.just(Val.error(e)));

        } catch (Exception e) {

            return Flux.just(Val.error(e));
        }
    }

    public Flux<JsonNode> getFlux(GeoPipResponseFormat format, int crs, ObjectMapper mapper) {
        try {
            return Flux.just(importGeoData(format, crs, mapper));
        } catch (IOException | ParseException e) {
            return Flux.error(e);
        }
    }

    private JsonNode importGeoData(GeoPipResponseFormat format, int crs, ObjectMapper mapper)
            throws IOException, ParseException {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), crs);
        JsonNode        posRes          = mapper.createObjectNode();
        try {
            switch (format) {
            case KML:
                posRes = GeometryConverter
                        .geometryToGeoJsonNode(GmlConverter.gmlToGeometry(readFile(reader), geometryFactory)).get();
                break;
            case GML:
                posRes = GeometryConverter.geometryToGML(GmlConverter.gmlToGeometry(readFile(reader), geometryFactory))
                        .get();
                break;
            case GEOJSON:
                posRes = GeometryConverter
                        .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(readFile(reader), geometryFactory))
                        .get();
                break;
            case WKT:
                posRes = GeometryConverter.geometryToWKT(WktConverter.wktToGeometry(readFile(reader), geometryFactory))
                        .get();
                break;
            }

            return posRes;

        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
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
