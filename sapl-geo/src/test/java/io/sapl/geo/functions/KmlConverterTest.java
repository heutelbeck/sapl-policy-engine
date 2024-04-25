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

    @BeforeAll
    void setup() throws TransformerException {
        StringWriter sw  = new StringWriter();
        var          pnt = source.getXmlSource().getElementsByTagName("Point").item(0);
        var          plg = source.getXmlSource().getElementsByTagName("Polygon").item(0);

        source.getTransform().transform(new DOMSource(pnt), new StreamResult(sw));
        point = Val.of(sw.toString());
        sw    = new StringWriter();
        source.getTransform().transform(new DOMSource(plg), new StreamResult(sw));
        polygon = Val.of(sw.toString());

    }

    @Test
    void kmlToGeoJsonTest() {

        var res  = geoConverter.kmlToGeoJson(point);
        var res1 = geoConverter.kmlToGeoJson(polygon);

        var expPoint   = source.getJsonSource().get("Point").toPrettyString();
        var expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void kmlToGeometryTest() throws ParseException {

        var res  = (Point) KmlConverter.kmlToGeometry(point);
        var res1 = (Polygon) KmlConverter.kmlToGeometry(polygon);

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);

    }

    @Test
    void kmlToGMLTest() throws TransformerException {

        var res  = geoConverter.kmlToGml(point);
        var res1 = geoConverter.kmlToGml(polygon);

        StringWriter sw = new StringWriter();

        var pnt1 = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var plg1 = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);

        sw = new StringWriter();
        source.getTransform().transform(new DOMSource(pnt1), new StreamResult(sw));
        var expPoint = sw.toString();
        sw = new StringWriter();
        source.getTransform().transform(new DOMSource(plg1), new StreamResult(sw));
        var expPolygon = sw.toString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));

    }

    @Test
    void kmlToWKTTest() {

        var res  = geoConverter.kmlToWkt(point);
        var res1 = geoConverter.kmlToWkt(polygon);

        var expPoint   = source.getJsonSource().get("WktPoint").asText();
        var expPolygon = source.getJsonSource().get("WktPolygon").asText();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

}
