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

import java.math.BigDecimal;
import java.util.BitSet;

import javax.measure.converter.UnitConverter;
import javax.measure.quantity.Quantity;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * Format always [Lat(y), Long(x)]
 */

@Slf4j
@NoArgsConstructor
@FunctionLibrary(name = GeoFunctionLibrary.NAME, description = GeoFunctionLibrary.DESCRIPTION)
public class GeoFunctionLibrary {

	public static final String NAME = "geo";
	public static final String DESCRIPTION = "Functions enabling location based authorisation and geofencing.";

	private static final String EQUALS_DOC = "equals(GEOMETRY1, GEOMETRY2): Tests if two geometries are exactly (!) equal. GEOMETRY can also be a GEOMETRYCOLLECTION.";
	private static final String DISJOINT_DOC = "disjoint(GEOMETRY1, GEOMETRY2): Tests if two geometries are disjoint from each other (not intersecting each other). ";
	private static final String TOUCHES_DOC = "touches(GEOMETRY1, GEOMETRY2): Tests if two geometries are touching each other.";
	private static final String CROSSES_DOC = "crosses(GEOMETRY1, GEOMETRY2): Tests if two geometries are crossing each other (having a intersecting area).";
	private static final String WITHIN_DOC = "within(GEOMETRY1, GEOMETRY2): Tests if the GEOMETRY1 is fully within GEOMETRY2 (converse of contains-function). GEOMETRY2 can also be of type GeometryCollection.";
	private static final String CONTAINS_DOC = "contains(GEOMETRY1, GEOMETRY2): Tests if the GEOMETRY1 fully contains GEOMETRY2 (converse of within-function). GEOMETRY1 can also be of type GeometryCollection.";
	private static final String OVERLAPS_DOC = "overlaps(GEOMETRY1, GEOMETRY2): Tests if two geometries are overlapping.";
	private static final String INTERSECTS_DOC = "intersects(GEOMETRY1, GEOMETRY2): Tests if two geometries have at least one common intersection point.";
	private static final String BUFFER_DOC = "buffer(GEOMETRY, BUFFERWIDTH): Adds a buffer area of BUFFERWIDTH around GEOMETRY and returns the new geometry."
			+ " BUFFERWIDTH is in the units of the coordinates or of the projection (if projection applied)";
	private static final String BOUNDARY_DOC = "boundary(GEOMETRY): Returns the boundary of a geometry.";
	private static final String CENTROID_DOC = "centroid(GEOMETRY): Returns a point that is the geometric center of gravity of the geometry.";
	private static final String CONVEXHULL_DOC = "convexHull(GEOMETRY): Returns the convex hull (smallest convex polygon, that contains all points of the geometry) of the geometry.";
	private static final String UNION_DOC = "union(GEOMETRY1, GEOMETRY2): Returns the union of two geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
	private static final String INTERSECTION_DOC = "intersection(GEOMETRY1, GEOMETRY2): Returns the point set intersection of the geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
	private static final String DIFFERENCE_DOC = "difference(GEOMETRY1, GEOMETRY2): Returns the closure of the set difference between two geometries.";
	private static final String SYMDIFFERENCE_DOC = "symDifference(GEOMETRY1, GEOMETRY2): Returns the closure of the symmetric difference between two geometries.";
	private static final String DISTANCE_DOC = "distance(GEOMETRY1, GEOMETRY2): Returns the (shortest) geometric (planar) distance between two geometries. Does return the value of the unit of the coordinates (or projection if used).";
	private static final String GEODISTANCE_DOC = "geoDistance(GEOMETRY1, GEOMETRY2): Returns the (shortest) geodetic distance of two geometries in [m]. Coordinate Reference System is the unprojected (source) system (WGS84 recommended).";
	private static final String ISWITHINDISTANCE_DOC = "isWithinDistance(GEOMETRY1, GEOMETRY2, DISTANCE): Tests if two geometries are within the given geometric (planar) distance of each other. "
			+ "Uses the unit of the coordinates (or projection if used).";
	private static final String ISWITHINGEODISTANCE_DOC = "isWithinGeoDistance(GEOMETRY1, GEOMETRY2, DISTANCE): Tests if two geometries are within the given geodetic distance of each other. Uses [m] as unit."
			+ " Coordinate Reference System is the unprojected (source) system (WGS84 recommended).";
	private static final String LENGTH_DOC = "length(GEOMETRY): Returns the length of the geometry (perimeter in case of areal geometries). The returned value is in the units of the coordinates or of the projection (if projection applied).";
	private static final String AREA_DOC = "area(GEOMETRY): Returns the area of the geometry. The returned value is in the units (squared) of the coordinates or of the projection (if projection applied).";
	private static final String ISSIMPLE_DOC = "isSimple(GEOMETRY): Returns true if the geometry has no anomalous geometric points (e.g. self interesection, self tangency,...).";
	private static final String ISVALID_DOC = "isValid(GEOMETRY): Returns true if the geometry is topologically valid according to OGC specifications.";
	private static final String ISCLOSED_DOC = "isClosed(GEOMETRY): Returns true if the geometry is either empty or from type (Multi)Point or a closed (Multi)LineString.";
	private static final String TOMETER_DOC = "toMeter(VALUE, UNIT): Converts the given VALUE from [UNIT] to [m].";
	private static final String TOSQUAREMETER_DOC = "toSquareMeter(VALUE, UNIT): Converts the given VALUE from [UNIT] to [m].";
	private static final String ONEANDONLY_DOC = "oneAndOnly(GEOMETRYCOLLECTION): If GEOMETRYCOLLECTION only contains one element, this element will be returned. In all other cases an error will be thrown.";
	private static final String BAGSIZE_DOC = "bagSize(GOEMETRYCOLLECTION): Returns the number of elements in the GEOMETRYCOLLECTION.";
	private static final String GEOMETRYISIN_DOC = "geometryIsIn(GEOMETRY, GEOMETRYCOLLECTION): Tests if GEOMETRY is included in GEOMETRYCOLLECTION.";
	private static final String GEOMETRYBAG_DOC = "geometryBag(GEOMETRY,...): Takes any number of GEOMETRY and returns a GEOMETRYCOLLECTION containing all of them.";
	private static final String RES_TO_GEOMETRYBAG_DOC = "resToGeometryBag(RESOURCE_ARRAY): Takes multiple Geometries from RESOURCE_ARRAY and turns them into a GeometryCollection (e.g. geofences from a third party system).";
	private static final String ATLEASTONEMEMBEROF_DOC = "atLeastOneMemberOf(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns TRUE if at least one member of GEOMETRYCOLLECTION1 is contained in GEOMETRYCOLLECTION2.";
	private static final String SUBSET_DOC = "subset(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns true, if GEOMETRYCOLLECTION1 is a subset of GEOMETRYCOLLECTION2.";
	private static final String GETPROJECTION_DOC = "getProjection(SRCSYSTEM, DESTSYSTEM): Returns the projection parameters between the given set of coordinate systems (given as EPSG id).";
	private static final String PROJECT_DOC = "project(GEOMETRY): Returns the projected geometry (or the geometry itself in case no projection is defined).";

