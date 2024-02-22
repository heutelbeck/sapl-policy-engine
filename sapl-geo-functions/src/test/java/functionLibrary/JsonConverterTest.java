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
package functionLibrary;

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
import io.sapl.geofunctions.JsonConverter;

@TestInstance(Lifecycle.PER_CLASS)
class JsonConverterTest extends TestBase {

    Val point   = null;
    Val polygon = null;

    @BeforeAll
    void setup() {
        point   = Val.of(source.getJsonSource().get("Point").toPrettyString());
        polygon = Val.of(source.getJsonSource().get("Polygon").toPrettyString());

    }

    @Test
    void geoJsonToKmlTest() {

        Val res = null;
        ;
        Val res1 = null;
        ;
        res  = JsonConverter.geoJsonToKML(point);
        res1 = JsonConverter.geoJsonToKML(polygon);

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
    void geoJsonToGeometryTest() {

        Point   res  = null;
        Polygon res1 = null;
        try {
            res  = (Point) JsonConverter.geoJsonToGeometry(point);
            res1 = (Polygon) JsonConverter.geoJsonToGeometry(polygon);
        } catch (ParseException e) {

            e.printStackTrace();
        }

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);

    }

    @Test
    void geoJsonToGMLTest() {

        Val res  = null;
        Val res1 = null;
        try {
            res  = JsonConverter.geoJsonToGml(point);
            res1 = JsonConverter.geoJsonToGml(polygon);

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
    void geoJsonToWktTest() {

        Val res  = null;
        Val res1 = null;
        res  = JsonConverter.geoJsonToWKT(point);
        res1 = JsonConverter.geoJsonToWKT(polygon);

        String expPoint   = EMPTY_STRING;
        String expPolygon = EMPTY_STRING;

        expPoint   = source.getJsonSource().get("WktPoint").asText();
        expPolygon = source.getJsonSource().get("WktPolygon").asText();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

}
