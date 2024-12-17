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
package io.sapl.geo.library;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.io.gml2.GMLWriter;
import org.locationtech.jts.io.kml.KMLWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class GeometryConverter {

    private static final GMLWriter     GML_WRITER     = new GMLWriter();
    private static final KMLWriter     KML_WRITER     = new KMLWriter();
    private static final WKTWriter     WKT_WRITER     = new WKTWriter();
    private static final GeoJsonWriter GEOJSON_WRITER = new GeoJsonWriter();

    static {
        // reader has problems if /n occurs in coordinates
        GML_WRITER.setMaxCoordinatesPerLine(Integer.MAX_VALUE);
        KML_WRITER.setMaximumCoordinatesPerLine(Integer.MAX_VALUE);
        WKT_WRITER.setMaxCoordinatesPerLine(Integer.MAX_VALUE);
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the GML string
     */
    public static Val geometryToGML(Geometry geo) {
        return Val.of(GML_WRITER.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the KML string
     */
    public static Val geometryToKML(Geometry geo) {
        return Val.of(KML_WRITER.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the WKT string
     */
    public static Val geometryToWKT(Geometry geo) {
        return Val.of(WKT_WRITER.write(geo));
    }

    /**
     * @param geo a {@link Geometry}
     * @return a {@link Val} containing the GeoJSON value
     * @throws JsonProcessingException
     * @throws JsonMappingException
     */
    public static Val geometryToGeoJsonNode(Geometry geo) throws JsonProcessingException {
        return Val.ofJson(GEOJSON_WRITER.write(geo));
    }
}
