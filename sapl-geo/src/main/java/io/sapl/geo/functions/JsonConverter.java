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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import io.sapl.api.functions.Function;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonConverter {
	
	private static final String GEOJSON_TO_KML   		= "converts GeoJSON to KML";
	private static final String GEOJSON_TO_GML   		= "converts GeoJSON to GML";
	private static final String GEOJSON_TO_WKT   		= "converts GeoJSON to WKT";

	
	@Function(name = "geoJsonToKml", docs = GEOJSON_TO_KML)
    public static Val geoJsonToKml(Val geoJson) {

        try {
            return GeometryConverter.geometryToKML((new GeoJsonReader()).read(geoJson.getText()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

	@Function(name = "geoJsonToGml", docs = GEOJSON_TO_GML  )
    public static Val geoJsonToGml(Val geoJson) {

        try {
            return GeometryConverter.geometryToGML((new GeoJsonReader()).read(geoJson.getText()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

	@Function(name = "geoJsonToWkt", docs = GEOJSON_TO_WKT)
    public static Val geoJsonToWkt(Val geoJson) {

        try {
            return GeometryConverter.geometryToWKT((new GeoJsonReader()).read(geoJson.getText()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public static Geometry geoJsonToGeometry(Val geoJson) throws ParseException {

        return geoJsonToGeometry(geoJson.getText());
    }

    public static Geometry geoJsonToGeometry(Val geoJson, GeometryFactory factory) throws ParseException {

        return geoJsonToGeometry(geoJson.getText(), factory);
    }

    public static Geometry geoJsonToGeometry(String geoJson, GeometryFactory factory) throws ParseException {

        return (new GeoJsonReader(factory)).read(geoJson);
    }

    public static Geometry geoJsonToGeometry(String geoJson) throws ParseException {

        return (new GeoJsonReader()).read(geoJson);
    }

}
