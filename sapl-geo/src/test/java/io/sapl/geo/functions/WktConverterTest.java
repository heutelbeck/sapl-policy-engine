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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.common.TestBase;

@TestInstance(Lifecycle.PER_CLASS)
class WktConverterTest extends TestBase {

	@BeforeAll
	void setup() {
		point = Val.of(source.getJsonSource().get("WktPoint").asText());
		polygon = Val.of(source.getJsonSource().get("WktPolygon").asText());
	}

	@Test
	void wktToKmlTest() throws TransformerException, ParseException {

		final var result = geoConverter.wktToKml(point);
		final var result1 = geoConverter.wktToKml(polygon);
		final var pnt1 = source.getXmlSource().getElementsByTagName("Point").item(0);
		final var plg1 = source.getXmlSource().getElementsByTagName("Polygon").item(0);

		final var stringWriter = new StringWriter();
		source.getTransform().transform(new DOMSource(pnt1), new StreamResult(stringWriter));
		final var expPoint = stringWriter.toString();
		stringWriter.getBuffer().setLength(0); // clean buffer
		source.getTransform().transform(new DOMSource(plg1), new StreamResult(stringWriter));
		final var expPolygon = stringWriter.toString();

		assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(result.getText()));
		assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(result1.getText()));
	}

	@Test
	void wktToGeometryTest() throws ParseException {

		final var result = (Point) WktConverter.wktToGeometry(point);
		final var result1 = (Polygon) WktConverter.wktToGeometry(polygon);
		final var expPoint = source.getPoint();
		final var expPolygon = source.getPolygon();

		assertEquals(expPoint, result);
		assertEquals(expPolygon, result1);
	}

	@Test
	void wktToGeometryFactoryTest() throws ParseException {

		final var factory = new GeometryFactory(new PrecisionModel(), 4326);
		final var result = (Point) WktConverter.wktToGeometry(point, factory);
		final var result1 = (Polygon) WktConverter.wktToGeometry(polygon, factory);
		final var expPoint = source.getPoint();
		final var expPolygon = source.getPolygon();

		assertEquals(expPoint, result);
		assertEquals(expPolygon, result1);
	}

	@Test
	void wktToGMLTest() throws TransformerException, ParseException {

		final var result = geoConverter.wktToGml(point);
		final var result1 = geoConverter.wktToGml(polygon);
		final var pnt1 = source.getXmlSource().getElementsByTagName("gml:Point").item(0);
		final var plg1 = source.getXmlSource().getElementsByTagName("gml:Polygon").item(0);

		final var stringWriter = new StringWriter();
		source.getTransform().transform(new DOMSource(pnt1), new StreamResult(stringWriter));
		final var expPoint = stringWriter.toString();
		stringWriter.getBuffer().setLength(0); // clean buffer
		source.getTransform().transform(new DOMSource(plg1), new StreamResult(stringWriter));
		final var expPolygon = stringWriter.toString();

		assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(result.getText()));
		assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(result1.getText()));
	}

	@Test
	void wktToGeoJsonTest() throws ParseException, JsonProcessingException {

		final var result = geoConverter.wktToGeoJson(point);
		final var result1 = geoConverter.wktToGeoJson(polygon);
		final var expPoint = source.getJsonSource().get("Point").toPrettyString();
		final var expPolygon = source.getJsonSource().get("Polygon").toPrettyString();

		assertEquals(StringUtils.trimAllWhitespace(expPoint), StringUtils.trimAllWhitespace(result.getText()));
		assertEquals(StringUtils.trimAllWhitespace(expPolygon), StringUtils.trimAllWhitespace(result1.getText()));
	}
}
