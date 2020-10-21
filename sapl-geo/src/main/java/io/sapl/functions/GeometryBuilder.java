/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class GeometryBuilder {

	private static final String UNABLE_TO_PARSE_GEOJSON = "Provided GeoJSON-format is not compliant. Unable to parse geometry.";

	private static final String UNABLE_TO_PARSE_WKT = "Provided WKT-format is not compliant. Unable to parse geometry.";

	private static final String UNABLE_TO_PARSE_GEOMETRY = "Unable to parse geometry to JsonNode.";

	public static Geometry fromJsonNode(JsonNode jsonGeometry) throws FunctionException {
		GeometryJSON geoJsonReader = new GeometryJSON();
		Reader stringReader = new StringReader(jsonGeometry.toString());

		try {
			return geoJsonReader.read(stringReader);
		} catch (IOException e) {
			throw new FunctionException(UNABLE_TO_PARSE_GEOJSON, e);
		}
	}

	public static Geometry geoOf(Val jsonGeometry) throws FunctionException {
		return fromJsonNode(jsonGeometry.get());
	}

	public static Geometry fromWkt(String wktGeometry) throws FunctionException {
		try {
			WKTReader wkt = new WKTReader();
			return wkt.read(wktGeometry);
		} catch (ParseException e) {
			throw new FunctionException(UNABLE_TO_PARSE_WKT, e);
		}
	}

	public static String toWkt(Geometry geometry) {
		WKTWriter wkt = new WKTWriter();
		return wkt.write(geometry);
	}

	public static JsonNode toJsonNode(Geometry geometry) throws FunctionException {
		ObjectMapper mapper = new ObjectMapper();
		GeometryJSON geoJsonWriter = new GeometryJSON();

		try {
			return mapper.readTree(geoJsonWriter.toString(geometry));
		} catch (IOException e) {
			throw new FunctionException(UNABLE_TO_PARSE_GEOMETRY, e);
		}
	}

	public static Val toVal(Geometry geometry) throws FunctionException {
		return Val.of(toJsonNode(geometry));
	}

	public static JsonNode wktToJsonNode(String wktGeometry) throws FunctionException {
		return toJsonNode(fromWkt(wktGeometry));
	}

	public static String jsonNodeToWkt(JsonNode jsonGeometry) throws FunctionException {
		return toWkt(fromJsonNode(jsonGeometry));
	}

	public static double geodesicDistance(Geometry geometryOne, Geometry geometryTwo) throws FunctionException {
		try {
			int startingPointIndex = 0;
			int destinationPointIndex = 1;

			CoordinateReferenceSystem crs = CRS.decode(GeoProjection.WGS84_CRS);
			DistanceOp distOp = new DistanceOp(geometryOne, geometryTwo);
			GeodeticCalculator gc = new GeodeticCalculator(crs);

			gc.setStartingPosition(JTS.toDirectPosition(distOp.nearestPoints()[startingPointIndex], crs));
			gc.setDestinationPosition(JTS.toDirectPosition(distOp.nearestPoints()[destinationPointIndex], crs));
			return gc.getOrthodromicDistance();
		} catch (TransformException e) {
			throw new FunctionException(GeoProjection.UNABLE_TO_TRANSFORM, e);
		} catch (FactoryException e) {
			throw new FunctionException(GeoProjection.CRS_COULD_NOT_INITIALIZE, e);
		}
	}

}
