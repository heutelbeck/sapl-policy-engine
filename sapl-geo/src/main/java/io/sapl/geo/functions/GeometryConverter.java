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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeometryConverter {

    private static final String EMPTY = "";

    public static Val geometryToGML(Geometry geo) {

        String s = EMPTY;
        try {
            s = (new GMLWriter()).write(geo);

        } catch (Exception e) {

            return Val.error(e);
        }
        return Val.of(s);

    }

    public static Val geometryToKML(Geometry geo) {
        String s = EMPTY;
        try {
            s = (new KMLWriter().write(geo));

        } catch (Exception e) {

            return Val.error(e);
        }
        return Val.of(s);
    }

    public static Val geometryToWKT(Geometry geo) {
        String s = EMPTY;
        try {
            s = (new WKTWriter().write(geo));
        } catch (Exception e) {
            return Val.error(e);
        }
        return Val.of(s);
    }

    public static Val geometryToGeoJsonNode(Geometry geo) {
        JsonNode json = null;
        try {
            ObjectMapper  mapper        = new ObjectMapper();
            GeoJsonWriter geoJsonWriter = new GeoJsonWriter();
            json = mapper.readTree(geoJsonWriter.write(geo));
        } catch (JsonProcessingException e) {
            return Val.error(e);
        }
        return Val.of(json);
    }

}
