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
import lombok.extern.slf4j.Slf4j;

/*
 * Format always [Lat(y), Long(x)]
 */

@Slf4j
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
	private static final String ENABLEPROJ_DOC = "enableProjection(SRCSYSTEM, DESTSYSTEM): Enables the projection from SRCSYSTEM to DESTSYSTEM (must be provided in ESPG-format).";
	private static final String DISABLEPROJ_DOC = "disableProjection(): Disables projection of geometries.";
	private static final String PROJECT_DOC = "project(GEOMETRY): Returns the projected geometry (or the geometry itself in case no projection is defined)";

	private static final String INPUT_NOT_GEOCOLLECTION_WITH_ONLY_ONE_GEOM = "Input must be a GeometryCollection containing only one Geometry.";
	private static final String UNIT_NOT_CONVERTIBLE = "Given unit '%s' is not convertible to '%s'.";
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private GeoProjection projection;

	public GeoFunctionLibrary(GeoProjection geoProj) {
		projection = geoProj;
	}

	public GeoFunctionLibrary() {
		// standard configuration - no projection to be applied
	}

	@Function(name = "equals", docs = EQUALS_DOC)
	public JsonNode geometryEquals(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().equals(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = DISJOINT_DOC)
	public JsonNode disjoint(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().disjoint(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = TOUCHES_DOC)
	public JsonNode touches(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().touches(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = CROSSES_DOC)
	public JsonNode crosses(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().crosses(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = WITHIN_DOC)
	public JsonNode within(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		if (saplGeometryTwo.getGeometry() instanceof GeometryCollection) {
			return JSON.booleanNode(saplGeometryOne.getGeometry().within(saplGeometryTwo.getGeometry().union()));
		} else {
			return JSON.booleanNode(saplGeometryOne.getGeometry().within(saplGeometryTwo.getGeometry()));
		}
	}

	@Function(docs = CONTAINS_DOC)
	public JsonNode contains(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		if (saplGeometryOne.getGeometry() instanceof GeometryCollection) {
			return JSON.booleanNode(saplGeometryOne.getGeometry().union().contains(saplGeometryTwo.getGeometry()));
		} else {
			return JSON.booleanNode(saplGeometryOne.getGeometry().contains(saplGeometryTwo.getGeometry()));
		}
	}

	@Function(docs = OVERLAPS_DOC)
	public JsonNode overlaps(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().overlaps(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = INTERSECTS_DOC)
	public JsonNode intersects(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.booleanNode(saplGeometryOne.getGeometry().intersects(saplGeometryTwo.getGeometry()));
	}

	@Function(docs = BUFFER_DOC)
	public JsonNode buffer(@JsonObject JsonNode jsonGeometry, @Number JsonNode buffer) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);
		saplGeometry.setGeometry(saplGeometry.getGeometry().buffer(buffer.asDouble()));

		return saplGeometry.toJsonNode();
	}

	@Function(docs = BOUNDARY_DOC)
	public JsonNode boundary(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);
		saplGeometry.setGeometry(saplGeometry.getGeometry().getBoundary());

		return saplGeometry.toJsonNode();
	}

	@Function(docs = CENTROID_DOC)
	public JsonNode centroid(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);
		saplGeometry.setGeometry(saplGeometry.getGeometry().getCentroid());

		return saplGeometry.toJsonNode();
	}

	@Function(docs = CONVEXHULL_DOC)
	public JsonNode convexHull(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);
		saplGeometry.setGeometry(saplGeometry.getGeometry().convexHull());

		return saplGeometry.toJsonNode();
	}

	@Function(docs = UNION_DOC)
	public JsonNode union(@JsonObject JsonNode... jsonGeometries) throws FunctionException {
		if (jsonGeometries.length == 1) {
			return jsonGeometries[0];
		}

		SAPLGeometry geomUnion = new SAPLGeometry(jsonGeometries[0], projection);
		for (int i = 1; i < jsonGeometries.length; i++) {
			SAPLGeometry additionalGeom = new SAPLGeometry(jsonGeometries[i], projection);
			geomUnion.setGeometry(geomUnion.getGeometry().union(additionalGeom.getGeometry()));
		}

		return geomUnion.toJsonNode();
	}

	@Function(docs = INTERSECTION_DOC)
	public JsonNode intersection(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne, projection);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo, projection);
		saplGeometryOne.setGeometry(saplGeometryOne.getGeometry().intersection(saplGeometryTwo.getGeometry()));

		return saplGeometryOne.toJsonNode();
	}

	@Function(docs = DIFFERENCE_DOC)
	public JsonNode difference(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne, projection);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo, projection);
		saplGeometryOne.setGeometry(saplGeometryOne.getGeometry().difference(saplGeometryTwo.getGeometry()));

		return saplGeometryOne.toJsonNode();
	}

	@Function(docs = SYMDIFFERENCE_DOC)
	public JsonNode symDifference(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne, projection);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo, projection);
		saplGeometryOne.setGeometry(saplGeometryOne.getGeometry().symDifference(saplGeometryTwo.getGeometry()));

		return saplGeometryOne.toJsonNode();
	}

	@Function(docs = DISTANCE_DOC)
	public JsonNode distance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne, projection);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo, projection);

		return JSON
				.numberNode(BigDecimal.valueOf(saplGeometryOne.getGeometry().distance(saplGeometryTwo.getGeometry())));
	}

	@Function(docs = GEODISTANCE_DOC)
	public JsonNode geoDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo)
			throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		return JSON.numberNode(BigDecimal.valueOf(saplGeometryOne.geodesicDistance(saplGeometryTwo)));
	}

	@Function(docs = ISWITHINDISTANCE_DOC)
	public JsonNode isWithinDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo,
			@Number JsonNode distInput) throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne, projection);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo, projection);
		double distance = distInput.asDouble();

		return JSON
				.booleanNode(saplGeometryOne.getGeometry().isWithinDistance(saplGeometryTwo.getGeometry(), distance));
	}

	@Function(docs = ISWITHINGEODISTANCE_DOC)
	public JsonNode isWithinGeoDistance(@JsonObject JsonNode jsonGeometryOne, @JsonObject JsonNode jsonGeometryTwo,
			@Number JsonNode distInput) throws FunctionException {
		SAPLGeometry saplGeometryOne = new SAPLGeometry(jsonGeometryOne);
		SAPLGeometry saplGeometryTwo = new SAPLGeometry(jsonGeometryTwo);

		double distance = distInput.asDouble();

		if (saplGeometryOne.geodesicDistance(saplGeometryTwo) <= distance) {
			return JSON.booleanNode(true);
		} else {
			return JSON.booleanNode(false);
		}
	}

	@Function(docs = LENGTH_DOC)
	public JsonNode length(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);

		return JSON.numberNode(BigDecimal.valueOf(saplGeometry.getGeometry().getLength()));
	}

	@Function(docs = AREA_DOC)
	public JsonNode area(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry, projection);

		return JSON.numberNode(BigDecimal.valueOf(saplGeometry.getGeometry().getArea()));
	}

	@Function(docs = ISSIMPLE_DOC)
	public JsonNode isSimple(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry);

		return JSON.booleanNode(saplGeometry.getGeometry().isSimple());
	}

	@Function(docs = ISVALID_DOC)
	public JsonNode isValid(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry saplGeometry = new SAPLGeometry(jsonGeometry);

		return JSON.booleanNode(saplGeometry.getGeometry().isValid());
	}

	@Function(docs = ISCLOSED_DOC)
	public JsonNode isClosed(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		Geometry geometry = new SAPLGeometry(jsonGeometry).getGeometry();
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
		SAPLGeometry geometry = new SAPLGeometry(jsonGeometry);
		return JSON.numberNode(BigDecimal.valueOf(geometry.getGeometry().getNumGeometries()));
	}

	@Function(docs = ONEANDONLY_DOC)
	public JsonNode oneAndOnly(@JsonObject JsonNode jsonGeometryCollection) throws FunctionException {
		GeometryCollection geometryCollection = (GeometryCollection) new SAPLGeometry(jsonGeometryCollection)
				.getGeometry();

		if (geometryCollection.getNumGeometries() == 1) {
			return new SAPLGeometry(geometryCollection.getGeometryN(0)).toJsonNode();
		} else {
			throw new FunctionException(INPUT_NOT_GEOCOLLECTION_WITH_ONLY_ONE_GEOM);
		}
	}

	@Function(docs = GEOMETRYISIN_DOC)
	public JsonNode geometryIsIn(@JsonObject JsonNode jsonGeometry, @JsonObject JsonNode jsonGeometryCollection)
			throws FunctionException {
		Geometry geometry = new SAPLGeometry(jsonGeometry).getGeometry();
		GeometryCollection geometryCollection = (GeometryCollection) new SAPLGeometry(jsonGeometryCollection)
				.getGeometry();
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
			geometries[i] = new SAPLGeometry(geometryJsonInput[i]).getGeometry();
		}

		GeometryFactory geomFactory = new GeometryFactory();
		return new SAPLGeometry(geomFactory.createGeometryCollection(geometries)).toJsonNode();
	}

	@Function(docs = RES_TO_GEOMETRYBAG_DOC)
	public JsonNode resToGeometryBag(@Array JsonNode resourceArray) throws FunctionException {
		ObjectMapper mapper = new ObjectMapper();
		return geometryBag(mapper.convertValue(resourceArray, JsonNode[].class));
	}

	@Function(docs = ATLEASTONEMEMBEROF_DOC)
	public JsonNode atLeastOneMemberOf(@JsonObject JsonNode jsonGeometryCollectionOne,
			@JsonObject JsonNode jsonGeometryCollectionTwo) throws FunctionException {
		GeometryCollection geometryCollectionOne = (GeometryCollection) new SAPLGeometry(jsonGeometryCollectionOne)
				.getGeometry();
		GeometryCollection geometryCollectionTwo = (GeometryCollection) new SAPLGeometry(jsonGeometryCollectionOne)
				.getGeometry();
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
		GeometryCollection geometryCollectionOne = (GeometryCollection) new SAPLGeometry(jsonGeometryCollectionOne)
				.getGeometry();
		GeometryCollection geometryCollectionTwo = (GeometryCollection) new SAPLGeometry(jsonGeometryCollectionTwo)
				.getGeometry();
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

	@Function(docs = ENABLEPROJ_DOC)
	public JsonNode enableProjection(@Text JsonNode srcSystem, @Text JsonNode destSystem) throws FunctionException {
		projection = new GeoProjection(srcSystem.asText(), destSystem.asText());
		return JSON.booleanNode(true);
	}

	@Function(docs = DISABLEPROJ_DOC)
	public JsonNode disableProjection() {
		projection = GeoProjection.returnEmptyProjection();
		return JSON.booleanNode(true);
	}

	@Function(docs = PROJECT_DOC)
	public JsonNode project(@JsonObject JsonNode jsonGeometry) throws FunctionException {
		SAPLGeometry geometry = new SAPLGeometry(jsonGeometry, projection);
		geometry.setProjection(GeoProjection.returnEmptyProjection());
		return geometry.toJsonNode();
	}

	@Function
	public JsonNode print(JsonNode node) {
		log.info(node.toString());
		return JSON.booleanNode(true);
	}
}
