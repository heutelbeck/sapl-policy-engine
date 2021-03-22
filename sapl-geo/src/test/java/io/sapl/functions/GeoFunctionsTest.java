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
package io.sapl.functions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

class GeoFunctionsTest {

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

	@BeforeEach
	void setUp() {
		jsonGeometryCollection = mock(JsonNode.class);
		when(jsonGeometryCollection.toString()).thenReturn(SAMPLE_GEOCOLLECTION);
		pointJFK = GeometryBuilder.fromWkt("POINT (40.64 -73.77861111)");
		pointFRA = GeometryBuilder.fromWkt("POINT (50.03333333 8.57055556)");
		pointBJM = GeometryBuilder.fromWkt("POINT (-3.32388889 29.31861111)");
	}

	@Test
	void jsonToGeometry() {
		Geometry geomObject = GeometryBuilder.fromJsonNode(jsonGeometryCollection);
		assertThat(geomObject, is(instanceOf(GeometryCollection.class)));
	}

	@Test
	void wktToGeometry() {
		Geometry geomObject = GeometryBuilder.fromWkt(SAMPLE_WKT_POINT);
		assertThat(geomObject, is(instanceOf(Point.class)));
	}

	@Test
	void wktParseException() {
		assertThrows(PolicyEvaluationException.class, () -> GeometryBuilder.fromWkt("PNT (0 1)"));
	}

	@Test
	void geometryGeoJsonExport() {
		GeometryFactory geomFactory = new GeometryFactory();
		Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));
		assertThat(GeometryBuilder.toJsonNode(jtsPoint).toString(), is("{\"type\":\"Point\",\"coordinates\":[1,1]}"));
	}

	@Test
	void geometryWktExport() {
		GeometryFactory geomFactory = new GeometryFactory();
		Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));
		assertThat(GeometryBuilder.toWkt(jtsPoint), is("POINT (1 1)"));
	}

	@Test
	void geodesicDistanceJFKFRA() {
		double correctDistance = 6206D;
		double calculatedDistance = GeometryBuilder.geodesicDistance(pointJFK, pointFRA) / 1000;
		assertThat(calculatedDistance, is(closeTo(correctDistance, GEO_DISTANCE_TOLERANCE)));
	}

	@Test
	void geodesicDistanceJFKBJM() {
		double correctDistance = 11357D;
		double calculatedDistance = GeometryBuilder.geodesicDistance(pointJFK, pointBJM) / 1000.D;
		assertThat(calculatedDistance, is(closeTo(correctDistance, GEO_DISTANCE_TOLERANCE)));
	}

	@Test
	void geodesicDistanceComparison() {
		assertThat(GeometryBuilder.geodesicDistance(pointJFK, pointBJM),
				is(greaterThan(GeometryBuilder.geodesicDistance(pointFRA, pointBJM))));
	}

	@Test
	void coordinateProjection() {
		GeoProjection projection = new GeoProjection();
		assertThat(pointFRA.distance(projection.reProject(projection.project(pointFRA))),
				is(lessThan(COORDINATE_TOLERANCE)));
	}

	@Test
	void geoProjectionEquals() {
		EqualsVerifier.forClass(GeoProjection.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.verify();
	}

	@Test
	void jsonIOException() {
		JsonNode nodeMock = mock(JsonNode.class);
		assertThrows(PolicyEvaluationException.class, () -> GeometryBuilder.fromJsonNode(nodeMock));
	}

}
