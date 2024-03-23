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
class WktConverterTest extends TestBase {

    String point   = EMPTY_STRING;
    String polygon = EMPTY_STRING;

    @BeforeAll
    void setup() {
        point   = source.getJsonSource().get("WktPoint").asText();
        polygon = source.getJsonSource().get("WktPolygon").asText();

    }

    @Test
    void wktToKmlTest() {

        Val res  = null;
        Val res1 = null;
        res  = WktConverter.wktToKML(Val.of(point));
        res1 = WktConverter.wktToKML(Val.of(polygon));

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
    void wktToGeometryTest() {

        Point   res  = null;
        Polygon res1 = null;
        try {
            res  = (Point) WktConverter.wktToGeometry(Val.of(point));
            res1 = (Polygon) WktConverter.wktToGeometry(Val.of(polygon));
        } catch (ParseException e) {

            e.printStackTrace();
        }

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);

    }

    @Test
    void wktToGMLTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = WktConverter.wktToGML(Val.of(point));
            res1 = WktConverter.wktToGML(Val.of(polygon));

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
    void wktToGeoJsonTest() {

        Val res  = null;
        Val res1 = null;
        res  = WktConverter.wktToGeoJsonString(Val.of(point));
        res1 = WktConverter.wktToGeoJsonString(Val.of(polygon));

        String expPoint   = EMPTY_STRING;
        String expPolygon = EMPTY_STRING;

        expPoint   = source.getJsonSource().get("Point").toPrettyString();
        expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

}
