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
import io.sapl.geo.common.TestBase;

@TestInstance(Lifecycle.PER_CLASS)
class GmlConverterTest extends TestBase {

    @BeforeAll
    void setup() throws TransformerException {

        var stringWriter = new StringWriter();
        var pnt          = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var plg          = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);

        source.getTransform().transform(new DOMSource(pnt), new StreamResult(stringWriter));
        point = Val.of(stringWriter.toString());
        stringWriter.getBuffer().setLength(0);// clean buffer
        source.getTransform().transform(new DOMSource(plg), new StreamResult(stringWriter));
        polygon = Val.of(stringWriter.toString());
    }

    @Test
    void gmlToGeoJsonTest() throws SAXException, IOException, ParserConfigurationException {

        var res        = geoConverter.gmlToGeoJson(point);
        var res1       = geoConverter.gmlToGeoJson(polygon);
        var expPoint   = source.getJsonSource().get("Point").toPrettyString();
        var expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void gmlToGeometryTest() throws SAXException, IOException, ParserConfigurationException {

        var res        = (Point) GmlConverter.gmlToGeometry(point);
        var res1       = (Polygon) GmlConverter.gmlToGeometry(polygon);
        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, res);
        assertEquals(expPolygon, res1);
    }

    @Test
    void gmlToKmlTest() throws TransformerException, SAXException, IOException, ParserConfigurationException {

        var res  = geoConverter.gmlToKml(point);
        var res1 = geoConverter.gmlToKml(polygon);
        var pnt1 = source.getXmlSource().getElementsByTagName("Point").item(0);
        var plg1 = source.getXmlSource().getElementsByTagName("Polygon").item(0);

        var stringWriter = new StringWriter();
        source.getTransform().transform(new DOMSource(pnt1), new StreamResult(stringWriter));
        var expPoint = stringWriter.toString();
        stringWriter.getBuffer().setLength(0);// clean buffer
        source.getTransform().transform(new DOMSource(plg1), new StreamResult(stringWriter));
        var expPolygon = stringWriter.toString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void gmlToWktTest() throws SAXException, IOException, ParserConfigurationException {

        var res        = geoConverter.gmlToWkt(point);
        var res1       = geoConverter.gmlToWkt(polygon);
        var expPoint   = source.getJsonSource().get("WktPoint").asText();
        var expPolygon = source.getJsonSource().get("WktPolygon").asText();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }
}
