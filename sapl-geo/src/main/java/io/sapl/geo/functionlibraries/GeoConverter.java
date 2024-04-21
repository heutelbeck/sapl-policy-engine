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
package io.sapl.geo.functionlibraries;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.locationtech.jts.io.ParseException;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.GmlConverter;
import io.sapl.geo.functions.JsonConverter;
import io.sapl.geo.functions.KmlConverter;
import io.sapl.geo.functions.WktConverter;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
@FunctionLibrary(name = "geoConverter", description = "")
public class GeoConverter {

    private static final String GML_TO_KML     = "converts GML to KML";
    private static final String GML_TO_GEOJSON = "converts GML to GeoJSON";
    private static final String GML_TO_WKT     = "converts GML to WKT";

    private static final String GEOJSON_TO_KML = "converts GeoJSON to KML";
    private static final String GEOJSON_TO_GML = "converts GeoJSON to GML";
    private static final String GEOJSON_TO_WKT = "converts GeoJSON to WKT";

    private static final String KML_TO_GML     = "converts KML to GML";
    private static final String KML_TO_GEOJSON = "converts KML to GeoJSON";
    private static final String KML_TO_WKT     = "converts KML to WKT";

    private static final String WKT_TO_GML     = "converts WKT to GML";
    private static final String WKT_TO_GEOJSON = "converts WKT to GeoJSON";
    private static final String WKT_TO_KML     = "converts WKT to KML";

    @Function(name = "gmlToKml", docs = GML_TO_KML)
    public Val gmlToKml(Val gml) {

        try {
            return GeometryConverter.geometryToKML(GmlConverter.gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    @Function(name = "gmlToGeoJsonString", docs = GML_TO_GEOJSON)
    public Val gmlToGeoJsonString(Val gml) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(GmlConverter.gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    @Function(name = "equalsExact", docs = GML_TO_WKT)
    public Val gmlToWkt(Val gml) {

        try {
            return GeometryConverter.geometryToWKT(GmlConverter.gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    @Function(name = "geoJsonToKml", docs = GEOJSON_TO_KML)
    public Val geoJsonToKml(Val geoJson) {

        try { // geoJsonToGeometry
//            return GeometryConverter.geometryToKML((new GeoJsonReader()).read(geoJson.getText()));
            return GeometryConverter.geometryToKML(JsonConverter.geoJsonToGeometry(geoJson));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "geoJsonToGml", docs = GEOJSON_TO_GML)
    public Val geoJsonToGml(Val geoJson) {

        try {
            return GeometryConverter.geometryToGML(JsonConverter.geoJsonToGeometry(geoJson));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "geoJsonToWkt", docs = GEOJSON_TO_WKT)
    public Val geoJsonToWkt(Val geoJson) {

        try {
            return GeometryConverter.geometryToWKT(JsonConverter.geoJsonToGeometry(geoJson));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "kmlToGml", docs = KML_TO_GML)
    public Val kmlToGml(Val kml) {

        try {
            return GeometryConverter.geometryToGML(KmlConverter.kmlToGeometry(kml));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "kmlToGeoJsonString", docs = KML_TO_GEOJSON)
    public Val kmlToGeoJsonString(Val kml) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(KmlConverter.kmlToGeometry(kml));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "kmlToWkt", docs = KML_TO_WKT)
    public Val kmlToWkt(Val kml) {

        try {
            return GeometryConverter.geometryToWKT(KmlConverter.kmlToGeometry(kml));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "wktToGml", docs = WKT_TO_GML)
    public Val wktToGml(Val wkt) {

        try {
            return GeometryConverter.geometryToGML(WktConverter.wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "wktToKml", docs = WKT_TO_KML)
    public Val wktToKml(Val wkt) {

        try {
            return GeometryConverter.geometryToKML(WktConverter.wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(name = "wktToGeoJsonString", docs = WKT_TO_GEOJSON)
    public Val wktToGeoJsonString(Val wkt) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(WktConverter.wktToGeometry(wkt));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

}
