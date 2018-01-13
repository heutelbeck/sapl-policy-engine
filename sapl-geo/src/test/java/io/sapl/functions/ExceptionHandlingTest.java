package io.sapl.functions;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.io.IOException;

import org.geotools.referencing.CRS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Geometry;

import io.sapl.api.functions.FunctionException;
import io.sapl.functions.GeoProjection;
import io.sapl.functions.SAPLGeometry;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ SAPLGeometry.class, ObjectMapper.class, CRS.class })
public class ExceptionHandlingTest {

    private GeoProjection sampleProjection;
    private Geometry sampleGeometry;

    @Before
    public void init() throws FunctionException {
	mockStatic(CRS.class);
	sampleGeometry = mock(Geometry.class);
	sampleProjection = new GeoProjection();
    }

    @Test
    public void factoryExceptionSrcToDestProjection() throws FactoryException {
	when(CRS.findMathTransform(any(), any(), anyBoolean())).thenThrow(new FactoryException());

	try {
	    sampleProjection.transformSrcToDestCRS(sampleGeometry);
	    fail("Exception in transform method was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of FactoryException in GeoProjection works not as expected.",
		    e.getMessage(), GeoProjection.NO_MATH_TRANSFORMATION_FOUND);
	}
    }

    @Test
    public void factoryExceptionDestToSrcProjection() throws FunctionException, FactoryException {
	when(CRS.findMathTransform(any(), any(), anyBoolean())).thenThrow(new FactoryException());

	try {
	    sampleProjection.transformDestToSrcCRS(sampleGeometry);
	    fail("Exception in transform method was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of FactoryException in GeoProjection works not as expected.",
		    e.getMessage(), GeoProjection.NO_MATH_TRANSFORMATION_FOUND);
	}
    }

    @Test
    public void mismatchedDimensionExceptionSrcToDestProjection() throws FactoryException {
	when(CRS.findMathTransform(any(), any(), anyBoolean()))
		.thenThrow(new MismatchedDimensionException());

	try {
	    sampleProjection.transformSrcToDestCRS(sampleGeometry);
	    fail("Exception in transform-method was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of MismatchedDimensionException in GeoProjection works not as expected.",
		    e.getMessage(), GeoProjection.UNABLE_TO_TRANSFORM);
	}
    }

    @Test
    public void mismatchedDimensionExceptionDestToSrcProjection() throws FactoryException {
	when(CRS.findMathTransform(any(), any(), anyBoolean()))
		.thenThrow(new MismatchedDimensionException());

	try {
	    sampleProjection.transformDestToSrcCRS(sampleGeometry);
	    fail("Exception in transform-method was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of MismatchedDimensionException in GeoProjection works not as expected.",
		    e.getMessage(), GeoProjection.UNABLE_TO_TRANSFORM);
	}
    }

    @Test
    public void factoryExceptionInStandardConstructor() throws FactoryException {
	when(CRS.decode(anyString())).thenThrow(new FactoryException());

	try {
	    new GeoProjection();
	    fail("Exception in empty GeoProjection-Constructor was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of FactoryException in empty GeoProjection-Constructor works not as expected.",
		    e.getMessage(), GeoProjection.CRS_COULD_NOT_INITIALIZE);
	}
    }

    @Test
    public void factoryExceptionInConstructor() throws FactoryException {
	when(CRS.decode(anyString())).thenThrow(new FactoryException());

	try {
	    new GeoProjection(GeoProjection.WEB_MERCATOR_CRS, GeoProjection.WGS84_CRS);
	    fail("Exception in GeoProjection-Constructor was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of FactoryException in GeoProjection-Constructor works not as expected.",
		    e.getMessage(), GeoProjection.CRS_COULD_NOT_INITIALIZE);
	}
    }

    @Test
    public void factoryExceptionInGeodesicCalculation() throws FactoryException, FunctionException {
	when(CRS.decode(anyString())).thenThrow(new FactoryException());

	SAPLGeometry saplGeometryOne = new SAPLGeometry("POINT (10 10)");
	SAPLGeometry saplGeometryTwo = mock(SAPLGeometry.class);

	try {
	    saplGeometryOne.geodesicDistance(saplGeometryTwo);
	    fail("Exception in geodesic calculations was expected but not thrown.");
	} catch (FunctionException e) {
	    assertEquals(
		    "Handling of FactoryException in geodesic calculations works not as expected.",
		    e.getMessage(), GeoProjection.CRS_COULD_NOT_INITIALIZE);
	}
    }

    @Test
    public void toJsonNodeIOException() throws Exception {
	try {
	    ObjectMapper mapperMock = mock(ObjectMapper.class);
	    when(mapperMock.readTree(anyString())).thenThrow(new IOException());

	    mockStatic(ObjectMapper.class);
	    whenNew(ObjectMapper.class).withNoArguments().thenReturn(mapperMock);

	    SAPLGeometry geom = new SAPLGeometry("POINT (1 1)");
	    geom.toJsonNode();
	    fail("Exception should have been thrown while exporting to GeoJson.");
	} catch (Exception e) {
	    assertEquals("Wrong exception is thrown while exporting to GeoJson.", e.getMessage(),
		    SAPLGeometry.UNABLE_TO_PARSE_GEOMETRY);
	}
    }
}
