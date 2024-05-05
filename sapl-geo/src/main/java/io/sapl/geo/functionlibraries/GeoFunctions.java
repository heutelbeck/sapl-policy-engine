/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.geo.functionlibraries;

import java.util.BitSet;
import javax.naming.OperationNotSupportedException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.geo.functions.CrsConst;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.JsonConverter;
import lombok.NoArgsConstructor;

/*
 * Format always [Lat(y), Long(x)]
 */
@Component
@NoArgsConstructor
@FunctionLibrary(name = GeoFunctions.NAME, description = GeoFunctions.DESCRIPTION)
public class GeoFunctions {

    public static final String  NAME                                                                                                                          = "geoFunctions";
    public static final String  DESCRIPTION                                                                                                                   = "Functions enabling location based authorisation and geofencing.";
    private static final String EQUALS_DOC                                                                                                                    = """
            equals(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are exactly (!) equal.  Two Geometries are exactly equal if:
            they have the same structure
            they have the same values for their vertices, in exactly the same order.""";
    private static final String DISJOINT_DOC                                                                                                                  = "disjoint(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are disjoint from each other (not intersecting each other). ";
    private static final String TOUCHES_DOC                                                                                                                   = "touches(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are touching each other.";
    private static final String CROSSES_DOC                                                                                                                   = "crosses(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are crossing each other (having a intersecting area).";
    private static final String WITHIN_DOC                                                                                                                    = "within(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS is fully within GEOMETRYCOLLECTIONTHAT (converse of contains-function). GEOMETRY2 can also be of type GeometryCollection.";
    private static final String CONTAINS_DOC                                                                                                                  = "contains(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS fully contains GEOMETRYCOLLECTIONTHAT (converse of within-function). GEOMETRY1 can also be of type GeometryCollection.";
    private static final String OVERLAPS_DOC                                                                                                                  = "overlaps(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are overlapping.";
    private static final String INTERSECTS_DOC                                                                                                                = "intersects(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries have at least one common intersection point.";
    private static final String BUFFER_DOC                                                                                                                    = "buffer(GEOMETRY, BUFFER_WIDTH): Adds a buffer area of BUFFER_WIDTH around GEOMETRY and returns the new geometry."
            + " BUFFE_RWIDTH is in the units of the coordinates or of the projection (if projection applied)";
    private static final String BOUNDARY_DOC                                                                                                                  = "boundary(GEOMETRY): Returns the boundary of a geometry.";
    private static final String CENTROID_DOC                                                                                                                  = "centroid(GEOMETRY): Returns a point that is the geometric center of gravity of the geometry.";
    private static final String CONVEX_HULL_GEOMETRY_RETURNS_THE_CONVEX_HULL_SMALLEST_CONVEX_POLYGON_THAT_CONTAINS_ALL_POINTS_OF_THE_GEOMETRY_OF_THE_GEOMETRY = "convexHull(GEOMETRY): Returns the convex hull (smallest convex polygon, that contains all points of the geometry) of the geometry.";
    private static final String UNION_DOC                                                                                                                     = "union(GEOMETRYTHIS, GEOMETRYTHAT): Returns the union of two geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
    private static final String INTERSECTION_DOC                                                                                                              = "intersection(GEOMETRYTHIS, GEOMETRYTHAT): Returns the point set intersection of the geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.";
    private static final String DIFFERENCE_DOC                                                                                                                = "difference(GEOMETRYTHIS, GEOMETRYTHAT): Returns the closure of the set difference between two geometries.";
    private static final String BETWEEN_TWO_GEOMETRIES                                                                                                        = "symDifference(GEOMETRYTHIS, GEOMETRY2): Returns the closure of the symmetric difference between two geometries.";
    private static final String DISTANCE_DOC                                                                                                                  = "distance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geometric (planar) distance between two geometries. Does return the value of the unit of the coordinates (or projection if used).";
    private static final String GEO_DISTANCE_DOC                                                                                                              = "geoDistance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geodetic distance of two geometries in [m]. Coordinate Reference System is the un-projected (source) system (WGS84 recommended).";
    private static final String IS_WITHIN_DISTANCE_DOC                                                                                                        = "isWithinDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geometric (planar) distance of each other. "
            + "Uses the unit of the coordinates (or projection if used).";
    private static final String IS_WITHIN_GEO_DISTANCE_DOC                                                                                                    = "isWithinGeoDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geodetic distance of each other. Uses [m] as unit."
            + " Coordinate Reference System is the unprojected (source) system (WGS84 recommended).";
    private static final String LENGTH_DOC                                                                                                                    = "length(GEOMETRY): Returns the length of the geometry (perimeter in case of areal geometries). The returned value is in the units of the coordinates or of the projection (if projection applied).";
    private static final String AREA_DOC                                                                                                                      = "area(GEOMETRY): Returns the area of the geometry. The returned value is in the units (squared) of the coordinates or of the projection (if projection applied).";
    private static final String IS_SIMPLE_DOC                                                                                                                 = "isSimple(GEOMETRY): Returns true if the geometry has no anomalous geometric points (e.g. self interesection, self tangency,...).";
    private static final String IS_VALID_DOC                                                                                                                  = "isValid(GEOMETRY): Returns true if the geometry is topologically valid according to OGC specifications.";
    private static final String IS_CLOSED_DOC                                                                                                                 = "isClosed(GEOMETRY): Returns true if the geometry is either empty or from type (Multi)Point or a closed (Multi)LineString.";

