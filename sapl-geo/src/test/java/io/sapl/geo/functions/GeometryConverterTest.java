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

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.geo.common.TestBase;

//import org.hamcrest.MatcherAssert.assertThat;

@TestInstance(Lifecycle.PER_CLASS)
class GeometryConverterTest extends TestBase {

    @Test
    void geometryToGMLTest() throws TransformerException {

        var stringwriter = new StringWriter();

        var point   = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
        var polygon = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);

        source.getTransform().transform(new DOMSource(point), new StreamResult(stringwriter));
        var expPoint = stringwriter.toString();
        stringwriter = new StringWriter();
        source.getTransform().transform(new DOMSource(polygon), new StreamResult(stringwriter));
        var expPolygon = stringwriter.toString();

        var res  = GeometryConverter.geometryToGML(source.getPoint());
        var res1 = GeometryConverter.geometryToGML(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void geometryToKMLTest() throws TransformerException {

        StringWriter stringwriter = new StringWriter();

        var point   = source.getXmlSource().getElementsByTagName("Point").item(0);
        var polygon = source.getXmlSource().getElementsByTagName("Polygon").item(0);

        source.getTransform().transform(new DOMSource(point), new StreamResult(stringwriter));
        var expPoint = stringwriter.toString();
        stringwriter = new StringWriter();
        source.getTransform().transform(new DOMSource(polygon), new StreamResult(stringwriter));
        var expPolygon = stringwriter.toString();

        var res  = GeometryConverter.geometryToKML(source.getPoint());
        var res1 = GeometryConverter.geometryToKML(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));
    }

    @Test
    void geometryToGeoJsonNodeTest() throws JsonProcessingException {

        var expPoint   = source.getJsonSource().get("Point").toPrettyString();
        var expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

        var res  = GeometryConverter.geometryToGeoJsonNode(source.getPoint()).get().toPrettyString();
        var res1 = GeometryConverter.geometryToGeoJsonNode(source.getPolygon()).get().toPrettyString();

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1));
    }

    @Test
    void geometryToWktTest() {

        var expPoint   = source.getJsonSource().get("WktPoint").asText();
        var expPolygon = source.getJsonSource().get("WktPolygon").asText();

        var res  = GeometryConverter.geometryToWKT(source.getPoint());
        var res1 = GeometryConverter.geometryToWKT(source.getPolygon());

        assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(res.getText()));
        assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(res1.getText()));

    }

}
