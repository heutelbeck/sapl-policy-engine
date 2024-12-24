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
package io.sapl.functions.geo;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import org.geotools.api.feature.Feature;
import org.geotools.api.feature.Property;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.xsd.Parser;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.spatial4j.distance.DistanceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = "geo", description = GeographicFunctionLibrary.DESCRIPTION, libraryDocumentation = GeographicFunctionLibrary.DOCUMENTATION)
public class GeographicFunctionLibrary {

    private static final String DESCRIPTION   = "A function library to manipulate, inspect, and convert geograpihc data.";
    private static final String DOCUMENTATION = """
            A function library to manipulate, inspect, and convert geograpihc data.
            """;

    static final String FAILED_TO_PARSE_GML_ERROR            = "Failed to parse GML.";
    static final String FAILED_TO_PARSE_KML_ERROR            = "Failed to parse KML.";
    static final String INCORRECT_NUMER_OF_GEOEMTRIES_ERROR  = "Input must be a GeometryCollection containing only one Geometry.";
    static final String INVALID_WKT_ERROR                    = "Invalid WKT.";
    static final String NO_GEOMETRIES_IN_GML_ERROR           = "No geometries in GML.";
    static final String NO_GEOMETRIES_IN_KML_ERROR           = "No geometries in KML.";
    static final String IS_CLOSED_NOT_APPLICABLE_FOR_S_ERROR = "Operation isClosed is not applicable for the type %s.";
    static final String NOT_A_GEOMETRY_COLLECTION_ERROR      = "The second parameter of the geometryIsIn was not a geometry collection.";

    private static final String WGS84 = "EPSG:4326";

    private static final GeoJsonReader   GEOJSON_READER   = new GeoJsonReader();
    private static final GeoJsonWriter   GEOJSON_WRITER   = new GeoJsonWriter();
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final Parser          GML2_READER      = new Parser(new org.geotools.gml2.GMLConfiguration());
    private static final Parser          GML3_READER      = new Parser(new org.geotools.gml3.GMLConfiguration());
    private static final Parser          KML_READER       = new Parser(new KMLConfiguration());
    private static final GeometryFactory WGS84_FACTORY    = new GeometryFactory(new PrecisionModel(), 4326);
    private static final WKTReader       WKT_READER       = new WKTReader();

    /*
     * Geometry Comparisons
     */

