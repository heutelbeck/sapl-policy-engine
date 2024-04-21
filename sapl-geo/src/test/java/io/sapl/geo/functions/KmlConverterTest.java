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

import java.io.StringWriter;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.springframework.util.StringUtils;

import io.sapl.api.interpreter.Val;

@TestInstance(Lifecycle.PER_CLASS)
class KmlConverterTest extends TestBase {

    String       point   = EMPTY_STRING;
    String       polygon = EMPTY_STRING;
    GeoConverter geoConverter;

    @BeforeAll
    void setup() {
    	geoConverter = new GeoConverter();
        StringWriter sw  = new StringWriter();
        var          pnt = source.getXmlSource().getElementsByTagName("Point").item(0);
        var          plg = source.getXmlSource().getElementsByTagName("Polygon").item(0);
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
    void kmlToGeoJsonTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.kmlToGeoJsonString(Val.of(point));
            res1 = geoConverter.kmlToGeoJsonString(Val.of(polygon));
        } catch (NullPointerException e) {

            e.printStackTrace();
        }

        String expPoint   = source.getJsonSource().get("Point").toPrettyString();
        String expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void kmlToGeometryTest() {

        Point   res  = null;
        Polygon res1 = null;
        try {
            res  = (Point) KmlConverter.kmlToGeometry(Val.of(point));
            res1 = (Polygon) KmlConverter.kmlToGeometry(Val.of(polygon));
        } catch (ParseException e) {

            e.printStackTrace();
        }

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);

    }

    @Test
    void kmlToGMLTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.kmlToGml(Val.of(point));
            res1 = geoConverter.kmlToGml(Val.of(polygon));

        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        String expPoint = EMPTY_STRING;
        ;
        String expPolygon = EMPTY_STRING;
        ;
        StringWriter sw = new StringWriter();

        var pnt1 = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var plg1 = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);
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
    void kmlToWKTTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = geoConverter.kmlToWkt(Val.of(point));
            res1 = geoConverter.kmlToWkt(Val.of(polygon));

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
