/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.LinkedList;

import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class KMLTest {

	public static final String TESTFILE = "/sample.kml";

	public static final String EXPECTED_RESPONSE = "{\"altitude\":0.0,\"accuracy\":0.0,\"trust\":0.0,"
			+ "\"geofences\":{\"Sample1\":{\"type\":\"MultiPoint\",\"coordinates\":[[-80,40],[-86,41]]},"
			+ "\"Sample2\":{\"type\":\"MultiPolygon\",\"coordinates\":"
			+ "[[[[-1,1],[-2,2],[-3,3],[-4,4],[-5,5],[-1,1]]]]}}}";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void importTest() throws FunctionException, AttributeException {
		KMLImport kml = new KMLImport(TESTFILE);
		assertEquals("KML file is not correctly imported into GeoPIPResponse", EXPECTED_RESPONSE,
				kml.toGeoPIPResponse().toString());
	}

	@Test
	public void constructorTest() throws AttributeException {
		JsonNode jsonFilename = JSON.textNode(TESTFILE);
		KMLImport stringKml = new KMLImport(TESTFILE);
		KMLImport jsonKml = new KMLImport(jsonFilename);

		assertEquals("Different constructors result in different import configurations.", stringKml, jsonKml);
	}

	@Test
	public void equalsTest() {
		EqualsVerifier.forClass(KMLImport.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS).verify();
	}

	@Test(expected = IllegalArgumentException.class)
	public void httpIllegalArgTest() throws FunctionException, AttributeException {
		assertNull("Empty HTTP-address leads to valid response.", new KMLImport("https://").toGeoPIPResponse());
	}

	@Test(expected = AttributeException.class)
	public void invalidJsonInConstructorTest() throws FunctionException, AttributeException {
		assertNull("Non textual JSON as constructor leads to valid response.",
				new KMLImport(JSON.booleanNode(false)).toGeoPIPResponse());
	}

	@Test(expected = AttributeException.class)
	public void httpNoKmlTest() throws FunctionException, AttributeException {
		assertNull("Random HTTP-address (non-KML) leads to valid response.",
				new KMLImport("http://about:blank").toGeoPIPResponse());
	}

	@Test
	public void invalidFileImportTest() throws FunctionException {
		try {
			new KMLImport("file_that_does_not_exist.kml").toGeoPIPResponse();
			fail("No exception is thrown when trying to access a non existing KML-file.");
		}
		catch (AttributeException e) {
			assertEquals("Wrong exception thrown when trying to access a non existing KML-file.",
					KMLImport.UNABLE_TO_PARSE_KML, e.getMessage());
		}
	}

	@Test
	@Ignore("Unit tests shouldn't depend on resources only accessible via the internet.")
	public void httpImportTest() throws FunctionException, AttributeException {
		KMLImport kml = new KMLImport("https://developers.google.com/kml/documentation/KML_Samples.kml");
		assertTrue("Only an empty response object is created when retrieving KML-file from the web.",
				kml.toGeoPIPResponse().toString().length() > 0);
	}

	@Test
	public void wrongCollection() throws FunctionException, AttributeException {
		Collection<String> coll = new LinkedList<>();
		coll.add("TestString");

		try {
			KMLImport.formatCollection(coll);
			fail("No exception is thrown when providing non compliant KML source.");
		}
		catch (AttributeException e) {
			assertEquals("Wrong exception handling when providing non compliant KML source.",
					KMLImport.UNABLE_TO_PARSE_KML, e.getMessage());
		}
	}

}
