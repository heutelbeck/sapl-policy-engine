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

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.DefaultMathTransformFactory;
import org.locationtech.jts.geom.Geometry;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransformFactory;
import org.opengis.referencing.operation.TransformException;

import io.sapl.api.interpreter.PolicyEvaluationException;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class GeoProjection {

	public static final String WGS84_CRS = "EPSG:4326"; // WGS84

	public static final String WEB_MERCATOR_CRS = "EPSG:3857"; // WebMercator

	protected static final String CRS_COULD_NOT_INITIALIZE = "Provided ESPG references could not be decoded into a coordinate reference system or math transformation.";

	protected static final String NO_MATH_TRANSFORMATION_FOUND = "No math-transformation could be found to convert between the provided CRS.";

	protected static final String UNABLE_TO_TRANSFORM = "Unable to transform/project the provided geometry.";

	private final MathTransform mathTransform;

	public GeoProjection()  {
		// standard configuration
		this(WGS84_CRS, WEB_MERCATOR_CRS);
	}

	public GeoProjection(String source, String dest)  {
		try {
			mathTransform = CRS.findMathTransform(CRS.decode(source), CRS.decode(dest), false);
		} catch (FactoryException e) {
			throw new PolicyEvaluationException(CRS_COULD_NOT_INITIALIZE, e);
		}
	}

	public GeoProjection(String mathTransformWkt)  {
		try {
			MathTransformFactory fact = new DefaultMathTransformFactory();
			mathTransform = fact.createFromWKT(mathTransformWkt);
		} catch (FactoryException e) {
			throw new PolicyEvaluationException(CRS_COULD_NOT_INITIALIZE, e);
		}
	}

	public Geometry project(Geometry geometry)  {
		try {
			return JTS.transform(geometry, mathTransform);
		} catch (MismatchedDimensionException | TransformException e) {
			throw new PolicyEvaluationException(UNABLE_TO_TRANSFORM, e);
		}
	}

	public Geometry reProject(Geometry geometry)  {
		try {
			return JTS.transform(geometry, mathTransform.inverse());
		} catch (MismatchedDimensionException | TransformException e) {
			throw new PolicyEvaluationException(UNABLE_TO_TRANSFORM, e);
		}
	}

	public String toWkt() {
		return mathTransform.toWKT();
	}

	public static GeoProjection returnEmptyProjection() {
		return null;
	}

}
