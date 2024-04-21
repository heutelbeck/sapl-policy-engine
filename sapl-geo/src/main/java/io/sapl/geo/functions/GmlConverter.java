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

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.gml2.GMLReader;
import org.xml.sax.SAXException;

import io.sapl.api.functions.Function;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GmlConverter {

	private static final String GML_TO_KML   		= "converts GML to KML";
	private static final String GML_TO_GEOJSON   	= "converts GML to GeoJSON";
	private static final String GML_TO_WKT   		= "converts GML to WKT";
	
	
	@Function(name = "gmlToKml", docs = GML_TO_KML)
    public static Val gmlToKml(Val gml) {

        try {
            return GeometryConverter.geometryToKML(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }
	
	@Function(name = "gmlToGeoJsonString", docs = GML_TO_GEOJSON)
    public static Val gmlToGeoJsonString(Val gml) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

	@Function(name = "equalsExact", docs = GML_TO_WKT)
    public static Val gmlToWkt(Val gml) {

        try {
            return GeometryConverter.geometryToWKT(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    public static Geometry gmlToGeometry(Val gml) throws SAXException, IOException, ParserConfigurationException {

        return gmlToGeometry(gml.getText());
    }

    public static Geometry gmlToGeometry(Val gml, GeometryFactory factory)
            throws SAXException, IOException, ParserConfigurationException {

        return gmlToGeometry(gml.getText(), factory);
    }

    public static Geometry gmlToGeometry(String gml) throws SAXException, IOException, ParserConfigurationException {

        return (new GMLReader()).read(gml, null);
    }

    public static Geometry gmlToGeometry(String gml, GeometryFactory factory)
            throws SAXException, IOException, ParserConfigurationException {

        return (new GMLReader()).read(gml, factory);
    }
}
