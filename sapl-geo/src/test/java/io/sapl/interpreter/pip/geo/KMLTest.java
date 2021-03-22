/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class KMLTest {

	static final String TESTFILE = "/sample.kml";

	static final String EXPECTED_RESPONSE = "{\"altitude\":0.0,\"accuracy\":0.0,\"trust\":0.0,"
			+ "\"geofences\":{\"Sample1\":{\"type\":\"MultiPoint\",\"coordinates\":[[-80,40],[-86,41]]},"
			+ "\"Sample2\":{\"type\":\"MultiPolygon\",\"coordinates\":"
			+ "[[[[-1,1],[-2,2],[-3,3],[-4,4],[-5,5],[-1,1]]]]}}}";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void importTest() {
		KMLImport kml = new KMLImport(TESTFILE);
		assertThat(kml.toGeoPIPResponse().toString(), is(EXPECTED_RESPONSE));
	}

	@Test
	void constructorTest() {
		JsonNode jsonFilename = JSON.textNode(TESTFILE);
		KMLImport stringKml = new KMLImport(TESTFILE);
		KMLImport jsonKml = new KMLImport(jsonFilename);
		assertThat(stringKml, is(jsonKml));
	}

	@Test
	void equalsTest() {
		EqualsVerifier.forClass(KMLImport.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS).verify();
	}

	@Test
	void httpIllegalArgTest() {
		assertThrows(PolicyEvaluationException.class, () -> new KMLImport("https://").toGeoPIPResponse());
	}

	@Test
	void invalidJsonInConstructorTest() {
		assertThrows(PolicyEvaluationException.class, () -> new KMLImport(JSON.booleanNode(false)).toGeoPIPResponse());
	}

	@Test
	void httpNoKmlTest() {
		assertThrows(PolicyEvaluationException.class, () -> new KMLImport("http://about:blank").toGeoPIPResponse());
	}

	@Test
	void invalidFileImportTest() {
		assertThrows(PolicyEvaluationException.class,
				() -> new KMLImport("file_that_does_not_exist.kml").toGeoPIPResponse());
	}

	@Test
	void wrongCollection() {
		Collection<String> coll = new LinkedList<>();
		coll.add("TestString");
		assertThrows(PolicyEvaluationException.class, () -> KMLImport.formatCollection(coll));
	}

}
