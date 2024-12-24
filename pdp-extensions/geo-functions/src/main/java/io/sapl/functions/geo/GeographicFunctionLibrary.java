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
            # Geographic Function Library

            The Geographic Function Library provides a rich set of geospatial functions for manipulating, analyzing, and converting geographic data. It is designed to work with GeoJSON as the primary format for representing geographic features.

            ## Overview
            This library allows users to perform:

            1. **Geometric Comparisons:** Evaluate spatial relationships between geometries, such as equality, disjointedness, adjacency, and intersection.
            2. **Geometric Operations:** Transform geometries through buffering, convex hull computation, unions, intersections, and differences.
            3. **Measurement Calculations:** Measure distances, lengths, areas, and geodesic distances between geometries.
            4. **Geometry Validation:** Check validity, simplicity, and closure of geometries.
            5. **Collection Operations:** Combine, subset, and test membership of geometries within collections.
            6. **Unit Conversions:** Convert measurements between units such as miles, yards, and degrees into meters.
            7. **Format Conversions:** Parse and convert geographic data between KML, GML, WKT, and GeoJSON formats.

            ## GeoJSON Format
            GeoJSON is a widely used format for encoding geographic data structures. It supports:
            - **Point:** Represents a single location (e.g., [longitude, latitude]).
            - **LineString:** Represents a sequence of points forming a line.
            - **Polygon:** Represents an area bounded by linear rings.
            - **MultiPoint, MultiLineString, MultiPolygon:** Collections of points, lines, or polygons.
            - **GeometryCollection:** Groups multiple geometries into a single structure.

            GeoJSON also includes properties for defining coordinate reference systems, though it defaults to WGS84 (EPSG:4326).

            ## Use Cases
            The library is suitable for:
            - Spatial analysis and validation.
            - Geographic data transformations and conversions.
            - Calculating distances and areas for geospatial features.
            - Validating and simplifying complex geometries.
            - Building complex policies for geospatial access control.

            ## Notes
            - The library assumes all geometries are in GeoJSON format.
            - All functions are schema-validated for correctness.
            - Methods operate seamlessly with input data using JSON processing.

            For more details, refer to individual function documentation.
            """;

    static final String FAILED_TO_PARSE_GML_ERROR            = "Failed to parse GML.";
    static final String FAILED_TO_PARSE_KML_ERROR            = "Failed to parse KML.";
    static final String GEOMETRY_TO_GEO_JSON_ERROR_S         = "Error converting Geometry to GeoJSON: %s";
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
            ```equalsExact(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries are exactly equal in terms of their structure and vertex values.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries have identical structures and coordinate values in the same order.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*
            policy "example"
            permit
            where
                var point1 = { "type": "Point", "coordinates": [10.0, 20.0] };
                var point2 = { "type": "Point", "coordinates": [10.0, 20.0] };
                equalsExact(point1, point2) == true;
            ```

            **Notes:**

            - Only exact matches are considered equal; differences in precision or coordinate order will result in `false`.
            - Suitable for testing identical geometries in scenarios requiring strict equality.
            """)
    public Val equalsExact(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::equalsExact);
    }

    @Function(docs = """
            ```disjoint(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries are disjoint, meaning they do not intersect.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries do not share any points.
            - Returns `false` if they intersect at any point.

            **Example:**

            ```
            import geo.*
            policy "example"
            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var point = { "type": "Point", "coordinates": [20.0, 20.0] };
                disjoint(polygon, point) == true;
            ```

            **Notes:**

            - Disjoint geometries have no spatial overlap.
            - Use this function to confirm spatial separation between geometries.
            """)
    public Val disjoint(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::disjoint);
    }

    @Function(docs = """
            ```touches(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries touch at their boundaries but do not overlap.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries share at least one boundary point but no interior points.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*
            policy "example"
            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[10,0], [10,10], [20,10], [20,0], [10,0]]] };
                touches(polygon1, polygon2) == true;
            ```

            **Notes:**

            - Use this function to determine adjacency without overlap.
            """)
    public Val touches(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::touches);
    }

    @Function(docs = """
            ```crosses(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries cross each other, meaning they intersect and share interior points without fully containing one another.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries intersect and cross each other at one or more points but do not contain one another.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var line1 = { "type": "LineString", "coordinates": [[0,0], [10,10]] };
                var line2 = { "type": "LineString", "coordinates": [[0,10], [10,0]] };
                crosses(line1, line2) == true;
            ```

            **Notes:**
            - Suitable for checking intersections between lines or other geometries that share some interior points.
            - Does not apply if one geometry fully contains the other.
            """)
    public Val crosses(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::crosses);
    }

    @Function(docs = """
            ```within(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether one geometry is completely within another geometry.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if every point of `thisGeometry` is inside `thatGeometry`.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point = { "type": "Point", "coordinates": [5, 5] };
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                within(point, polygon) == true;
            ```

            **Notes:**

            - Useful for containment checks where the geometry must be fully enclosed.
            """)
    public Val within(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> (that instanceof GeometryCollection) ? thiz.within(that.union()) : thiz.within(that));
    }

    @Function(docs = """
            ```contains(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether one geometry completely contains another geometry.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if `thisGeometry` fully contains `thatGeometry`.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var point = { "type": "Point", "coordinates": [5, 5] };
                contains(polygon, point) == true;
            ```

            **Notes:**

            - Suitable for verifying if a geometry encompasses another geometry entirely.
            """)
    public Val contains(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, (thiz,
                that) -> (thiz instanceof GeometryCollection) ? thiz.union().contains(that) : thiz.contains(that));
    }

    @Function(docs = """
            ```overlaps(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries overlap, meaning they share some but not all interior points.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries overlap and share some but not all points.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                overlaps(polygon1, polygon2) == true;
            ```

            **Notes:**

            - Use this function to check partial overlap without full containment.
            """)
    public Val overlaps(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::overlaps);
    }

    @Function(docs = """
            ```intersects(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```:
            Tests whether two geometries intersect, meaning they share at least one point.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometries intersect at any point.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var line1 = { "type": "LineString", "coordinates": [[0,0], [10,10]] };
                var line2 = { "type": "LineString", "coordinates": [[0,10], [10,0]] };
                intersects(line1, line2) == true;
            ```

            **Notes:**

            - Use this function to verify if geometries have any spatial overlap.
            """)
    public Val intersects(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, Geometry::intersects);
    }

    @Function(docs = """
            ```isWithinDistance(GEOMETRY thisGeometry, GEOMETRY thatGeometry, DOUBLE distance)```:
            Tests whether the distance between two geometries is within a specified value.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.
            - `distance`: A numeric value specifying the distance threshold.

            **Output:**

            - Returns `true` if the distance between the geometries is less than or equal to `distance`.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [3, 4] };
                isWithinDistance(point1, point2, 5.0) == true;
            ```

            **Notes:**

            - Use this function for proximity checks between geometries.
            """)
    public Val isWithinDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry, @Number Val distance) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> thiz.isWithinDistance(that, distance.get().asDouble()));
    }

    @Function(docs = """
            ```isWithinGeodesicDistance(GEOMETRY thisGeometry, GEOMETRY thatGeometry, DOUBLE distance)```:
            Tests whether two geometries are within a specified geodesic (earth surface) distance.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.
            - `distance`: A numeric value specifying the geodesic distance threshold (meters).

            **Output:**

            - Returns `true` if the geometries are within the specified geodesic distance.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                isWithinGeoDistance(point1, point2, 150) == true;
            ```

            **Notes:**

            - Suitable for geodesic distance checks, especially for large-scale geographic data.
            """)
    public Val isWithinGeodesicDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
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

    @Function(docs = """
            ```isSimple(GEOMETRY geometry)```:
            Tests whether a geometry is simple, meaning it has no self-intersections or anomalies.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometry is simple.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var line = { "type": "LineString", "coordinates": [[0,0], [1,1], [1,0], [0,0]] };
                isSimple(line) == false;
            ```

            **Notes:**

            - Use this function to validate geometry simplicity, especially for topological checks.
            """)
    public Val isSimple(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return Val.of(geoJsonToGeometry(geometry).isSimple());
    }

    @Function(docs = """
            ```isValid(GEOMETRY geometry)```:
            Tests whether a geometry is valid based on topological rules.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**
            - Returns `true` if the geometry is valid.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                isValid(polygon) == true;
            ```

            **Notes:**

            - A valid geometry adheres to topological constraints such as no self-intersections.
            """)
    public Val isValid(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).isValid());
    }

    @Function(docs = """
            ```isClosed(GEOMETRY geometry)```:
            Tests whether a geometry like a LineString is closed, meaning it starts and ends at the same point.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns `true` if the geometry is closed.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var ring = { "type": "LineString", "coordinates": [[0,0], [0,10], [10,10], [10,0], [0,0]] };
                isClosed(ring) == true;
            ```

            **Notes:**

            - Only applicable to LineStrings and other linear geometries.
            - Use to ensure rings or loops are adequately closed.
            """)
    public Val isClosed(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val geometry) {
        final var jtsGeometry = geoJsonToGeometry(geometry);
        if (Geometry.TYPENAME_POINT.equals(jtsGeometry.getGeometryType())
                || Geometry.TYPENAME_MULTIPOINT.equals(jtsGeometry.getGeometryType())) {
            return Val.TRUE;
        }
        switch (jtsGeometry.getGeometryType()) {
        case Geometry.TYPENAME_LINESTRING:
            return Val.of(((LineString) jtsGeometry).isClosed());
        case Geometry.TYPENAME_MULTILINESTRING:
            return Val.of(((MultiLineString) jtsGeometry).isClosed());
        default:
            return Val.error(String.format(IS_CLOSED_NOT_APPLICABLE_FOR_S_ERROR, jtsGeometry.getGeometryType()));
        }
    }

    /*
     * Geometry Operations
     */

    @Function(docs = """
            ```buffer(GEOMETRY geometry, NUMBER bufferWidth)```: Adds a buffer area of BUFFER_WIDTH around GEOMETRY and returns
            the new geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.
            - `bufferWidth`: A numeric value specifying the width of the buffer (same units as coordinates).

            **Output:**

            - Returns a new geometry representing the buffered area.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point = { "type": "Point", "coordinates": [0, 0] };
                buffer(point, 10.0) == { "type": "Polygon", "coordinates": [[[10,0], [0,10], [-10,0], [0,-10], [10,0]]] };
            ```

            **Notes:**

            - Useful for creating buffer zones around points, lines, or polygons.
            """, schema = GeoJSONSchemata.POLYGON)
    public Val buffer(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry, @Number Val bufferWidth) {
        return applyUnaryTransformation(geometry, g -> g.buffer(bufferWidth.get().asDouble()));
    }

    @Function(docs = """
            ```boundary(GEOMETRY geometry)```: Returns the boundary of a geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the boundary.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                boundary(polygon) == { "type": "LineString", "coordinates": [[0,0], [0,10], [10,10], [10,0], [0,0]] };
            ```

            **Notes:**

            - Returns the outer boundary for polygonal geometries.
            - Returns line segments or points depending on input geometry type.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val boundary(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::getBoundary);
    }

    @Function(docs = """
            ```centroid(GEOMETRY geometry)```: Returns the geometric center (centroid) of the geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a Point geometry representing the centroid.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                centroid(polygon) == { "type": "Point", "coordinates": [5, 5] };
            ```

            **Notes:**

            - The centroid is the center of mass for the geometry.
            - For multi-part geometries, the result considers all components.
            """, schema = GeoJSONSchemata.POINT)
    public Val centroid(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::getCentroid);
    }

    @Function(docs = """
            ```convexHull(GEOMETRY geometry)```: Returns the convex hull of the geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a Polygon geometry representing the convex hull.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var points = { "type": "MultiPoint", "coordinates": [[0,0], [0,10], [10,10], [10,0]] };
                convexHull(points) == { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
            ```

            **Notes:**

            - Computes the smallest convex polygon containing all points.
            - Useful for simplifying geometries or bounding datasets.
            """, schema = GeoJSONSchemata.CONVEX_HULL)
    public Val convexHull(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return applyUnaryTransformation(geometry, Geometry::convexHull);
    }

    private Val applyUnaryTransformation(Val thisGeometry, UnaryOperator<Geometry> transformation) {
        return geometryToGeoJSON(transformation.apply(geoJsonToGeometry(thisGeometry)));
    }

    @Function(docs = """
            ```union(GEOMETRY... geometries)```: Returns the union of an arbritrary number of geometries.

            **Inputs:**

            - `geometries`: A variable number of arguments of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry representing the union of inputs.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                union(polygon1, polygon2) == { "type": "Polygon", "coordinates": [[[0,0], [0,10], [5,10], [5,15], [15,15], [15,5], [10,5], [10,0], [0,0]]] };
            ```

            **Notes:**

            - Merges overlapping areas of geometries.
            - Accepts geometry collections as input.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val union(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val... geometries) {
        if (geometries.length == 0) {
            return geometryToGeoJSON(WGS84_FACTORY.createEmpty(-1));
        }

        var geomUnion = geoJsonToGeometry(geometries[0]);
        for (int i = 1; i < geometries.length; i++) {
            geomUnion = geomUnion.union(geoJsonToGeometry(geometries[i]));
        }
        return geometryToGeoJSON(geomUnion);
    }

    @Function(docs = """
            ```intersection(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Returns the intersection of the geometries.

            **Inputs:**

            - `geometryThis`: A geometry object in GeoJSON format.
            - `geometryThat`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the common area of the inputs.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                intersection(polygon1, polygon2) == { "type": "Polygon", "coordinates": [[[5,5], [5,10], [10,10], [10,5], [5,5]]] };
            ```

            **Notes:**

            - Computes the overlap between geometries.
            - Useful for finding shared areas.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public Val intersection(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::intersection);
    }

    @Function(docs = """
            ```difference(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Computes the difference between two geometries.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the part of `thisGeometry` that does not intersect with `thatGeometry`.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                difference(polygon1, polygon2) == { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
            ```

            **Notes:**

            - Computes the geometric difference by subtracting overlapping areas.
            - Useful for isolating non-intersecting regions.
            """, schema = GeoJSONSchemata.GEOMETRIES)
    public Val difference(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::difference);
    }

    @Function(docs = """
            ```symDifference(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Computes the symmetric difference between two geometries.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the parts of both geometries that do not intersect.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                symDifference(polygon1, polygon2) == { "type": "MultiPolygon", "coordinates": [[[[0,0], [0,10], [10,10], [10,0], [0,0]]], [[[5,5], [5,15], [15,15], [15,5], [5,5]]]] };
            ```

            **Notes:**

            - Computes areas that are exclusive to each input geometry.
            - Useful for highlighting non-overlapping regions.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
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
            ```distance(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Returns the shortest planar distance between
            two geometries.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns a numeric value representing the distance.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [3, 4] };
                distance(point1, point2) == 5.0;
            ```

            **Notes:**

            - The distance is calculated based on the coordinate units.
            - Suitable for planar (non-geodesic) distance calculations.
            """)
    public Val distance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return Val.of(geoJsonToGeometry(thisGeometry).distance(geoJsonToGeometry(thatGeometry)));
    }

    @Function(docs = """
            ```geodesicDistance(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Returns the shortest geodesic distance between two
            geometries in meters. This method uses WGS84 as its reference coordinate system.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**
            - Returns a numeric value representing the geodesic distance in meters.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                geoDistance(point1, point2) <= 157.25;
            ```

            **Notes:**

            - Uses geodesic calculations suitable for geographic coordinates.
            - Ideal for large-scale or global distance computations.
            """)
    public Val geodesicDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thisGeometry,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val thatGeometry) {
        return Val.of(geodesicDistanceOfVals(thisGeometry, thatGeometry));
    }

    @Function(docs = """
            ```geodesicDistance(GEOMETRY thisGeometry, GEOMETRY thatGeometry, TEXT coordinateReferenceSystem)```:
            Returns the shortest geodesic distance between two geometries in meters.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.
            - `coordinateReferenceSystem`: A coordinate system, such as WGS84, i.e. `"EPSG:4326"`.
              Also see: (https://epsg.io).

            **Output:**

            - Returns a numeric value representing the geodesic distance in meters.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                geoDistance(point1, point2, "EPSG:4326") <= 157.25;
            ```

            **Notes:**

            - Uses geodesic calculations suitable for geographic coordinates.
            - Ideal for large-scale or global distance computations.
            """)
    public Val geoDistance(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThis,
            @Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val jsonGeometryThat,
            Val coordinateReferenceSystem) {
        return Val.of(geodesicDistance(jsonGeometryThis, jsonGeometryThat, coordinateReferenceSystem.getText()));
    }

    private double geodesicDistanceOfVals(Val thisGeometry, Val thatGeometry) {
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
            ```length(GEOMETRY geometry)```: Returns the length of a geometry, including perimeter for polygons.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a numeric value representing the length.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var line = { "type": "LineString", "coordinates": [[0,0], [0,10], [10,10], [10,0], [0,0]] };
                length(line) == 40.0;
            ```

            **Notes:**

            - Measures the total length or perimeter based on input geometry.
            """)
    public Val length(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return Val.of(geoJsonToGeometry(geometry).getLength());
    }

    @Function(docs = """
            ```area(GEOMETRY geometry)```: Returns the area of a geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a numeric value representing the area.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                area(polygon) == 100.0;
            ```

            **Notes:**

            - Computes the area for polygonal geometries.
            - Units depend on coordinate system or projection used.
            """)
    public Val area(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val geometry) {
        return Val.of(geoJsonToGeometry(geometry).getArea());
    }

    /*
     * Geometry Collection Set Operations
     */

    @Function(docs = """
            ```subset(GEOMETRYCOLLECTION thisGeometryCollection, GEOMETRYCOLLECTION thatGeometryCollection)```:
            Checks if one geometry collection is a subset of another.

            **Inputs:**

            - `thisGeometryCollection`: A geometry collection in GeoJSON format.
            - `thatGeometryCollection`: Another geometry collection in GeoJSON format.

            **Output:**

            - Returns `true` if `GEOMETRYCOLLECTION1` is entirely contained within `GEOMETRYCOLLECTION2`.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var collection1 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                var collection2 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
                subset(collection1, collection2) == true;
            ```

            **Notes:**

            - Suitable for verifying hierarchical relationships between geometry collections.
            """)
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
            ```atLeastOneMemberOf(GEOMETRYCOLLECTION thisGeometryCollection, GEOMETRYCOLLECTION thatGeometryCollection)```: Checks if at least one geometry in the first collection is present in the second collection.

            **Inputs:**

            - `thisGeometryCollection`: A geometry collection in GeoJSON format.
            - `thatGeometryCollection`: Another geometry collection in GeoJSON format.

            **Output:**

            - Returns `true` if at least one geometry from `thisGeometryCollection` exists in `thatGeometryCollection`.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var collection1 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                var collection2 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [1, 1] },
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                atLeastOneMemberOf(collection1, collection2) == true;
            ```

            **Notes:**

            - Checks for partial membership between geometry collections.
            """)
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

    @Function(docs = """
            ```bagSize(GEOMETRYCOLLECTION geometryCollection)```: Returns the number of geometries in a collection.

            **Inputs:**

            - `geometryCollection`: A geometry collection in GeoJSON format.

            **Output:**

            - Returns an integer representing the number of geometries in the collection.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
                bagSize(collection) == 2;
            ```

            **Notes:**

            - Useful for evaluating collection size.
            """)
    public Val bagSize(@Schema(GeoJSONSchemata.GEOMETRIES) @JsonObject Val jsonGeometry) {
        return Val.of(geoJsonToGeometry(jsonGeometry).getNumGeometries());
    }

    @Function(docs = """
            ```oneAndOnly(GEOMETRYCOLLECTION geometryCollection)```: Returns the only geometry in a collection if it contains exactly one geometry.

            **Inputs:**

            - `geometryCollection`: A geometry collection in GeoJSON format.

            **Output:**

            - Returns the single geometry if the collection contains exactly one geometry.
            - Returns an error otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                oneAndOnly(collection) == { "type": "Point", "coordinates": [0, 0] };
            ```

            **Notes:**

            - Ensures uniqueness of geometry within a collection.
            - Returns an error if more than one geometry is present.
            """, schema = GeoJSONSchemata.GEOMETRIES)
    public Val oneAndOnly(@Schema(GeoJSONSchemata.GEOMETRY_COLLECTION) @JsonObject Val jsonGeometryCollection) {
        if (geoJsonToGeometry(jsonGeometryCollection) instanceof GeometryCollection geometryCollection
                && geometryCollection.getNumGeometries() == 1) {
            return geometryToGeoJSON(geometryCollection.getGeometryN(0));
        } else {
            return Val.error(INCORRECT_NUMER_OF_GEOEMTRIES_ERROR);
        }
    }

    @Function(docs = """
            ```geometryIsIn(GEOMETRY geometry, GEOMETRYCOLLECTION geometryCollection)```: Checks if a geometry is contained within a collection.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.
            - `geometryCollection`: A geometry collection in GeoJSON format.

            **Output:**

            - Returns `true` if the geometry is in the collection.
            - Returns `false` otherwise.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point = { "type": "Point", "coordinates": [0, 0] };
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                geometryIsIn(point, collection) == true;
            ```

            **Notes:**

            - Checks for the existence of a specific geometry within a collection.
            """)
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

    @Function(docs = """
            ```geometryBag(GEOMETRY... geometries)```: Combines multiple geometries into a single geometry collection.

            **Inputs:**

            - `geometries`: A variable number of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry collection containing all input geometries.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [1, 1] };
                geometryBag(point1, point2) == { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
            ```

            **Notes:**

            - Useful for grouping geometries into collections.
            """, schema = GeoJSONSchemata.GEOMETRY_COLLECTION)
    public Val geometryBag(@Schema(GeoJSONSchemata.JSON_SCHEME_COMPLETE) @JsonObject Val... geometryValues) {
        final var geometries = new Geometry[geometryValues.length];
        for (int i = 0; i < geometryValues.length; i++) {
            geometries[i] = geoJsonToGeometry(geometryValues[i]);
        }
        return geometryToGeoJSON(GEOMETRY_FACTORY.createGeometryCollection(geometries));
    }

    @Function(docs = """
            ```flattenGeometryBag(ARRAY geometriesArray)```: Flattens an array of geometries into a single geometry collection.

            **Inputs:**

            - `geometriesArray`: An array of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry collection containing all geometries from the input array.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var geometries = [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ];
                flattenGeometryBag(geometries) == { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
            ```

            **Notes:**

            - Useful for combining geometries from arrays into collections.
            """, schema = GeoJSONSchemata.GEOMETRY_COLLECTION)
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

    @Function(docs = """
            ```milesToMeter(NUMBER value)```: Converts a distance in miles to meters.

            **Inputs:**

            - `value`: A numeric value representing distance in miles.

            **Output:**

            - Returns a numeric value representing the converted distance in meters.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                milesToMeter(1.0) == 1609.34;
            ```

            **Notes:**

            - Useful for converting mile-based measurements to meters for calculations.
            """)
    public Val milesToMeter(@Number Val value) {
        return Val.of(milesToMeter(value.get().asDouble()));
    }

    private double milesToMeter(@Number double value) {
        return value * DistanceUtils.MILES_TO_KM * 1000.0D;
    }

    @Function(docs = """
            ```yardToMeter(NUMBER value)```: Converts a distance in yards to meters.

            **Inputs:**

            - `value`: A numeric value representing distance in yards.

            **Output:**

            - Returns a numeric value representing the converted distance in meters.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                yardToMeter(1.0) == 0.9144;
            ```

            **Notes:**

            - Converts yard-based measurements to meters for compatibility in geospatial calculations.
            """)
    public Val yardToMeter(@Number Val value) {
        return Val.of(yardToMeter(value.get().asDouble()));
    }

    private double yardToMeter(double yards) {
        return milesToMeter(yards / 1760.0D);
    }

    @Function(docs = """
            ```degreeToMeter(NUMBER value)```: Converts a distance in degrees to meters.

            **Inputs:**

            - `value`: A numeric value representing distance in degrees.

            **Output:**

            - Returns a numeric value representing the converted distance in meters.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                degreeToMeter(1.0) == 111319.9;
            ```

            **Notes:**

            - Converts degree-based distances to meters, useful for geographic coordinate transformations.
            """)
    public Val degreeToMeter(@Number Val value) {
        return Val.of(value.get().asDouble() * DistanceUtils.DEG_TO_KM * 1000);
    }

    /*
     * Geographic Data Converters
     */

    @Function(docs = """
            ```kmlToGeoJSON(TEXT kml)```: Converts KML data to GeoJSON format.

            **Inputs:**

            - `kml`: A string containing KML data.

            **Output:**

            - Returns a GeoJSON object representing the converted KML data.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var kmlData = "<kml><Placemark><Point><coordinates>10,20</coordinates></Point></Placemark></kml>";
                kmlToGeoJSON(kmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Use this function to transform KML data into a GeoJSON format for compatibility.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
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

    @Function(docs = """
            ```wktToGeoJSON(TEXT wkt)```: Converts WKT data to GeoJSON format.

            **Inputs:**

            - `wkt`: A string containing Well-Known Text (WKT) geometry.

            **Output:**

            - Returns a GeoJSON object representing the converted WKT data.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var wktData = "POINT(10 20)";
                wktToGeoJSON(wktData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Useful for converting WKT geometries into GeoJSON for processing.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public static Val wktToGeoJSON(@Text Val wkt) {
        try {
            return geometryToGeoJSON(WKT_READER.read(wkt.getText()));
        } catch (ParseException e) {
            return Val.error(INVALID_WKT_ERROR);
        }
    }

    @Function(docs = """
            ```gml3ToGeoJSON(TEXT gml)```: Converts GML 3 data to GeoJSON format.

            **Inputs:**

            - `gml`: A string containing GML 3 data.

            **Output:**
            - Returns a GeoJSON object representing the converted GML 3 data.

            **Example:**
            ```
            import geo.*

            policy "example"

            permit
            where
                var gmlData = "<gml:Point><gml:coordinates>10,20</gml:coordinates></gml:Point>";
                gml3ToGeoJSON(gmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Designed for compatibility with GML 3 formatted data.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
    public static Val gml3ToGeoJSON(@Text Val gml) {
        return gmlToGeoJSON(gml, GML3_READER);
    }

    @Function(docs = """
            ```gml2ToGeoJSON(TEXT gml)```: Converts GML 2 data to GeoJSON format.

            **Inputs:**

            - `gml`: A string containing GML 2 data.

            **Output:**

            - Returns a GeoJSON object representing the converted GML 2 data.

            **Example:**

            ```
            import geo.*

            policy "example"

            permit
            where
                var gmlData = "<gml:Point><gml:coordinates>10,20</gml:coordinates></gml:Point>";
                gml2ToGeoJSON(gmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Designed for compatibility with GML 2 formatted data.
            """, schema = GeoJSONSchemata.JSON_SCHEME_COMPLETE)
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
            return Val.error(String.format(GEOMETRY_TO_GEO_JSON_ERROR_S, e.getMessage()));
        }
    }

}
