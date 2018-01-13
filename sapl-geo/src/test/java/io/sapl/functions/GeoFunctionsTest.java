/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.geotools.referencing.CRS;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import io.sapl.api.functions.FunctionException;
import io.sapl.functions.GeoProjection;
import io.sapl.functions.SAPLGeometry;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class GeoFunctionsTest {

    private static final double COORDINATE_TOLERANCE = 0.001;
    private static final int GEO_DISTANCE_TOLERANCE = 1;

    private static final String SAMPLE_GEOCOLLECTION = "{ \"type\": \"GeometryCollection\","
	    + "\"geometries\": [{ \"type\": \"Point\", \"coordinates\": [70.0, 0.0] },"
	    + "{ \"type\": \"LineString\", \"coordinates\": [ [11.0, 0.0], [12.0, 1.0] ] }] }";
    private static final String SAMPLE_WKT_POINT = "POINT (30 10)";

    private SAPLGeometry pointJFK;
    private SAPLGeometry pointFRA;
    private SAPLGeometry pointBJM;
    private GeoProjection sampleProjection;
    private JsonNode jsonGeometryCollection;

    @Before
    public void init() throws FunctionException {
	jsonGeometryCollection = mock(JsonNode.class);
	when(jsonGeometryCollection.toString()).thenReturn(SAMPLE_GEOCOLLECTION);

	pointJFK = new SAPLGeometry("POINT (40.64 -73.77861111)");
	pointFRA = new SAPLGeometry("POINT (50.03333333 8.57055556)");
	pointBJM = new SAPLGeometry("POINT (-3.32388889 29.31861111)");
	sampleProjection = new GeoProjection(GeoProjection.WGS84_CRS,
		GeoProjection.WEB_MERCATOR_CRS);
    }

    @Test
    public void jsonToSAPLGeometry() {
	try {
	    Geometry geomObject = new SAPLGeometry(jsonGeometryCollection).getGeometry();
	    assertTrue("GeoJSON is not correctly being converted into JTS object.",
		    geomObject instanceof GeometryCollection);
	} catch (FunctionException e) {
	    fail("GeoJSON is not correctly being converted into JTS object.");
	}
    }

    @Test
    public void wktToSAPLGeometry() {
	try {
	    Geometry geomObject = new SAPLGeometry(SAMPLE_WKT_POINT).getGeometry();
	    assertTrue("WKT-Format is not correctly being converted into JTS object.",
		    geomObject instanceof Point);
	} catch (FunctionException e) {
	    fail("WKT-Format is not correctly being converted into JTS object.");
	}
    }

    @Test
    public void wktToSAPLGeometryWithProjection() {
	try {
	    Geometry geomObject = new SAPLGeometry(SAMPLE_WKT_POINT, sampleProjection)
		    .getGeometry();
	    assertTrue(
		    "WKT-Format is not correctly being converted into JTS object when applying projection.",
		    geomObject instanceof Point);
	} catch (FunctionException e) {
	    fail("WKT-Format is not correctly being converted into JTS object when applying projection.");
	}
    }

    @Test
    public void wktToSAPLGeometryWithNullProjection() {
	try {
	    SAPLGeometry geomObject = new SAPLGeometry(SAMPLE_WKT_POINT, null);
	    assertTrue(
		    "WKT-Format is not correctly being converted into JTS object when passing NULL as projection.",
		    geomObject.getGeometry() instanceof Point
			    && geomObject.getProjection() == null);
	} catch (FunctionException e) {
	    fail("WKT-Format is not correctly being converted into JTS object when passing NULL as projection.");
	}
    }

    @Test(expected = FunctionException.class)
    public void wktParseException() throws FunctionException {
	SAPLGeometry geomObject = new SAPLGeometry("PNT (0 1)");
	assertNull("Geometry is being created even though provided WKT-format was not well formed.",
		geomObject);
    }

    @Test
    public void geometryToSAPLGeometry() {
	GeometryFactory geomFactory = new GeometryFactory();
	Geometry geomObject = new SAPLGeometry(geomFactory.createPoint(new Coordinate(1.0, 1.0)))
		.getGeometry();

	assertTrue("SAPLGeometry is not correctly being constructed by JTS geometry.",
		geomObject instanceof Point);
    }

    @Test
    public void geometryToSAPLGeometryWithNullProjection() throws FunctionException {
	GeometryFactory geomFactory = new GeometryFactory();
	SAPLGeometry geomObject = new SAPLGeometry(
		geomFactory.createPoint(new Coordinate(1.0, 1.0)), null);

	assertTrue(
		"SAPLGeometry is not correctly being constructed by JTS geometry when passing NULL as projection.",
		geomObject.getGeometry() instanceof Point
			&& geomObject.getProjection() == null);
    }

    @Test
    public void geometryGeoJsonExport() {
	GeometryFactory geomFactory = new GeometryFactory();
	Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));

	try {
	    SAPLGeometry saplPoint = new SAPLGeometry(jtsPoint);

	    assertEquals("JTS Geometry is not correctly being converted into GeoJSON-Format.",
		    "{\"type\":\"Point\",\"coordinates\":[1,1]}",
		    saplPoint.toJsonNode().toString());
	} catch (FunctionException e) {
	    fail("JTS Geometry is not correctly being converted into GeoJSON.");
	}
    }

    @Test
    public void geometryWktExport() {
	GeometryFactory geomFactory = new GeometryFactory();
	Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));

	try {
	    SAPLGeometry saplPoint = new SAPLGeometry(jtsPoint);

	    assertEquals("JTS Geometry is not correctly being converted into WKT-format.",
		    "POINT (1 1)",
		    saplPoint.toWkt());
	} catch (FunctionException e) {
	    fail("JTS Geometry is not correctly being converted into WKT-format.");
	}
    }

    @Test
    public void geometryWktExportWithProj() {
	GeometryFactory geomFactory = new GeometryFactory();
	Geometry jtsPoint = geomFactory.createPoint(new Coordinate(1, 1));

	try {
	    SAPLGeometry saplPoint = new SAPLGeometry(jtsPoint, sampleProjection);
	    assertEquals(
		    "JTS Geometry is not correctly being converted into WKT-format when applying projections.",
		    "POINT (0.9999999999999887 1)",
		    saplPoint.toWkt());
	} catch (FunctionException e) {
	    fail("JTS Geometry is not correctly being converted into WKT-format when applying projections.");
	}
    }

    @Test
    public void geodesicDistanceJFKFRA() throws FunctionException {
	int correctDistance = 6206;
	int calculatedDistance = (int) Math.round(pointJFK.geodesicDistance(pointFRA) / 1000);

	assertEquals("Geodesic distance (JFK-FRA) is not correctly calculated.", calculatedDistance,
		correctDistance,
		GEO_DISTANCE_TOLERANCE);
    }

    @Test
    public void geodesicDistanceJFKBJM() throws FunctionException {
	int correctDistance = 11357;
	int calculatedDistance = (int) Math.round(pointJFK.geodesicDistance(pointBJM) / 1000);

	assertEquals("Geodesic distance (JFK-BJM) is not correctly calculated.", calculatedDistance,
		correctDistance,
		GEO_DISTANCE_TOLERANCE);
    }

    @Test
    public void geodesicDistanceComparison() throws FunctionException {
	assertTrue("Geodesic distances are not correctly calculated.",
		pointJFK.geodesicDistance(pointBJM) > pointFRA.geodesicDistance(pointBJM));
    }

    @Test
    public void coordinateProjection() throws FunctionException {
	SAPLGeometry projectedFRA = new SAPLGeometry(pointFRA.getGeometry(), new GeoProjection());

	assertTrue("Geodesic distances are not correctly calculated.",
		pointFRA.toJTSGeometry()
			.distance(projectedFRA.toJTSGeometry()) < COORDINATE_TOLERANCE);
    }

    @Test
    public void projectionGetSet() throws FunctionException {
	SAPLGeometry saplGeometry = new SAPLGeometry(SAMPLE_WKT_POINT);
	saplGeometry.setProjection(sampleProjection);
	assertEquals(
		"Setters and/or Getters for projection in SAPLGeometry are not working correctly.",
		sampleProjection, saplGeometry.getProjection());
    }

    @Test
    public void geoProjectionGetter() throws FactoryException, FunctionException {
	GeoProjection testProjection = new GeoProjection();
	CoordinateReferenceSystem sourceCrs = CRS.decode(GeoProjection.WGS84_CRS);
	CoordinateReferenceSystem destCrs = CRS.decode(GeoProjection.WEB_MERCATOR_CRS);

	assertTrue("Standard initialization of CRS in GeoProjection works not as expected.",
		sourceCrs.equals(testProjection.getSourceCrs())
			&& destCrs.equals(testProjection.getDestinationCrs()));
    }

    @Test
    public void geoProjectionEquals() {
	EqualsVerifier.forClass(GeoProjection.class)
		.suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS).verify();
    }

    @Test
    public void geometryEquals() {
	EqualsVerifier.forClass(SAPLGeometry.class)
		.suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
		.withPrefabValues(Geometry.class, pointFRA.getGeometry(), pointJFK.getGeometry())
		.verify();
    }

    @Test(expected = FunctionException.class)
    public void jsonIOException() throws FunctionException {
	JsonNode nodeMock = mock(JsonNode.class);
	assertNull("Geometry is being created even though JsonNode is only a mock object.",
		new SAPLGeometry(nodeMock));
    }
}