    @Function(docs = """
            equalsExact(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are exactly equal. Two Geometries are exactly equal if:
            they have the same structure
            they have the same values for their vertices, in exactly the same order.""")
    public Val equalsExact(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::equalsExact);
    }

    @Function(docs = "disjoint(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are disjoint from each other (not intersecting each other). ")
    public Val disjoint(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::disjoint);
    }

    @Function(docs = "touches(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are touching each other.")
    public Val touches(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::touches);
    }

    @Function(docs = "crosses(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are crossing each other (having a intersecting area).")
    public Val crosses(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::crosses);
    }

    @Function(docs = """
            within(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS is fully within GEOMETRYCOLLECTIONTHAT (converse of contains-function).
            GEOMETRY2 can also be of type GeometryCollection.""")
    public Val within(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> (that instanceof GeometryCollection) ? thiz.within(that.union()) : thiz.within(that));
    }

    @Function(docs = """
            contains(GEOMETRYTHIS, GEOMETRYTHAT): Tests if the GEOMETRYCOLLECTIONTHIS fully contains GEOMETRYCOLLECTIONTHAT (converse of within-function).
            GEOMETRY1 can also be of type GeometryCollection.""")
    public Val contains(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, (thiz,
                that) -> (thiz instanceof GeometryCollection) ? thiz.union().contains(that) : thiz.contains(that));
    }

    @Function(docs = "overlaps(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries are overlapping.")
    public Val overlaps(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::overlaps);
    }

    @Function(docs = "intersects(GEOMETRYTHIS, GEOMETRYTHAT): Tests if two geometries have at least one common intersection point.")
    public Val intersects(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::intersects);
    }

    @Function(docs = """
            isWithinDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geometric (planar) distance of each other.
            Uses the unit of the coordinates (or projection if used).""")
    public Val isWithinDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry, @Number Val distance) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> thiz.isWithinDistance(that, distance.get().asDouble()));
    }

    @Function(docs = """
            isWithinGeoDistance(GEOMETRYTHIS, GEOMETRYTHAT, DISTANCE): Tests if two geometries are within the given geodetic distance of each other. Uses [m] as unit.
            Coordinate Reference System is the unprojected (source) system (WGS84 recommended).""")
    public Val isWithinGeoDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry, @Number Val distance) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> geodesicDistance(thiz, that) <= distance.get().asDouble());
    }

    private Val testGeometryBiPredicate(Val thisGeometry, Val thatGeometry, BiPredicate<Geometry, Geometry> predicate) {
        return Val.of(predicate.test(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry)));
    }

    /*
     * Geometry Predicates
     */

    @Function(docs = "isSimple(GEOMETRY): Returns true if the geometry has no anomalous geometric points (e.g. self interesection, self tangency,...).")
    public Val isSimple(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).isSimple());
    }

    @Function(docs = "isValid(GEOMETRY): Returns true if the geometry is topologically valid according to OGC specifications.")
    public Val isValid(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).isValid());
    }

    @Function(docs = "isClosed(GEOMETRY): Returns true if the geometry is either empty or from type (Multi)Point or a closed (Multi)LineString.")
    public Val isClosed(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val jsonGeometry) {
        final var geometry = geoJsonToGeometry(jsonGeometry);
        if (Geometry.TYPENAME_POINT.equals(geometry.getGeometryType())
                || Geometry.TYPENAME_MULTIPOINT.equals(geometry.getGeometryType())) {
            return Val.TRUE;
        }
        switch (geometry.getGeometryType()) {
        case Geometry.TYPENAME_LINESTRING:
            return Val.of(((LineString) geometry).isClosed());
        case Geometry.TYPENAME_MULTILINESTRING:
            return Val.of(((MultiLineString) geometry).isClosed());
        default:
            return Val.error(String.format(IS_CLOSED_NOT_APPLICABLE_FOR_S_ERROR, geometry.getGeometryType()));
        }
    }

    /*
     * Geometry Operations
     */

    @Function(docs = """
            buffer(GEOMETRY, BUFFER_WIDTH): Adds a buffer area of BUFFER_WIDTH around GEOMETRY and returns the new geometry.
            BUFFER_WIDTH is in the units of the coordinates or of the projection (if projection applied)""", schema = GeoJSONSchemata.POLYGON)
    public Val buffer(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry, @Number Val buffer) {
        return applyUnaryTransformation(geometry, g -> g.buffer(buffer.get().asDouble()));
    }

    @Function(docs = "boundary(GEOMETRY): Returns the boundary of a geometry.", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val boundary(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::getBoundary);
    }

    @Function(docs = "centroid(GEOMETRY): Returns a point that is the geometric center of gravity of the geometry.", schema = GeoJSONSchemata.POINT)
    public Val centroid(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::getCentroid);
    }

    @Function(docs = "convexHull(GEOMETRY): Returns the convex hull (smallest convex polygon, that contains all points of the geometry) of the geometry.", schema = GeoJSONSchemata.CONVEX_HULL)
    public Val convexHull(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::convexHull);
    }

    private Val applyUnaryTransformation(Val thisGeometry, UnaryOperator<Geometry> transformation) {
        return geometryToGeoJSON(transformation.apply(geoJsonToGeometry(thisGeometry)));
    }

    @Function(docs = "union(GEOMETRYTHIS, GEOMETRYTHAT): Returns the union of two geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val union(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val... jsonGeometries) {
        if (jsonGeometries.length == 0) {
            return geometryToGeoJSON(WGS84_FACTORY.createEmpty(-1));
        }

        var geomUnion = geoJsonToGeometry(jsonGeometries[0]);
        for (int i = 1; i < jsonGeometries.length; i++) {
            geomUnion = geomUnion.union(geoJsonToGeometry(jsonGeometries[i]));
        }
        return geometryToGeoJSON(geomUnion);
    }

    @Function(docs = "intersection(GEOMETRYTHIS, GEOMETRYTHAT): Returns the point set intersection of the geometries. GEOMETRY can also be a GEOMETRYCOLLECTION.", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val intersection(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::intersection);
    }

    @Function(docs = "difference(GEOMETRYTHIS, GEOMETRYTHAT): Returns the closure of the set difference between two geometries.", schema = GeoJSONSchemata.GEOMETRIES)
    public Val difference(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::difference);
    }

    @Function(docs = "symDifference(GEOMETRYTHIS, GEOMETRY2): Returns the closure of the symmetric difference between two geometries.", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val symDifference(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::symDifference);
    }

    private Val applyBinaryTransformation(Val thisGeometry, Val thatGeometry, BinaryOperator<Geometry> transformation) {
        return geometryToGeoJSON(
                transformation.apply(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry)));
    }

    /*
     * Measuring
     */

    @Function(docs = """
            distance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geometric (planar) distance between two geometries.
            Does return the value of the unit of the coordinates (or projection if used).""")
    public Val distance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return Val.of(geoJsonToGeometry(thisGeometry).distance(geoJsonToGeometry(thatGeometry)));
    }

    @Function(name = "geodesicDistance", docs = """
            geodesicDistance(GEOMETRYTHIS, GEOMETRYTHAT): Returns the (shortest) geodetic distance of two geometries in [m].
            Coordinate Reference System is the un-projected (source) system (WGS84 recommended).""")
    public Val geoDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return Val.of(geodesicDistance(thisGeometry, thatGeometry));
    }

    @Function(name = "geodesicDistance", docs = """
            geodesicDistance(GEOMETRYTHIS, GEOMETRYTHAT, COORDINATE SYSTEM): Returns the (shortest) geodetic distance of two geometries in [m].
            Coordinate Reference System is the un-projected (source) system (WGS84 recommended).""")
    public Val geoDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThis,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThat,
            Val coordinateReferenceSystem) {
        return Val.of(geodesicDistance(jsonGeometryThis, jsonGeometryThat, coordinateReferenceSystem.getText()));
    }

    private double geodesicDistance(Val thisGeometry, Val thatGeometry) {
        return geodesicDistance(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry));
    }

    private double geodesicDistance(Val thisGeometry, Val thatGeometry, String coordinateReferenceSystem) {
        return geodesicDistance(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry),
                coordinateReferenceSystem);
    }

    private double geodesicDistance(Geometry thisGeometry, Geometry thatGeometry) {
        return geodesicDistance(thisGeometry, thatGeometry, WGS84);
    }

    @SneakyThrows
    private double geodesicDistance(Geometry thisGeometry, Geometry thatGeometry, String coordinateReferenceSystem) {
        final var crs              = CRS.decode(coordinateReferenceSystem);
        final var distOp           = new DistanceOp(thisGeometry, thatGeometry);
        final var nearestPoints    = distOp.nearestPoints();
        final var nearestPointThis = nearestPoints[0];
        final var nearestPointThat = nearestPoints[1];
        final var gc               = new GeodeticCalculator(crs);
        gc.setStartingPosition(JTS.toDirectPosition(nearestPointThis, crs));
        gc.setDestinationPosition(JTS.toDirectPosition(nearestPointThat, crs));
        return gc.getOrthodromicDistance();
    }

    @Function(docs = """
            length(GEOMETRY): Returns the lenth of the geometry (perimeter in case of areal geometries).
            The returned value is in the units of the coordinates or of the projection (if projection applied).""")
    public Val length(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).getLength());
    }

    @Function(docs = """
            area(GEOMETRY): Returns the area of the geometry.
            The returned value is in the units (squared) of the coordinates or of the projection (if projection applied).""")
    public Val area(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).getArea());
    }

    /*
     * Geometry Collection Set Operations
     */

    @Function(docs = "subset(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2): Returns true, if GEOMETRYCOLLECTIONTHIS is a subset of GEOMETRYCOLLECTIONTHAT.")
    public Val subset(@Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val thisGeometryCollection,
            @Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val thatGeometryCollection) {
        return testGeometryBiPredicate(thisGeometryCollection, thatGeometryCollection,
                GeographicFunctionLibrary::subset);
    }

    private static Boolean subset(Geometry geometryCollectionThis, Geometry geometryCollectionThat) {
        if (geometryCollectionThis.getNumGeometries() > geometryCollectionThat.getNumGeometries()) {
            return false;
        }
        // Use BitSet as more efficient replacement for boolean array
        final var resultSet = new BitSet(geometryCollectionThis.getNumGeometries());
        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    resultSet.set(i);
                }
            }
        }
        return (resultSet.cardinality() == geometryCollectionThis.getNumGeometries());
    }

    @Function(docs = """
            atLeastOneMemberOf(GEOMETRYCOLLECTION1, GEOMETRYCOLLECTION2):
            Returns TRUE if at least one member of GEOMETRYCOLLECTIONTHIS is contained in GEOMETRYCOLLECTIONTHAT.""")
    public Val atLeastOneMemberOf(@Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val thisGeometryCollection,
            @JsonObject Val thatGeometryCollection) {
        return testGeometryBiPredicate(thisGeometryCollection, thatGeometryCollection,
                GeographicFunctionLibrary::atLeastOneMemberOf);
    }

    private static Boolean atLeastOneMemberOf(Geometry geometryCollectionThis, Geometry geometryCollectionThat) {
        for (int i = 0; i < geometryCollectionThis.getNumGeometries(); i++) {
            for (int j = 0; j < geometryCollectionThat.getNumGeometries(); j++) {
                if (geometryCollectionThis.getGeometryN(i).equals(geometryCollectionThat.getGeometryN(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Function(docs = "bagSize(GOEMETRYCOLLECTION): Returns the number of elements in the GEOMETRYCOLLECTION.")
    public Val bagSize(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).getNumGeometries());
    }

    @Function(docs = """
            oneAndOnly(GEOMETRYCOLLECTION): If GEOMETRYCOLLECTION only contains one element, this element will be returned.
            In all other cases an error will be thrown.""", schema = GeoJSONSchemata.GEOMETRIES)
    public Val oneAndOnly(@Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollection) {
        if (geoJsonToGeometry(jsonGeometryCollection) instanceof GeometryCollection geometryCollection
                && geometryCollection.getNumGeometries() == 1) {
            return geometryToGeoJSON(geometryCollection.getGeometryN(0));
        } else {
            return Val.error(INCORRECT_NUMER_OF_GEOEMTRIES_ERROR);
        }
    }

    @Function(docs = "geometryIsIn(GEOMETRY, GEOMETRYCOLLECTION): Tests if GEOMETRY is included in GEOMETRYCOLLECTION.")
    public Val geometryIsIn(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val jsonGeometry,
            @Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollection) {
        if (geoJsonToGeometry(jsonGeometryCollection) instanceof GeometryCollection geometryCollection) {
            final var geometry = geoJsonToGeometry(jsonGeometry);
            for (int i = 0; i < geometryCollection.getNumGeometries(); i++) {
                if (geometry.equals(geometryCollection.getGeometryN(i))) {
                    return Val.TRUE;
                }
            }
            return Val.FALSE;
        }
        return Val.error(NOT_A_GEOMETRY_COLLECTION_ERROR);
    }

    @Function(docs = "geometryBag(GEOMETRY,...): Takes any number of GEOMETRY and returns a GEOMETRYCOLLECTION containing all of them.", schema = GeoJSONSchemata.GEOMETRY_COLLECTION)
    public Val geometryBag(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val... geometryValues) {
        final var geometries = new Geometry[geometryValues.length];
        for (int i = 0; i < geometryValues.length; i++) {
            geometries[i] = geoJsonToGeometry(geometryValues[i]);
        }
        return geometryToGeoJSON(GEOMETRY_FACTORY.createGeometryCollection(geometries));
    }

    @Function(docs = """
            flattenGometryBag(RESOURCE_ARRAY): Takes multiple Geometries from RESOURCE_ARRAY and turns them into a GeometryCollection
            (e.g. geofences from a third party system).""", schema = GeoJSONSchemata.GEOMETRY_COLLECTION)
    public Val flattenGeometryBag(@Array Val arrayOfGeometries) {
        final var nodes = arrayOfGeometries.getArrayNode();
        final var vals  = new Val[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            vals[i] = Val.of(nodes.get(i).deepCopy());
        }
        return geometryBag(vals);
    }

    /*
     * Unit Conversions
     */

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from MILES to [m].")
    public Val milesToMeter(@Number Val jsonValue) {
        return Val.of(milesToMeter(jsonValue.get().asDouble()));
    }

    private double milesToMeter(@Number double value) {
        return value * DistanceUtils.MILES_TO_KM * 1000.0D;
    }

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from YARDS to [m].")
    public Val yardToMeter(@Number Val jsonValue) {
        return Val.of(yardToMeter(jsonValue.get().asDouble()));
    }

    private double yardToMeter(double yards) {
        return milesToMeter(yards / 1760.0D);
    }

    @Function(docs = "toMeter(VALUE, UNIT): Converts the given VALUE from DEGREES to [m].")
    public Val degreeToMeter(@Number Val jsonValue) {
        return Val.of(jsonValue.get().asDouble() * DistanceUtils.DEG_TO_KM * 1000);
    }

    /*
     * Geographic Data Converters
     */

    @Function(docs = "converts KML to GeoJSON", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val kmlToGeoJSON(@Text Val kml) {
        return parseKmlToGeoJSON(kml);
    }

    private static Val parseKmlToGeoJSON(Val kml) {
        Object parsed;
        try {
            parsed = KML_READER.parse(new StringReader(kml.getText()));
        } catch (Exception e) {
            return Val.error(FAILED_TO_PARSE_KML_ERROR);
        }
        final var geometries = collect(parsed);
        if (geometries.isEmpty()) {
            return Val.error(NO_GEOMETRIES_IN_KML_ERROR);
        } else if (geometries.size() == 1) {
            return geometryToGeoJSON(geometries.get(0));
        }
        var geometryCollection = WGS84_FACTORY.createGeometryCollection(geometries.toArray(new Geometry[0]));
        return geometryToGeoJSON(geometryCollection);
    }

    @SneakyThrows
    private static List<Geometry> collect(Object o) {
        if (o instanceof Feature feature) {
            return collectGeometries(feature);
        } else if (o instanceof FeatureCollection<?, ?> featureCollection) {
            return collectGeometries(featureCollection);
        } else if (o instanceof List<?> featureList) {
            return collectGeometries(featureList);
        } else if (o instanceof Geometry geometry) {
            geometry.setUserData(CRS.decode(WGS84));
            return List.of(geometry);
        }
        return List.of();
    }

    private static List<Geometry> collectGeometries(List<?> featureList) {
        final var geometries = new ArrayList<Geometry>();
        for (var feature : featureList) {
            geometries.addAll(collect(feature));
        }
        return geometries;
    }

    private static List<Geometry> collectGeometries(FeatureCollection<?, ?> featureCollection) {
        final var geometries = new ArrayList<Geometry>();
        try (var features = featureCollection.features()) {
            while (features.hasNext()) {
                geometries.addAll(collect(features.next()));
            }
        }
        return geometries;
    }

    private static List<Geometry> collectGeometries(Feature feature) {
        final var geometries = new ArrayList<Geometry>();
        for (Property property : feature.getProperties()) {
            geometries.addAll(collect(property.getValue()));
        }
        return geometries;
    }

    @Function(docs = "converts WKT to GeoJSON", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public static Val wktToGeoJSON(@Text Val wkt) {
        try {
            return geometryToGeoJSON(WKT_READER.read(wkt.getText()));
        } catch (ParseException e) {
            return Val.error(INVALID_WKT_ERROR);
        }
    }

    @Function(docs = "converts GML3 to GeoJSON", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public static Val gml3ToGeoJSON(@Text Val gml) {
        return gmlToGeoJSON(gml, GML3_READER);
    }

    @Function(docs = "converts GML2 to GeoJSON", schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public static Val gml2ToGeoJSON(@Text Val gml) {
        return gmlToGeoJSON(gml, GML2_READER);
    }

    private static Val gmlToGeoJSON(@Text Val gml, Parser parser) {
        Object parsed;
        try {
            parsed = parser.parse(new StringReader(gml.getText()));
        } catch (Exception e) {
            return Val.error(FAILED_TO_PARSE_GML_ERROR);
        }
        final var geometries = collect(parsed);
        if (geometries.isEmpty()) {
            return Val.error(NO_GEOMETRIES_IN_GML_ERROR);
        } else if (geometries.size() == 1) {
            return geometryToGeoJSON(geometries.get(0));
        }
        var geometryCollection = WGS84_FACTORY.createGeometryCollection(geometries.toArray(new Geometry[0]));
        return geometryToGeoJSON(geometryCollection);
    }

    /*
     * Convert Val to Geometry and back
     */

    @SneakyThrows
    static Geometry geoJsonToGeometry(Val geoJson) {
        return GEOJSON_READER.read(geoJson.toString());
    }

    Val geometryToGeoJSON(Geometry geo) {
        try {
            return Val.ofJson(GEOJSON_WRITER.write(geo));
        } catch (JsonProcessingException e) {
            return Val.error("Error converting Geometry to GeoJSON: " + e.getMessage());
        }
    }

}
