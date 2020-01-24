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
package io.sapl.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import io.sapl.api.functions.FunctionException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class GeoFunctionsTest {

	private static final double COORDINATE_TOLERANCE = 0.001;

	private static final int GEO_DISTANCE_TOLERANCE = 1;

	private static final String SAMPLE_GEOCOLLECTION = "{ \"type\": \"GeometryCollection\","
			+ "\"geometries\": [{ \"type\": \"Point\", \"coordinates\": [70.0, 0.0] },"
			+ "{ \"type\": \"LineString\", \"coordinates\": [ [11.0, 0.0], [12.0, 1.0] ] }] }";

	private static final String SAMPLE_WKT_POINT = "POINT (30 10)";

	private Geometry pointJFK;

	private Geometry pointFRA;

	private Geometry pointBJM;

	private JsonNode jsonGeometryCollection;

	@Before
	public void init() throws FunctionException {
		jsonGeometryCollection = mock(JsonNode.class);
		when(jsonGeometryCollection.toString()).thenReturn(SAMPLE_GEOCOLLECTION);

		pointJFK = GeometryBuilder.fromWkt("POINT (40.64 -73.77861111)");
		pointFRA = GeometryBuilder.fromWkt("POINT (50.03333333 8.57055556)");
		pointBJM = GeometryBuilder.fromWkt("POINT (-3.32388889 29.31861111)");
	}

	@Test
	public void jsonToGeometry() {
		try {
			Geometry geomObject = GeometryBuilder.fromJsonNode(jsonGeometryCollection);
			assertTrue("GeoJSON is not correctly being converted into JTS object.",
					geomObject instanceof GeometryCollection);
		}
		catch (FunctionException e) {
			fail("GeoJSON is not correctly being converted into JTS object.");
		}
	}

	@Test
	public void wktToGeometry() {
		try {
			Geometry geomObject = GeometryBuilder.fromWkt(SAMPLE_WKT_POINT);
			assertTrue("WKT-Format is not correctly being converted into JTS object.", geomObject instanceof Point);
		}
		catch (FunctionException e) {
			fail("WKT-Format is not correctly being converted into JTS object.");
		}
	}

	@Test(expected = FunctionException.class)
	public void wktParseException() throws FunctionException {
		Geometry geomObject = GeometryBuilder.fromWkt("PNT (0 1)");
		assertNull("Geometry is being created even though provided WKT-format was not well formed.", geomObject);
	}

	@Test
	public void geometryGeoJsonExport() {
		GeometryFactory geomFactory = new GeometryFactory();
		Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));

		try {
			assertEquals("JTS Geometry is not correctly being converted into GeoJSON-Format.",
					"{\"type\":\"Point\",\"coordinates\":[1,1]}", GeometryBuilder.toJsonNode(jtsPoint).toString());
		}
		catch (FunctionException e) {
			fail("JTS Geometry is not correctly being converted into GeoJSON.");
		}
	}

	@Test
	public void geometryWktExport() {
		GeometryFactory geomFactory = new GeometryFactory();
		Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));

		assertEquals("JTS Geometry is not correctly being converted into WKT-format.", "POINT (1 1)",
				GeometryBuilder.toWkt(jtsPoint));

	}

	@Test
	public void geodesicDistanceJFKFRA() throws FunctionException {
		int correctDistance = 6206;
		int calculatedDistance = (int) Math.round(GeometryBuilder.geodesicDistance(pointJFK, pointFRA) / 1000);

		assertEquals("Geodesic distance (JFK-FRA) is not correctly calculated.", calculatedDistance, correctDistance,
				GEO_DISTANCE_TOLERANCE);
	}

	@Test
	public void geodesicDistanceJFKBJM() throws FunctionException {
		int correctDistance = 11357;
		int calculatedDistance = (int) Math.round(GeometryBuilder.geodesicDistance(pointJFK, pointBJM) / 1000);

		assertEquals("Geodesic distance (JFK-BJM) is not correctly calculated.", calculatedDistance, correctDistance,
				GEO_DISTANCE_TOLERANCE);
	}

	@Test
	public void geodesicDistanceComparison() throws FunctionException {
		assertTrue("Geodesic distances are not correctly calculated.", GeometryBuilder.geodesicDistance(pointJFK,
				pointBJM) > GeometryBuilder.geodesicDistance(pointFRA, pointBJM));
	}

	@Test
	public void coordinateProjection() throws FunctionException {
		GeoProjection projection = new GeoProjection();

		assertTrue("Geodesic distances are not correctly calculated.",
				pointFRA.distance(projection.reProject(projection.project(pointFRA))) < COORDINATE_TOLERANCE);
	}

	@Test
	public void geoProjectionEquals() {
		EqualsVerifier.forClass(GeoProjection.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.verify();
	}

	@Test(expected = FunctionException.class)
	public void jsonIOException() throws FunctionException {
		JsonNode nodeMock = mock(JsonNode.class);
		assertNull("Geometry is being created even though JsonNode is only a mock object.",
				GeometryBuilder.fromJsonNode(nodeMock));
	}

}
