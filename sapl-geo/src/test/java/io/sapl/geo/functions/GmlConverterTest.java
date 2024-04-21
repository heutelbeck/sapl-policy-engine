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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.io.StringWriter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.functionlibraries.GeoConverter;

@TestInstance(Lifecycle.PER_CLASS)
class GmlConverterTest extends TestBase {

    String       point   = EMPTY_STRING;
    String       polygon = EMPTY_STRING;
    GeoConverter geoConverter;

    @BeforeAll
    void setup() {
        geoConverter = new GeoConverter();
        StringWriter sw  = new StringWriter();
        var          pnt = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var          plg = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);
        try {
            source.getTransform().transform(new DOMSource(pnt), new StreamResult(sw));
            point = sw.toString();
            sw    = new StringWriter();
            source.getTransform().transform(new DOMSource(plg), new StreamResult(sw));
            polygon = sw.toString();
        } catch (TransformerException e) {

            e.printStackTrace();
        }

    }

    @Test
    void gmlToGeoJsonTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.gmlToGeoJsonString(Val.of(point));
            res1 = geoConverter.gmlToGeoJsonString(Val.of(polygon));
        } catch (NullPointerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        String expPoint   = source.getJsonSource().get("Point").toPrettyString();
        String expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void gmlToGeometryTest() {

        Point   res  = null;
        Polygon res1 = null;
        try {
            res  = (Point) GmlConverter.gmlToGeometry(Val.of(point));
            res1 = (Polygon) GmlConverter.gmlToGeometry(Val.of(polygon));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);

    }

    @Test
    void gmlToKmlTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.gmlToKml(Val.of(point));
            res1 = geoConverter.gmlToKml(Val.of(polygon));

        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        String expPoint = EMPTY_STRING;
        ;
        String expPolygon = EMPTY_STRING;
        ;
        StringWriter sw = new StringWriter();

        var pnt1 = source.getXmlSource().getElementsByTagName("Point").item(0);
        var plg1 = source.getXmlSource().getElementsByTagName("Polygon").item(0);
        try {
            sw = new StringWriter();
            source.getTransform().transform(new DOMSource(pnt1), new StreamResult(sw));
            expPoint = sw.toString();
            sw       = new StringWriter();
            source.getTransform().transform(new DOMSource(plg1), new StreamResult(sw));
            expPolygon = sw.toString();
        } catch (TransformerException e) {

            e.printStackTrace();
        }

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));

    }

    @Test
    void gmlToWktTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.gmlToWkt(Val.of(point));
            res1 = geoConverter.gmlToWkt(Val.of(polygon));

        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        String expPoint   = EMPTY_STRING;
        String expPolygon = EMPTY_STRING;

        expPoint   = source.getJsonSource().get("WktPoint").asText();
        expPolygon = source.getJsonSource().get("WktPolygon").asText();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

}
