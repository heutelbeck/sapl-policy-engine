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
package io.sapl.geofunctions;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.gml2.GMLReader;
import org.xml.sax.SAXException;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GmlConverter {

    // public static String gmlToKML(String gml) throws SAXException, IOException,
    // ParserConfigurationException{
    //
    // return GeometryConverter.geometryToKML(gmlToGeometry(gml));
    // }

    public static Val gmlToKML(Val gml) {

        try {
            return GeometryConverter.geometryToKML(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    public static Val gmlToGeoJsonString(Val gml) {

        try {
            return GeometryConverter.geometryToGeoJsonNode(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    public static Val gmlToWKT(Val gml) {

        try {
            return GeometryConverter.geometryToWKT(gmlToGeometry(gml));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            return Val.error(e);
        }
    }

    public static Geometry gmlToGeometry(Val gml) throws SAXException, IOException, ParserConfigurationException {

        return (new GMLReader()).read(gml.getText(), null);
    }

}