	private static final String INPUT_NOT_GEOCOLLECTION_WITH_ONLY_ONE_GEOM = "Input must be a GeometryCollection containing only one Geometry.";
	private static final String UNIT_NOT_CONVERTIBLE = "Given unit '%s' is not convertible to '%s'.";
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Function(name = "equals", docs = EQUALS_DOC)
	public JsonNode geometryEquals(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.equals(geometryTwo));
	}

	@Function(docs = DISJOINT_DOC)
	public JsonNode disjoint(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.disjoint(geometryTwo));
	}

	@Function(docs = TOUCHES_DOC)
	public JsonNode touches(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.touches(geometryTwo));
	}

	@Function(docs = CROSSES_DOC)
	public JsonNode crosses(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.crosses(geometryTwo));
	}

	@Function(docs = WITHIN_DOC)
	public JsonNode within(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		if (geometryTwo instanceof GeometryCollection) {
			return JSON.booleanNode(geometryOne.within(geometryTwo.union()));
		} else {
			return JSON.booleanNode(geometryOne.within(geometryTwo));
		}
	}

	@Function(docs = CONTAINS_DOC)
	public JsonNode contains(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		if (geometryOne instanceof GeometryCollection) {
			return JSON.booleanNode(geometryOne.union().contains(geometryTwo));
		} else {
			return JSON.booleanNode(geometryOne.contains(geometryTwo));
		}
	}

	@Function(docs = OVERLAPS_DOC)
	public JsonNode overlaps(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.overlaps(geometryTwo));
	}

	@Function(docs = INTERSECTS_DOC)
	public JsonNode intersects(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.booleanNode(geometryOne.intersects(geometryTwo));
	}

	@Function(docs = BUFFER_DOC)
	public JsonNode buffer(@JsonObject JsonNode jsonGeometry, @Number JsonNode buffer) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return GeometryBuilder.toJsonNode(geometry.buffer(buffer.asDouble()));
	}

	@Function(docs = BOUNDARY_DOC)
	public JsonNode boundary(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return GeometryBuilder.toJsonNode(geometry.getBoundary());
	}

	@Function(docs = CENTROID_DOC)
	public JsonNode centroid(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return GeometryBuilder.toJsonNode(geometry.getCentroid());
	}

	@Function(docs = CONVEXHULL_DOC)
	public JsonNode convexHull(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return GeometryBuilder.toJsonNode(geometry.convexHull());
	}

	@Function(docs = UNION_DOC)
	public JsonNode union(@JsonObject JsonNode... jsonGeometries) throws FunctionException {
		if (jsonGeometries.length == 1) {
			return jsonGeometries[0];
		}

		Geometry geomUnion = GeometryBuilder.fromJsonNode(jsonGeometries[0]);
		for (int i = 1; i < jsonGeometries.length; i++) {
			Geometry additionalGeom = GeometryBuilder.fromJsonNode(jsonGeometries[i]);
			geomUnion = geomUnion.union(additionalGeom);
		}

		return GeometryBuilder.toJsonNode(geomUnion);
	}

	@Function(docs = INTERSECTION_DOC)
	public JsonNode intersection(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return GeometryBuilder.toJsonNode(geometryOne.intersection(geometryTwo));
	}

	@Function(docs = DIFFERENCE_DOC)
	public JsonNode difference(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return GeometryBuilder.toJsonNode(geometryOne.difference(geometryTwo));
	}

	@Function(docs = SYMDIFFERENCE_DOC)
	public JsonNode symDifference(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return GeometryBuilder.toJsonNode(geometryOne.symDifference(geometryTwo));
	}

	@Function(docs = DISTANCE_DOC)
	public JsonNode distance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.numberNode(BigDecimal.valueOf(geometryOne.distance(geometryTwo)));
	}

	@Function(docs = GEODISTANCE_DOC)
	public JsonNode geoDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		return JSON.numberNode(BigDecimal.valueOf(GeometryBuilder.geodesicDistance(geometryOne, geometryTwo)));
	}

	@Function(docs = ISWITHINDISTANCE_DOC)
	public JsonNode isWithinDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo,
			@Number JsonNode distInput) throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);
		double distance = distInput.asDouble();

		return JSON.booleanNode(geometryOne.isWithinDistance(geometryTwo, distance));
	}

	@Function(docs = ISWITHINGEODISTANCE_DOC)
	public JsonNode isWithinGeoDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo,
			@Number JsonNode distInput) throws FunctionException {
		Geometry geometryOne = GeometryBuilder.fromJsonNode(jsonGeometryOne);
		Geometry geometryTwo = GeometryBuilder.fromJsonNode(jsonGeometryTwo);

		double distance = distInput.asDouble();

		if (GeometryBuilder.geodesicDistance(geometryOne, geometryTwo) <= distance) {
			return JSON.booleanNode(true);
		} else {
			return JSON.booleanNode(false);
		}
	}

	@Function(docs = LENGTH_DOC)
	public JsonNode length(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return JSON.numberNode(BigDecimal.valueOf(geometry.getLength()));
	}

	@Function(docs = AREA_DOC)
	public JsonNode area(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return JSON.numberNode(BigDecimal.valueOf(geometry.getArea()));
	}

	@Function(docs = ISSIMPLE_DOC)
	public JsonNode isSimple(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return JSON.booleanNode(geometry.isSimple());
	}

	@Function(docs = ISVALID_DOC)
	public JsonNode isValid(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return JSON.booleanNode(geometry.isValid());
	}

	@Function(docs = ISCLOSED_DOC)
	public JsonNode isClosed(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		boolean result = false;

		if (geometry.isEmpty() || ("Point".equals(geometry.getGeometryType()))
				|| ("MultiPoint".equals(geometry.getGeometryType()))) {
			result = true;
		} else if ("LineString".equals(geometry.getGeometryType())) {
			result = ((LineString) geometry).isClosed();
		} else if ("MultiLineString".equals(geometry.getGeometryType())) {
			result = ((MultiLineString) geometry).isClosed();
		}

		return JSON.booleanNode(result);
	}

	@Function(docs = TOMETER_DOC)
	public JsonNode toMeter(@Number JsonNode jsonValue, @Text JsonNode jsonUnit) throws FunctionException {
		double convertedValue;
		Unit<? extends Quantity> unitFrom = Unit.valueOf(jsonUnit.asText());

		if (unitFrom.isCompatible(SI.METER)) {
			UnitConverter unitConv = unitFrom.getConverterTo(SI.METER);
			convertedValue = unitConv.convert(jsonValue.asDouble());
		} else {
			throw new FunctionException(String.format(UNIT_NOT_CONVERTIBLE, jsonUnit.asText(), "m"));
		}
		return JSON.numberNode(BigDecimal.valueOf(convertedValue));
	}

	@Function(docs = TOSQUAREMETER_DOC)
	public JsonNode toSquareMeter(@Number JsonNode jsonValue, @Text JsonNode jsonUnit) throws FunctionException {
		double convertedValue;
		Unit<? extends Quantity> unitFrom = Unit.valueOf(jsonUnit.asText());

		if (unitFrom.isCompatible(SI.SQUARE_METRE)) {
			UnitConverter unitConv = unitFrom.getConverterTo(SI.SQUARE_METRE);
			convertedValue = unitConv.convert(jsonValue.asDouble());
		} else {
			throw new FunctionException(String.format(UNIT_NOT_CONVERTIBLE, jsonUnit.asText(), "m^2"));
		}
		return JSON.numberNode(BigDecimal.valueOf(convertedValue));
	}

	@Function(docs = BAGSIZE_DOC)
	public JsonNode bagSize(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return JSON.numberNode(BigDecimal.valueOf(geometry.getNumGeometries()));
	}

	@Function(docs = ONEANDONLY_DOC)
	public JsonNode oneAndOnly(@JsonObject JsonNode jsonGeometryCollection) throws FunctionException {
		GeometryCollection geometryCollection = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollection);

		if (geometryCollection.getNumGeometries() == 1) {
			return GeometryBuilder.toJsonNode(geometryCollection.getGeometryN(0));
		} else {
			throw new FunctionException(INPUT_NOT_GEOCOLLECTION_WITH_ONLY_ONE_GEOM);
		}
	}

	@Function(docs = GEOMETRYISIN_DOC)
	public JsonNode geometryIsIn(@JsonObject JsonNode jsonGeometry, @JsonObject JsonNode jsonGeometryCollection)
			throws FunctionException {
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		GeometryCollection geometryCollection = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollection);
		boolean result = false;

		for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
			if (geometry.equals(geometryCollection.getGeometryN(i))) {
				result = true;
			}
		}
		return JSON.booleanNode(result);
	}

	@Function(docs = GEOMETRYBAG_DOC)
	public JsonNode geometryBag(@JsonObject JsonNode... geometryJsonInput) throws FunctionException {
		Geometry[] geometries = new Geometry[geometryJsonInput.length];
		for (int i = 0; i < geometryJsonInput.length; i++) {
			geometries[i] = GeometryBuilder.fromJsonNode(geometryJsonInput[i]);
		}

		GeometryFactory geomFactory = new GeometryFactory();
		return GeometryBuilder.toJsonNode(geomFactory.createGeometryCollection(geometries));
	}

	@Function(docs = RES_TO_GEOMETRYBAG_DOC)
	public JsonNode resToGeometryBag(@Array JsonNode resourceArray) throws FunctionException {
		ObjectMapper mapper = new ObjectMapper();
		return geometryBag(mapper.convertValue(resourceArray, JsonNode[].class));
	}

	@Function(docs = ATLEASTONEMEMBEROF_DOC)
	public JsonNode atLeastOneMemberOf(@JsonObject JsonNode jsonGeometryCollectionOne,
			@JsonObject JsonNode jsonGeometryCollectionTwo) throws FunctionException {
		GeometryCollection geometryCollectionOne = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollectionOne);
		GeometryCollection geometryCollectionTwo = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollectionTwo);
		boolean result = false;

		for (int i = 0; i < geometryCollectionOne.getNumGeometries(); i++) {
			for (int j = 0; j < geometryCollectionTwo.getNumGeometries(); j++) {
				if (geometryCollectionOne.getGeometryN(i).equals(geometryCollectionTwo.getGeometryN(j))) {
					result = true;
				}
			}
		}
		return JSON.booleanNode(result);
	}

	@Function(docs = SUBSET_DOC)
	public JsonNode subset(@JsonObject JsonNode jsonGeometryCollectionOne,
			@JsonObject JsonNode jsonGeometryCollectionTwo) throws FunctionException {
		GeometryCollection geometryCollectionOne = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollectionOne);
		GeometryCollection geometryCollectionTwo = (GeometryCollection) GeometryBuilder
				.fromJsonNode(jsonGeometryCollectionTwo);
		if (geometryCollectionOne.getNumGeometries() > geometryCollectionTwo.getNumGeometries()) {
			return JSON.booleanNode(false);
		}

		// Use BitSet as more efficient replacement for boolean array
		BitSet resultSet = new BitSet(geometryCollectionOne.getNumGeometries());

		for (int i = 0; i < geometryCollectionOne.getNumGeometries(); i++) {
			for (int j = 0; j < geometryCollectionTwo.getNumGeometries(); j++) {
				if (geometryCollectionOne.getGeometryN(i).equals(geometryCollectionTwo.getGeometryN(j))) {
					resultSet.set(i);
				}
			}
		}

		if (resultSet.cardinality() == geometryCollectionOne.getNumGeometries()) {
			return JSON.booleanNode(true);
		} else {
			return JSON.booleanNode(false);
		}
	}

	@Function(docs = GETPROJECTION_DOC)
	public JsonNode getProjection(@Text JsonNode srcSystem, @Text JsonNode destSystem) throws FunctionException {
		return JSON.textNode(new GeoProjection(srcSystem.asText(), destSystem.asText()).toWkt());
	}

	@Function(docs = PROJECT_DOC)
	public JsonNode project(@JsonObject JsonNode jsonGeometry, @Text JsonNode mathTransform) throws FunctionException {
		GeoProjection projection = new GeoProjection(mathTransform.asText());
		Geometry geometry = GeometryBuilder.fromJsonNode(jsonGeometry);
		return GeometryBuilder.toJsonNode(projection.project(geometry));
	}

	@Function
	public JsonNode print(JsonNode node) {
		LOGGER.info(node.toString());
		return JSON.booleanNode(true);
	}
}
