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

//import static org.junit.Assert.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import org.springframework.util.StringUtils;

//import org.hamcrest.MatcherAssert.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
class GeometryConverterTest extends TestBase {

    @Test
    void geometryToGMLTest() {

        String       expPoint   = EMPTY_STRING;
        String       expPolygon = EMPTY_STRING;
        StringWriter sw         = new StringWriter();

        var pnt = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var plg = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);
        try {
            source.getTransform().transform(new DOMSource(pnt), new StreamResult(sw));
            expPoint = sw.toString();
            sw       = new StringWriter();
            source.getTransform().transform(new DOMSource(plg), new StreamResult(sw));
            expPolygon = sw.toString();
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        var res  = GeometryConverter.geometryToGML(source.getPoint());
        var res1 = GeometryConverter.geometryToGML(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void geometryToKMLTest() {

        String       expPoint   = EMPTY_STRING;
        String       expPolygon = EMPTY_STRING;
        StringWriter sw         = new StringWriter();

        var pnt = source.getXmlSource().getElementsByTagName("Point").item(0);
        var plg = source.getXmlSource().getElementsByTagName("Polygon").item(0);
        try {
            source.getTransform().transform(new DOMSource(pnt), new StreamResult(sw));
            expPoint = sw.toString();
            sw       = new StringWriter();
            source.getTransform().transform(new DOMSource(plg), new StreamResult(sw));
            expPolygon = sw.toString();
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        var res  = GeometryConverter.geometryToKML(source.getPoint());
        var res1 = GeometryConverter.geometryToKML(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void geometryToGeoJsonNodeTest() {

        String expPoint   = EMPTY_STRING;
        String expPolygon = EMPTY_STRING;

        expPoint   = source.getJsonSource().get("Point").toPrettyString();
        expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        var res  = GeometryConverter.geometryToGeoJsonNode(source.getPoint()).get().toPrettyString();
        var res1 = GeometryConverter.geometryToGeoJsonNode(source.getPolygon()).get().toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1));
    }

    @Test
    void geometryToWktTest() {

        String expPoint   = EMPTY_STRING;
        String expPolygon = EMPTY_STRING;

        expPoint   = source.getJsonSource().get("WktPoint").asText();
        expPolygon = source.getJsonSource().get("WktPolygon").asText();

        var res  = GeometryConverter.geometryToWKT(source.getPoint());
        var res1 = GeometryConverter.geometryToWKT(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));

    }

}
