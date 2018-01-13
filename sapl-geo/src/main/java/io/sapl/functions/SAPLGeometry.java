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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.geotools.geojson.geom.GeometryJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import io.sapl.api.functions.FunctionException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class SAPLGeometry {

	private static final String UNABLE_TO_PARSE_GEOJSON = "Provided GeoJSON-format is not compliant. Unable to parse geometry.";
	private static final String UNABLE_TO_PARSE_WKT = "Provided WKT-format is not compliant. Unable to parse geometry.";
	protected static final String UNABLE_TO_PARSE_GEOMETRY = "Unable to parse geometry to JsonNode.";

	private Geometry geometry;
	private GeoProjection projection;

	public SAPLGeometry(Geometry geom) {
		geometry = geom;
	}

	public SAPLGeometry(Geometry jtsGeometry, GeoProjection geoProj) throws FunctionException {
		geometry = jtsGeometry;
		if (geoProj != null) {
			projection = geoProj;
			geometry = projection.transformSrcToDestCRS(jtsGeometry);
		}
	}

	public SAPLGeometry(JsonNode jsonGeometry) throws FunctionException {
		fromJsonNode(jsonGeometry);
	}

	public SAPLGeometry(JsonNode jsonGeometry, GeoProjection geoProj) throws FunctionException {
		fromJsonNode(jsonGeometry);
		if (geoProj != null) {
			projection = geoProj;
			geometry = projection.transformSrcToDestCRS(geometry);
		}
	}

	public SAPLGeometry(String wktGeometry) throws FunctionException {
		fromWkt(wktGeometry);
	}

	public SAPLGeometry(String wktGeometry, GeoProjection geoProj) throws FunctionException {
		fromWkt(wktGeometry);
		if (geoProj != null) {
			projection = geoProj;
			geometry = projection.transformSrcToDestCRS(geometry);
		}
	}

	private void fromJsonNode(JsonNode jsonGeometry) throws FunctionException {
		GeometryJSON geoJsonReader = new GeometryJSON();
		Reader stringReader = new StringReader(jsonGeometry.toString());

		try {
			geometry = geoJsonReader.read(stringReader);
		} catch (IOException e) {
			throw new FunctionException(UNABLE_TO_PARSE_GEOJSON, e);
		}
	}

	private void fromWkt(String wktGeometry) throws FunctionException {
		try {
			WKTReader wkt = new WKTReader();
			geometry = wkt.read(wktGeometry);
		} catch (ParseException e) {
			throw new FunctionException(UNABLE_TO_PARSE_WKT, e);
		}
	}

	public String toWkt() throws FunctionException {
		WKTWriter wkt = new WKTWriter();
		if (projection != null) {
			return wkt.write(projection.transformDestToSrcCRS(geometry));
		} else {
			return wkt.write(geometry);
		}
	}

	public Geometry toJTSGeometry() throws FunctionException {
		if (projection != null) {
			return projection.transformDestToSrcCRS(geometry);
		} else {
			return geometry;
		}
	}

	public JsonNode toJsonNode() throws FunctionException {
		ObjectMapper mapper = new ObjectMapper();
		GeometryJSON geoJsonWriter = new GeometryJSON();

		try {
			if (projection != null) {
				return mapper.readTree(geoJsonWriter.toString(projection.transformDestToSrcCRS(geometry)));
			} else {
				return mapper.readTree(geoJsonWriter.toString(geometry));
			}
		} catch (IOException e) {
			throw new FunctionException(UNABLE_TO_PARSE_GEOMETRY, e);
		}
	}

	public double geodesicDistance(SAPLGeometry geometryTwo) throws FunctionException {
		try {
			int startingPointIndex = 0;
			int destinationPointIndex = 1;

			CoordinateReferenceSystem crs = CRS.decode(GeoProjection.WGS84_CRS);
			DistanceOp distOp = new DistanceOp(geometry, geometryTwo.getGeometry());
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
