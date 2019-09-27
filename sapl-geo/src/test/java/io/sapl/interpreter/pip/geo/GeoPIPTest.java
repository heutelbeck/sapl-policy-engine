package io.sapl.interpreter.pip.geo;
/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.pip.GeoPolicyInformationPoint;

public class GeoPIPTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final GeoPolicyInformationPoint AF = new GeoPolicyInformationPoint();

	@Test
	public void postgisTest() throws AttributeException, FunctionException {
		assertEquals("GeoAttributeFinder does not call the correct methods when accessing PostGIS.",
				PostGISConnection.TEST_OKAY,
				AF.postgis(JSON.textNode(PostGISConnection.AF_TEST), null).blockFirst().asText());
	}

	@Test
	public void traccarTest() throws AttributeException, FunctionException {
		assertEquals("GeoAttributeFinder does not call the correct methods when accessing Traccar.",
				TraccarConnection.TEST_OKAY,
				AF.traccar(JSON.textNode(TraccarConnection.AF_TEST), null).blockFirst().asText());
	}

	@Test
	public void kmlTest() throws AttributeException, FunctionException {
		assertEquals("GeoAttributeFinder does not call the correct methods when accessing KML.", KMLImport.TEST_OKAY,
				AF.kml(JSON.textNode(""), null).blockFirst().asText());
	}

}
