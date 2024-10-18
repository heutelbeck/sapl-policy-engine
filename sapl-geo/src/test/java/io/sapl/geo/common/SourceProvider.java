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
package io.sapl.geo.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

public final class SourceProvider {

	GeometryFactory geometryFactory = new GeometryFactory();
	Coordinate[] coordinates = new Coordinate[] { new Coordinate(10, 12), new Coordinate(10, 14),
			new Coordinate(12, 10), new Coordinate(13, 14), new Coordinate(10, 12) };

	@Getter
	Point point = geometryFactory.createPoint(coordinates[0]);
	@Getter
	Polygon polygon = geometryFactory.createPolygon(coordinates);

	@Getter
	Document xmlSource;
	@Getter
	JsonNode jsonSource;
	@Getter
	Transformer transform;

	final String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

	public SourceProvider() {
		try {
			setUp();
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize SourceProvider", e);
		}
	}

	public final void setUp() throws TransformerConfigurationException, TransformerFactoryConfigurationError,
			ParserConfigurationException, SAXException, IOException {

		final var factory = DocumentBuilderFactory.newInstance();
		transform = TransformerFactory.newInstance().newTransformer();
		transform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		final var f = new File(resourceDirectory + "/xmlSource.xml");
		final var builder = factory.newDocumentBuilder();
		xmlSource = builder.parse(f);
		final var mapper = new ObjectMapper();
		jsonSource = mapper.readTree(new File(resourceDirectory + "/jsonSource.json"));
	}
}
