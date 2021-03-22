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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.mockito.MockedStatic;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import io.sapl.api.interpreter.PolicyEvaluationException;

class ExceptionHandlingTest {

	private Geometry sampleGeometry;

	@BeforeEach
	void setUp() {
		sampleGeometry = mock(Geometry.class);
	}

	@Test
	void factoryExceptionInProjectionConstructor() {
		try (MockedStatic<CRS> crsMock = mockStatic(CRS.class)) {
			crsMock.when(() -> CRS.findMathTransform(any(), any(), anyBoolean())).thenThrow(new FactoryException());
			assertThrows(PolicyEvaluationException.class, () -> new GeoProjection());
		}
	}

	@Test
	void transformExceptioninProjection() {
		try (MockedStatic<JTS> jtsMock = mockStatic(JTS.class)) {
			jtsMock.when(() -> JTS.transform(any(Geometry.class), any())).thenThrow(new TransformException());
			assertThrows(PolicyEvaluationException.class, () -> new GeoProjection().project(sampleGeometry));
		}
	}

	@Test
	void mismatchedDimensionExceptioninProjection() {
		try (MockedStatic<JTS> jtsMock = mockStatic(JTS.class)) {
			jtsMock.when(() -> JTS.transform(any(Geometry.class), any())).thenThrow(new MismatchedDimensionException());
			assertThrows(PolicyEvaluationException.class, () -> new GeoProjection().project(sampleGeometry));
		}
	}

	@Test
	void factoryExceptionInGeodesicCalculation() {
		try (MockedStatic<CRS> crsMock = mockStatic(CRS.class)) {
			crsMock.when(() -> CRS.decode(anyString())).thenThrow(new FactoryException());
			Geometry geometryOne = GeometryBuilder.fromWkt("POINT (10 10)");
			Geometry geometryTwo = mock(Geometry.class);
			assertThrows(PolicyEvaluationException.class,
					() -> GeometryBuilder.geodesicDistance(geometryOne, geometryTwo));
		}
	}

}
