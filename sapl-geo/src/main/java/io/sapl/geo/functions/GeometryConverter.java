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
package io.sapl.geo.functions;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.io.gml2.GMLWriter;
import org.locationtech.jts.io.kml.KMLWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeometryConverter {

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the GML-string}
     */
    public static Val geometryToGML(Geometry geo) {
        final var writer = new GMLWriter();
        writer.setMaxCoordinatesPerLine(Integer.MAX_VALUE);// reader has problems if /n occurs in coordinates
        return Val.of(writer.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the KML-string}
     */
    public static Val geometryToKML(Geometry geo) {
        final var writer = new KMLWriter();
        writer.setMaximumCoordinatesPerLine(Integer.MAX_VALUE); // reader has problems if /n occurs in coordinates
        return Val.of(writer.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the WKT-string}
     */
    public static Val geometryToWKT(Geometry geo) {
        final var writer = new WKTWriter();
        writer.setMaxCoordinatesPerLine(Integer.MAX_VALUE);// reader has problems if /n occurs in coordinates
        return Val.of(writer.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the GeoJSON-string}
     * @throws JsonProcessingException
     * @throws JsonMappingException
     */
    public static Val geometryToGeoJsonNode(Geometry geo) throws JsonProcessingException {
        JsonNode  json          = null;
        final var mapper        = new ObjectMapper();
        final var geoJsonWriter = new GeoJsonWriter();
        json = mapper.readTree(geoJsonWriter.write(geo));
        return Val.of(json);
    }
}
