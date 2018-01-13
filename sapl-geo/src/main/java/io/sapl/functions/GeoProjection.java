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

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Geometry;

import io.sapl.api.functions.FunctionException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class GeoProjection {

	public static final String WGS84_CRS = "EPSG:4326"; // WGS84
	public static final String WEB_MERCATOR_CRS = "EPSG:3857"; // WebMercator
	protected static final String CRS_COULD_NOT_INITIALIZE = "Provided ESPG references could not be decoded into a coordinate reference system.";
	protected static final String NO_MATH_TRANSFORMATION_FOUND = "No math-transformation could be found to convert between the provided CRS.";
	protected static final String UNABLE_TO_TRANSFORM = "Unable to transform/project the provided geometry.";

	private final CoordinateReferenceSystem sourceCrs;
	private final CoordinateReferenceSystem destinationCrs;

	public GeoProjection() throws FunctionException {
		// standard configuration
		try {
			sourceCrs = CRS.decode(WGS84_CRS);
			destinationCrs = CRS.decode(WEB_MERCATOR_CRS);
		} catch (FactoryException e) {
			throw new FunctionException(CRS_COULD_NOT_INITIALIZE, e);
		}
	}

	public GeoProjection(String source, String dest) throws FunctionException {
		try {
			sourceCrs = CRS.decode(source);
			destinationCrs = CRS.decode(dest);
		} catch (FactoryException e) {
			throw new FunctionException(CRS_COULD_NOT_INITIALIZE, e);
		}
	}

	public Geometry transformSrcToDestCRS(Geometry geometry) throws FunctionException {
		try {
			MathTransform mathTransform = CRS.findMathTransform(sourceCrs, destinationCrs, false);
			return JTS.transform(geometry, mathTransform);
		} catch (FactoryException e) {
			throw new FunctionException(NO_MATH_TRANSFORMATION_FOUND, e);
		} catch (MismatchedDimensionException | TransformException e) {
			throw new FunctionException(UNABLE_TO_TRANSFORM, e);
		}
	}

	public Geometry transformDestToSrcCRS(Geometry geometry) throws FunctionException {
		try {
			MathTransform mathTransform = CRS.findMathTransform(destinationCrs, sourceCrs, false);
			return JTS.transform(geometry, mathTransform);
		} catch (FactoryException e) {
			throw new FunctionException(NO_MATH_TRANSFORMATION_FOUND, e);
		} catch (MismatchedDimensionException | TransformException e) {
			throw new FunctionException(UNABLE_TO_TRANSFORM, e);
		}
	}
}