    private static final String MILES_TOMETER_DOC  = "toMeter(VALUE, UNIT): Converts the given VALUE from MILES to [m].";
    private static final String YARDS_TOMETER_DOC  = "toMeter(VALUE, UNIT): Converts the given VALUE from YARDS to [m].";
    private static final String DEGREE_TOMETER_DOC = "toMeter(VALUE, UNIT): Converts the given VALUE from DEGREES to [m].";
    // private static final String TOSQUAREMETER_DOC = "toSquareMeter(VALUE, UNIT):
    // Converts the given VALUE from [UNIT] to [m].";
    private static final String ONE_AND_ONLY_DOC                            = "oneAndOnly(GEOMETRYCOLLECTION): If GEOMETRYCOLLECTION only contains one element, this element will be returned. In all other cases an error will be thrown.";
    private static final String BAG_SIZE_DOC                                = "bagSize(GOEMETRYCOLLECTION): Returns the number of elements in the GEOMETRYCOLLECTION.";
    private static final String GEOMETRY_IS_IN_DOC                          = "geometryIsIn(GEOMETRY, GEOMETRYCOLLECTION): Tests if GEOMETRY is included in GEOMETRYCOLLECTION.";
    private static final String GEOMETRY_BAG_DOC                            = "geometryBag(GEOMETRY,...): Takes any number of GEOMETRY and returns a GEOMETRYCOLLECTION containing all of them.";
    private static final String RES_TO_GEOMETRY_BAG_DOC                     = "resToGeometryBag(RESOURCE_ARRAY): Takes multiple Geometries from RESOURCE_ARRAY and turns them into a GeometryCollection (e.g. geofences from a third party system).";
    private static final String AT_LEAST_ONE_MEMBER_OF_DOC                  = "atLeastOneMemberOf(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns TRUE if at least one member of GEOMETRYCOLLECTIONTHIS is contained in GEOMETRYCOLLECTIONTHAT.";
    private static final String SUBSET_DOC                                  = "subset(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns true, if GEOMETRYCOLLECTIONTHIS is a subset of GEOMETRYCOLLECTIONTHAT.";
    private static final String INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM = "Input must be a GeometryCollection containing only one Geometry.";

