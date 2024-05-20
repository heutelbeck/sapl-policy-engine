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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.sapl.api.interpreter.Val;

@TestInstance(Lifecycle.PER_CLASS)
class KmlConverterTest extends TestBase {

    @BeforeAll
    void setup() throws TransformerException {
        var stringWriter = new StringWriter();
        var pnt          = source.getXmlSource().getElementsByTagName("Point").item(0);
        var plg          = source.getXmlSource().getElementsByTagName("Polygon").item(0);

        source.getTransform().transform(new DOMSource(pnt), new StreamResult(stringWriter));
        point        = Val.of(stringWriter.toString());
        stringWriter = new StringWriter();
        source.getTransform().transform(new DOMSource(plg), new StreamResult(stringWriter));
        polygon = Val.of(stringWriter.toString());

    }

    @Test
    void kmlToGeoJsonTest() throws ParseException, JsonMappingException, JsonProcessingException {

        var result  = geoConverter.kmlToGeoJson(point);
        var result1 = geoConverter.kmlToGeoJson(polygon);

        var expPoint   = source.getJsonSource().get("Point").toPrettyString();
        var expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(result.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(result1.getText()));
    }

    @Test
    void kmlToGeometryTest() throws ParseException {

        var result  = (Point) KmlConverter.kmlToGeometry(point);
        var result1 = (Polygon) KmlConverter.kmlToGeometry(polygon);

        var expPoint   = source.getPoint();
        var expPolygon = source.getPolygon();

        assertEquals(expPoint, result);
        assertEquals(expPolygon, result1);

    }

    @Test
    void kmlToGMLTest() throws TransformerException, ParseException {

        var res  = geoConverter.kmlToGml(point);
        var res1 = geoConverter.kmlToGml(polygon);

        var stringWriter = new StringWriter();

        var point   = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var polygon = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);

        stringWriter = new StringWriter();
        source.getTransform().transform(new DOMSource(point), new StreamResult(stringWriter));
        var expPoint = stringWriter.toString();
        stringWriter = new StringWriter();
        source.getTransform().transform(new DOMSource(polygon), new StreamResult(stringWriter));
        var expPolygon = stringWriter.toString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));

    }

    @Test
    void kmlToWKTTest() throws ParseException {

        var result  = geoConverter.kmlToWkt(point);
        var result1 = geoConverter.kmlToWkt(polygon);

        var expPoint   = source.getJsonSource().get("WktPoint").asText();
        var expPolygon = source.getJsonSource().get("WktPolygon").asText();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(result.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(result1.getText()));
    }

}
