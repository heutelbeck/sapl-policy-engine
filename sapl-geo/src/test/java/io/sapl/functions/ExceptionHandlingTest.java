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

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;

import io.sapl.api.functions.FunctionException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ObjectMapper.class, CRS.class, JTS.class })
public class ExceptionHandlingTest {

	private Geometry sampleGeometry;

	@Before
	public void init() throws FunctionException {
		mockStatic(CRS.class);
		mockStatic(JTS.class);
		sampleGeometry = mock(Geometry.class);
	}

	@Test
	public void factoryExceptionInProjectionConstructor() throws FactoryException {
		when(CRS.findMathTransform(any(), any(), anyBoolean())).thenThrow(new FactoryException());

		try {
			new GeoProjection();
			fail("Exception in transform method was expected but not thrown.");
		} catch (FunctionException e) {
			assertEquals("Handling of FactoryException in GeoProjection works not as expected.",
					GeoProjection.CRS_COULD_NOT_INITIALIZE, e.getMessage());
		}
	}

	@Test
	public void transformExceptioninProjection() throws TransformException, MismatchedDimensionException {
		when(JTS.transform(any(Geometry.class), any())).thenThrow(new TransformException());

		try {
			GeoProjection sampleProjection = new GeoProjection();
			sampleProjection.project(sampleGeometry);
			fail("Exception in transform method was expected but not thrown.");
		} catch (FunctionException e) {
			assertEquals("Handling of FactoryException in GeoProjection works not as expected.",
					GeoProjection.UNABLE_TO_TRANSFORM, e.getMessage());
		}
	}

	@Test
	public void mismatchedDimensionExceptioninProjection() throws TransformException, MismatchedDimensionException {
		when(JTS.transform(any(Geometry.class), any())).thenThrow(new MismatchedDimensionException());

		try {
			GeoProjection sampleProjection = new GeoProjection();
			sampleProjection.project(sampleGeometry);
			fail("Exception in transform method was expected but not thrown.");
		} catch (FunctionException e) {
			assertEquals("Handling of FactoryException in GeoProjection works not as expected.",
					GeoProjection.UNABLE_TO_TRANSFORM, e.getMessage());
		}
	}

	@Test
	public void factoryExceptionInGeodesicCalculation() throws FactoryException, FunctionException {
		when(CRS.decode(anyString())).thenThrow(new FactoryException());

		Geometry geometryOne = GeometryBuilder.fromWkt("POINT (10 10)");
		Geometry geometryTwo = mock(Geometry.class);

		try {
			GeometryBuilder.geodesicDistance(geometryOne, geometryTwo);
			fail("Exception in geodesic calculations was expected but not thrown.");
		} catch (FunctionException e) {
			assertEquals("Handling of FactoryException in geodesic calculations works not as expected.",
					GeoProjection.CRS_COULD_NOT_INITIALIZE, e.getMessage());
		}
	}
}