    @Function(name = "equalsExact", docs = EQUALS_DOC)
    public Val geometryEquals(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(geometryEquals(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {

            return Val.error(e);
        }
    }

    public Boolean geometryEquals(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .equalsExact(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = DISJOINT_DOC)
    public Val disjoint(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(disjoint(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean disjoint(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .disjoint(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = TOUCHES_DOC)
    public Val touches(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(touches(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean touches(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .touches(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = CROSSES_DOC)
    public Val crosses(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(crosses(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean crosses(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .crosses(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = WITHIN_DOC)
    public Val within(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(within(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean within(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        Geometry geometryThis = JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString());
        Geometry geometryThat = JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString());
        return (geometryThat instanceof GeometryCollection) ? geometryThis.within(geometryThat.union())
                : geometryThis.within(geometryThat);
    }

    @Function(docs = CONTAINS_DOC)
    public Val contains(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(contains(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean contains(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        Geometry geometryThis = JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString());
        Geometry geometryThat = JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString());
        return (geometryThis instanceof GeometryCollection) ? geometryThis.union().contains(geometryThat)
                : geometryThis.contains(geometryThat);
    }

    @Function(docs = OVERLAPS_DOC)
    public Val overlaps(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(overlaps(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean overlaps(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .overlaps(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = INTERSECTS_DOC)
    public Val intersects(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(intersects(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean intersects(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat)
            throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .intersects(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()));
    }

    @Function(docs = BUFFER_DOC)
    public Val buffer(@JsonObject Val jsonGeometry, @Number Val buffer) {
        try {
            return buffer(jsonGeometry.get(), buffer.get().asDouble());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val buffer(@JsonObject JsonNode jsonGeometry, @Number Double buffer) throws ParseException {
        
    	
    	
    	var a = GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).buffer(buffer));
    	
    	return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).buffer(buffer));
    }

    @Function(docs = BOUNDARY_DOC)
    public Val boundary(@JsonObject Val jsonGeometry) {
        try {
            return boundary(jsonGeometry.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val boundary(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getBoundary());
    }

    @Function(docs = CENTROID_DOC)
    public Val centroid(@JsonObject Val jsonGeometry) {
        try {
            return centroid(jsonGeometry.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val centroid(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getCentroid());
    }

    @Function(docs = CONVEX_HULL_GEOMETRY_RETURNS_THE_CONVEX_HULL_SMALLEST_CONVEX_POLYGON_THAT_CONTAINS_ALL_POINTS_OF_THE_GEOMETRY_OF_THE_GEOMETRY)
    public Val convexHull(@JsonObject Val jsonGeometry) {
        try {
            return convexHull(jsonGeometry.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val convexHull(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return GeometryConverter
                .geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).convexHull());
    }

    @Function(docs = UNION_DOC)
    public Val union(@JsonObject Val... jsonGeometries) {

        try {
            JsonNode[] geometries = new JsonNode[jsonGeometries.length];
            for (int i = 0; i < jsonGeometries.length; i++) {
                geometries[i] = jsonGeometries[i].get();
            }
            return union(geometries);
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val union(@JsonObject JsonNode... jsonGeometries) throws ParseException {
        if (jsonGeometries.length == 1) {
            return Val.of(jsonGeometries[0]);
        }
        Geometry geomUnion = JsonConverter.geoJsonToGeometry(jsonGeometries[0].toPrettyString());
        for (int i = 1; i < jsonGeometries.length; i++) {
            Geometry additionalGeom = JsonConverter.geoJsonToGeometry(jsonGeometries[i].toPrettyString());
            geomUnion = geomUnion.union(additionalGeom);
        }
        return GeometryConverter.geometryToGeoJsonNode(geomUnion);
    }

    @Function(docs = INTERSECTION_DOC)
    public Val intersection(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return intersection(geoJsonThis.get(), geoJsonThat.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val intersection(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .intersection(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = DIFFERENCE_DOC)
    public Val difference(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return difference(geoJsonThis.get(), geoJsonThat.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val difference(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .difference(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = BETWEEN_TWO_GEOMETRIES)
    public Val symDifference(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return symDifference(geoJsonThis.get(), geoJsonThat.get());
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val symDifference(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return GeometryConverter.geometryToGeoJsonNode(JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .symDifference(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = DISTANCE_DOC)
    public Val distance(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat) {
        try {
            return Val.of(distance(geoJsonThis.get(), geoJsonThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public double distance(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat) throws ParseException {
        return (JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .distance(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString())));
    }

    @Function(docs = IS_WITHIN_DISTANCE_DOC)
    public Val isWithinDistance(@JsonObject Val geoJsonThis, @JsonObject Val geoJsonThat, @Number Val distInput) {
        try {
            return Val.of(isWithinDistance(geoJsonThis.get(), geoJsonThat.get(), distInput.get().asDouble()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean isWithinDistance(@JsonObject JsonNode geoJsonThis, @JsonObject JsonNode geoJsonThat,
            @Number Double distInput) throws ParseException {
        return JsonConverter.geoJsonToGeometry(geoJsonThis.toPrettyString())
                .isWithinDistance(JsonConverter.geoJsonToGeometry(geoJsonThat.toPrettyString()), distInput);
    }

    @Function(docs = GEO_DISTANCE_DOC)
    public Val geoDistance(@JsonObject Val jsonGeometryThis, @JsonObject Val jsonGeometryThat)
            throws FactoryException, TransformException {
        try {
            return Val.of(geoDistance(jsonGeometryThis.get(), jsonGeometryThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    @Function(docs = GEO_DISTANCE_DOC)
    public Val geoDistance(@JsonObject Val jsonGeometryThis, @JsonObject Val jsonGeometryThat,
            Val coordinateReferenceSystem) {
        try {
            return Val.of(
                    geoDistance(jsonGeometryThis.get(), jsonGeometryThat.get(), coordinateReferenceSystem.getText()));
        } catch (Exception e) {
            return Val.error(e);
        }
    }

    public double geoDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat)
            throws ParseException, FactoryException, TransformException {
        return geoDistance(jsonGeometryThis, jsonGeometryThat, CrsConst.WGS84_CRS.getValue());
    }

    public double geoDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat, String coordinateReferenceSystem)
            throws ParseException, NoSuchAuthorityCodeException, FactoryException, TransformException {
        return geodesicDistance(jsonGeometryThis, jsonGeometryThat, coordinateReferenceSystem);

    }

    private double geodesicDistance(JsonNode jsonGeometryThis, JsonNode jsonGeometryThat,
            String coordinateReferenceSystem) throws ParseException, FactoryException, TransformException {
        Geometry geometryThis = JsonConverter.geoJsonToGeometry(jsonGeometryThis.toPrettyString());
        Geometry geometryThat = JsonConverter.geoJsonToGeometry(jsonGeometryThat.toPrettyString());

        CoordinateReferenceSystem crs    = CRS.decode(coordinateReferenceSystem);
        DistanceOp                distOp = new DistanceOp(geometryThis, geometryThat);
        GeodeticCalculator        gc     = new GeodeticCalculator(crs);

        gc.setStartingPosition(JTS.toDirectPosition(distOp.nearestPoints()[0], crs));
        gc.setDestinationPosition(JTS.toDirectPosition(distOp.nearestPoints()[1], crs));

        return gc.getOrthodromicDistance();
    }

    @Function(docs = IS_WITHIN_GEO_DISTANCE_DOC)
    public Val isWithinGeoDistance(@JsonObject Val jsonGeometryThis, @JsonObject Val jsonGeometryThat,
            @Number Val distance) {
        try {
            return Val
                    .of(isWithinGeoDistance(jsonGeometryThis.get(), jsonGeometryThat.get(), distance.get().asDouble()));
        } catch (TransformException | FactoryException | ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean isWithinGeoDistance(@JsonObject JsonNode jsonGeometryThis, @JsonObject JsonNode jsonGeometryThat,
            @Number double distance) throws TransformException, FactoryException, ParseException {
        return geoDistance(jsonGeometryThis, jsonGeometryThat) <= distance;
    }

    @Function(docs = LENGTH_DOC)
    public Val length(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(length(jsonGeometry.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Double length(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getLength();
    }

    @Function(docs = AREA_DOC)
    public Val area(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(area(jsonGeometry.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Double area(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getArea();
    }

    @Function(docs = IS_SIMPLE_DOC)
    public Val isSimple(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(isSimple(jsonGeometry.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean isSimple(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).isSimple();
    }

    @Function(docs = IS_VALID_DOC)
    public Val isValid(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(isValid(jsonGeometry.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean isValid(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).isValid();
    }

    @Function(docs = IS_CLOSED_DOC)
    public Val isClosed(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(isClosed(jsonGeometry.get()));
        } catch (OperationNotSupportedException | ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean isClosed(@JsonObject JsonNode jsonGeometry) throws ParseException, OperationNotSupportedException {
        Geometry geometry = JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString());

        if (geometry.isEmpty() || (geometry.getGeometryType().equals(Geometry.TYPENAME_POINT))
                || (geometry.getGeometryType().equals(Geometry.TYPENAME_MULTIPOINT))) {
            return true;
        }
        switch (geometry.getGeometryType()) {
        case Geometry.TYPENAME_LINESTRING:
            return ((LineString) geometry).isClosed();
        case Geometry.TYPENAME_MULTILINESTRING:
            return ((MultiLineString) geometry).isClosed();
        case Geometry.TYPENAME_LINEARRING:
            return ((LinearRing) geometry).isClosed();
        default:
            throw new OperationNotSupportedException(
                    "Operation isClosed is not applicable for the type " + geometry.getGeometryType());
        }

    }

    @Function(docs = MILES_TOMETER_DOC)
    public Val milesToMeter(@Number Val jsonValue) {
        try {
            return Val.of(milesToMeter(jsonValue.get()));
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        }
    }

    public JsonNode milesToMeter(@Number JsonNode jsonValue) throws IllegalArgumentException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.convertValue(milesToMeter(jsonValue.asDouble()), JsonNode.class);
    }

    public double milesToMeter(@Number double value) {
        return value * DistanceUtils.MILES_TO_KM * 1000;
    }

    @Function(docs = YARDS_TOMETER_DOC)
    public Val yardToMeter(@Number Val jsonValue) {
        try {
            return Val.of(yardToMeter(jsonValue.get()));
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        }
    }

    public JsonNode yardToMeter(@Number JsonNode jsonValue) throws IllegalArgumentException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.convertValue(milesToMeter(jsonValue.asDouble() / 1760), JsonNode.class);
    }

    @Function(docs = DEGREE_TOMETER_DOC)
    public Val degreeToMeter(@Number Val jsonValue) {
        try {
            return Val.of(degreeToMeter(jsonValue.get()));
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        }
    }

    public JsonNode degreeToMeter(@Number JsonNode jsonValue) throws IllegalArgumentException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.convertValue(degreeToMeter(jsonValue.asDouble()), JsonNode.class);
    }

    public double degreeToMeter(@Number double value) {
        return value * DistanceUtils.DEG_TO_KM * 1000;
    }

    @Function(docs = BAG_SIZE_DOC)
    public Val bagSize(@JsonObject Val jsonGeometry) {
        try {
            return Val.of(bagSize(jsonGeometry.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public int bagSize(@JsonObject JsonNode jsonGeometry) throws ParseException {
        return JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString()).getNumGeometries();
    }

    @Function(docs = ONE_AND_ONLY_DOC)
    public Val oneAndOnly(@JsonObject Val jsonGeometryCollection) {
        try {
            return oneAndOnly(jsonGeometryCollection.get());
        } catch (OperationNotSupportedException | ClassCastException | ParseException e) {
            return Val.error(e);
        }
    }

    public Val oneAndOnly(@JsonObject JsonNode jsonGeometryCollection)
            throws ParseException, OperationNotSupportedException, ClassCastException {
        GeometryCollection geometryCollection = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollection.toPrettyString());
        if (geometryCollection.getNumGeometries() == 1) {
            return GeometryConverter.geometryToGeoJsonNode(geometryCollection.getGeometryN(0));
        } else {
            throw new OperationNotSupportedException(INPUT_NOT_GEO_COLLECTION_WITH_ONLY_ONE_GEOM);
        }
    }

    @Function(docs = GEOMETRY_IS_IN_DOC)
    public Val geometryIsIn(@JsonObject Val jsonGeometry, @JsonObject Val jsonGeometryCollection) {

        try {
            return Val.of(geometryIsIn(jsonGeometry.get(), jsonGeometryCollection.get()));
        } catch (ClassCastException | ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean geometryIsIn(@JsonObject JsonNode jsonGeometry, @JsonObject JsonNode jsonGeometryCollection)
            throws ParseException, ClassCastException {
        Geometry           geometry           = JsonConverter.geoJsonToGeometry(jsonGeometry.toPrettyString());
        GeometryCollection geometryCollection = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollection.toPrettyString());

        for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
            if (geometry.equals(geometryCollection.getGeometryN(i))) {
                return true;
            }
        }
        return false;
    }

    @Function(docs = GEOMETRY_BAG_DOC)
    public Val geometryBag(@JsonObject Val... geometryJsonInput) {

        try {
            JsonNode[] geometries = new JsonNode[geometryJsonInput.length];
            for (int i = 0; i < geometryJsonInput.length; i++) {
                geometries[i] = geometryJsonInput[i].get();
            }

            return geometryBag(geometries);
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Val geometryBag(@JsonObject JsonNode... geometryJsonInput) throws ParseException {
        Geometry[] geometries = new Geometry[geometryJsonInput.length];
        for (int i = 0; i < geometryJsonInput.length; i++) {
            geometries[i] = JsonConverter.geoJsonToGeometry(geometryJsonInput[i].toPrettyString());
        }

        GeometryFactory geomFactory = new GeometryFactory();
        return GeometryConverter.geometryToGeoJsonNode(geomFactory.createGeometryCollection(geometries));
    }

    @Function(docs = RES_TO_GEOMETRY_BAG_DOC)
    public Val resToGeometryBag(@Array Val resourceArray) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode[]   nodes  = mapper.convertValue(resourceArray.get(), JsonNode[].class);

        Val[] vals = new Val[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            vals[i] = Val.of(nodes[i]);
        }
        return geometryBag(vals);
    }

    @Function(docs = AT_LEAST_ONE_MEMBER_OF_DOC)
    public Val atLeastOneMemberOf(@JsonObject Val jsonGeometryCollectionThis,
            @JsonObject Val jsonGeometryCollectionThat) {
        try {
            return Val.of(atLeastOneMemberOf(jsonGeometryCollectionThis.get(), jsonGeometryCollectionThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean atLeastOneMemberOf(@JsonObject JsonNode jsonGeometryCollectionThis,
            @JsonObject JsonNode jsonGeometryCollectionThat) throws ParseException {
        GeometryCollection geometryCollectionThis = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThis.toPrettyString());
        GeometryCollection geometryCollectionThat = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThat.toPrettyString());

        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Function(docs = SUBSET_DOC)
    public Val subset(@JsonObject Val jsonGeometryCollectionThis, @JsonObject Val jsonGeometryCollectionThat) {

        try {
            return Val.of(subset(jsonGeometryCollectionThis.get(), jsonGeometryCollectionThat.get()));
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    public Boolean subset(@JsonObject JsonNode jsonGeometryCollectionThis,
            @JsonObject JsonNode jsonGeometryCollectionThat) throws ParseException {
        GeometryCollection geometryCollectionThis = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThis.toPrettyString());
        GeometryCollection geometryCollectionThat = (GeometryCollection) JsonConverter
                .geoJsonToGeometry(jsonGeometryCollectionThat.toPrettyString());
        if (geometryCollectionThis.getNumGeometries() > geometryCollectionThat.getNumGeometries()) {
            return false;
        }

        // Use BitSet as more efficient replacement for boolean array
        BitSet resultSet = new BitSet(geometryCollectionThis.getNumGeometries());

        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    resultSet.set(i);
                }
            }
        }

        return (resultSet.cardinality() == geometryCollectionThis.getNumGeometries());
    }

}
