/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.SneakyThrows;
import lombok.val;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.xsd.Configuration;
import org.geotools.xsd.Parser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.relateng.RelateNG;
import org.locationtech.jts.operation.relateng.RelatePredicate;
import org.locationtech.spatial4j.distance.DistanceUtils;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

@FunctionLibrary(name = "geo", description = GeographicFunctionLibrary.DESCRIPTION, libraryDocumentation = GeographicFunctionLibrary.DOCUMENTATION)
public class GeographicFunctionLibrary {

    // GeoTools logs every intentionally blocked external reference at WARNING with
    // a full stack trace, so silence it to prevent log flooding in production. The
    // JUL LogManager references loggers only weakly, so the configured logger is
    // held here. Otherwise a garbage collection drops it along with its level.
    private static final Logger GEOTOOLS_XSD_LOGGER = Logger.getLogger("org.geotools.xsd");

    static {
        GEOTOOLS_XSD_LOGGER.setLevel(Level.SEVERE);
    }

    static final String DESCRIPTION   = "A function library to manipulate, inspect, and convert geographic data.";
    static final String DOCUMENTATION = """
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
               KML and GML accept spaces, tabs and line breaks between coordinate tuples. In a GML `coordinates` element,
               the `ts` attribute can declare a different separator, including a nonbreaking space.

            ## GeoJSON Format
            GeoJSON is a widely used format for encoding geographic data structures. It supports:
            - **Point:** Represents a single location (e.g., [longitude, latitude]).
            - **LineString:** Represents a sequence of points forming a line.
            - **Polygon:** Represents an area bounded by linear rings.
            - **MultiPoint, MultiLineString, MultiPolygon:** Collections of points, lines, or polygons.
            - **GeometryCollection:** Groups multiple geometries into a single structure.

            Inputs must be geometries, not Feature or FeatureCollection objects. Each position has at least two numbers.
            Results are RFC 7946 geometries without a `crs` member. Empty coordinate arrays represent empty geometries.
            Distances require nonempty geometries. Geodesic distances use longitude and latitude in degrees.

            ## Numerical Tolerances
            PDP implementations use different geometry libraries and may approximate computed results differently.
            Planar predicates use the supplied coordinate units and the floating-point topology of JTS.
            Use ordinary coordinate ranges for the chosen reference system. Extreme scales and nearly degenerate shapes
            can change a predicate result. For geofencing, verify who supplies the location and the perimeter used by the policy.
            - `buffer`: Up to 1 percent of the absolute buffer width in coordinate units, because circular arcs are approximated by line segments.
            - `geoDistance`, `geodesicDistance`, `isWithinGeodesicDistance`: Up to 1 millimeter in the computed distance, because ellipsoidal geodesics use numerical approximations. Threshold decisions within this distance may differ.
            - `union`, `intersection`, `difference`, `symDifference`: Up to 0.000001 coordinate units in the symmetric discrete Hausdorff distance between result boundaries. Overlay algorithms use different precision grids and intersection calculations. This tolerance applies to ordinary finite coordinate ranges and does not guarantee accuracy for arbitrary coordinate magnitudes or degenerate inputs.

            ## Use Cases
            The library is suitable for:
            - Spatial analysis and validation.
            - Geographic data transformations and conversions.
            - Calculating distances and areas for geospatial features.
            - Validating and simplifying complex geometries.
            - Building complex policies for geospatial access control.

            ## Notes
            - The library assumes all geometries are in GeoJSON format.
            - Methods operate seamlessly with input data using JSON processing.

            ## Resource Limits
            Geographic inputs may come from untrusted sources such as attribute finders or the
            authorization subscription, so every parse boundary (GeoJSON, WKT, GML, KML) is bounded
            and fails closed to an error value when a limit is exceeded:
            - Input size is capped at 4 MB.
            - A single geometry may contain at most 100,000 vertices.
            - A geometry collection may contain at most 50,000 members.
            - The collection comparisons `subset` and `atLeastOneMemberOf` are quadratic, so they are
              additionally capped at 1,000,000 pairwise checks.
            - Distance searches allow up to 1,000,000 candidate comparisons. Planar `isWithinDistance` returns
              false immediately when the bounding boxes are already farther apart than the threshold.

            These limits comfortably accommodate detailed real-world boundaries. They apply because
            this input may originate from the authorization subscription or from policy information
            points, which are not vetted to the same degree as the policies and variables shipped
            with the PDP configuration.

            KML and GML inputs are parsed with external entity and remote schema resolution disabled,
            so a document cannot make the PDP fetch an external entity or schema (no server-side
            request forgery). Only the schemas bundled locally with the engine are used.

            For more details, refer to individual function documentation.
            """;

    static final String ERROR_DISTANCE_COMPARISON_LIMIT      = "Geometry distance calculation exceeds the maximum of %d comparisons.";
    static final String ERROR_EMPTY_DISTANCE                 = "Distances require nonempty geometries.";
    static final String ERROR_FAILED_TO_PARSE_GML            = "Failed to parse GML.";
    static final String ERROR_FAILED_TO_PARSE_KML            = "Failed to parse KML.";
    static final String ERROR_GEOMETRY_INPUT_TOO_LARGE       = "Geographic input exceeds the maximum size of %d bytes.";
    static final String ERROR_GEOMETRY_TOO_MANY_MEMBERS      = "Geometry exceeds the maximum of %d members.";
    static final String ERROR_GEOMETRY_TOO_MANY_VERTICES     = "Geometry exceeds the maximum of %d vertices.";
    static final String ERROR_GEOMETRY_TO_GEO_JSON           = "Failed to convert geometry to GeoJSON.";
    static final String ERROR_GEOMETRY_WKT_NESTING_TOO_DEEP  = "WKT nesting exceeds the maximum of %d levels.";
    static final String ERROR_INCORRECT_NUMBER_OF_GEOMETRIES = "Input must be a GeometryCollection containing only one Geometry.";
    static final String ERROR_INVALID_GEOJSON                = "Invalid GeoJSON geometry.";
    static final String ERROR_INVALID_WKT                    = "Invalid WKT.";
    static final String ERROR_INVALID_WKT_LINE               = "Invalid WKT geometry. A LineString must have zero or at least two positions.";
    static final String ERROR_INVALID_WKT_RING_CLOSED        = "Invalid WKT geometry. A LinearRing must be closed.";
    static final String ERROR_INVALID_WKT_RING_SIZE          = "Invalid WKT geometry. A LinearRing must have zero or at least three positions.";
    static final String ERROR_INVALID_WKT_SHELL              = "Invalid WKT geometry. An empty polygon shell cannot have nonempty holes.";
    static final String ERROR_IS_CLOSED_NOT_APPLICABLE_FOR_S = "Operation isClosed is not applicable for the type %s.";
    static final String ERROR_NO_GEOMETRIES_IN_GML           = "No geometries in GML.";
    static final String ERROR_NO_GEOMETRIES_IN_KML           = "No geometries in KML.";
    static final String ERROR_NOT_A_GEOMETRY_COLLECTION      = "The second parameter of the geometryIsIn was not a geometry collection.";
    static final String ERROR_TOO_MANY_COMPARISONS           = "Geometry collection comparison exceeds the maximum of %d pairwise checks.";

    static final String ERROR_UNSUPPORTED_CRS = "The coordinate reference system '%s' is not supported. Geodesic distances take a geographic coordinate reference system such as EPSG:4326.";

    private static final String WGS84 = "EPSG:4326";

    private static final GeoJsonReader GEOJSON_READER = new GeoJsonReader();
    private static final GeoJsonWriter GEOJSON_WRITER = new GeoJsonWriter();
    static {
        GEOJSON_WRITER.setEncodeCRS(false);
    }

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    // GeoTools Parser is stateful and not thread-safe. A fresh one is built per
    // call
    // (see hardenedParser). gml2/gml3 GMLConfiguration share a class name, hence
    // the FQNs.
    private static final Configuration   GML2_CONFIG   = new org.geotools.gml2.GMLConfiguration();
    private static final Configuration   GML3_CONFIG   = new org.geotools.gml3.GMLConfiguration();
    private static final Configuration   KML_CONFIG    = new KMLConfiguration();
    private static final GeometryFactory WGS84_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    // Inputs may come from attribute finders or the subscription, not only trusted
    // policy literals, so every parse boundary caps input size and geometry
    // complexity.
    static final int  MAX_GEO_INPUT_BYTES      = 4 * 1024 * 1024;
    static final int  MAX_GEOMETRY_VERTICES    = 100_000;
    static final int  MAX_GEOMETRY_COUNT       = 50_000;
    static final long MAX_PAIRWISE_COMPARISONS = 1_000_000L;
    static final int  MAX_WKT_NESTING_DEPTH    = 128;

    /**
     * Resolves external entities and schema references against a fixed allowlist
     * of the XML Schema documents geotools bundles on the classpath for KML/GML
     * parsing, and resolves nothing else. This closes the XXE and external-entity
     * SSRF vector on caller-supplied documents: any entity or schema the document
     * declares is mapped to a bundled local resource if and only if it matches the
     * allowlist, otherwise resolution is rejected by throwing so nothing is ever
     * fetched. The resolver never returns
     * {@code null} for a non-null system identifier, because per the SAX
     * EntityResolver contract {@code null} tells the parser to fetch the URI
     * itself. An attacker-controlled URI such as
     * {@code http://169.254.169.254/x.xsd} or {@code file:///etc/passwd} is never
     * fetched regardless of its suffix, and resolution fails closed.
     * <p>
     * Geotools loads its own bundled schemas through its internal schema index
     * rather than through this resolver, so in normal operation the allowlist
     * branch is not exercised by legitimate documents. It exists to keep local
     * schema resolution available without ever permitting a remote or arbitrary
     * local fetch.
     * <p>
     * Implements {@link EntityResolver2} because geotools' parser consults the
     * four-argument resolution path; a plain {@code EntityResolver} is bypassed
     * for general external entities.
     */
    private static final class SchemaOnlyEntityResolver implements EntityResolver2 {

        private static final SchemaOnlyEntityResolver INSTANCE = new SchemaOnlyEntityResolver();

        private static final InputSource EMPTY_SOURCE = new InputSource(new StringReader(""));

        private static final String ERROR_EXTERNAL_REFERENCE_BLOCKED = "External XML reference blocked (geo functions perform no I/O): ";

        // Bundled, classpath-local schema resources geotools ships for KML/GML.
        // Resolution is restricted to exactly these. Nothing else is fetched.
        private static final List<String> ALLOWED_LOCAL_SCHEMA_RESOURCES = List.of("org/geotools/kml/v22/ogckml22.xsd",
                "org/geotools/kml/kml21.xsd", "org/geotools/gml3/gml.xsd", "org/geotools/gml2/feature.xsd");

        @Override
        public InputSource getExternalSubset(String name, String baseUri) {
            return EMPTY_SOURCE;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            return resolveEntity(null, publicId, null, systemId);
        }

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseUri, String systemId)
                throws SAXException {
            if (systemId == null) {
                return EMPTY_SOURCE;
            }
            val resolved = resolveSystemId(baseUri, systemId);
            // Fail closed: a non-bundled reference is rejected (throws), so geotools never
            // fetches it.
            return bundledLocalSchema(resolved)
                    .orElseThrow(() -> new SAXException(ERROR_EXTERNAL_REFERENCE_BLOCKED + systemId));
        }

        private static Optional<InputSource> bundledLocalSchema(String resolved) {
            val path = resolved.split("[?#]", 2)[0];
            return ALLOWED_LOCAL_SCHEMA_RESOURCES.stream().filter(path::endsWith)
                    .map(resource -> GeographicFunctionLibrary.class.getClassLoader().getResource(resource))
                    .filter(url -> url != null && path.equals(url.toString())).findFirst()
                    .map(url -> new InputSource(url.toString()));
        }

        private static String resolveSystemId(String baseUri, String systemId) {
            try {
                return baseUri == null ? URI.create(systemId).toString()
                        : URI.create(baseUri).resolve(systemId).toString();
            } catch (RuntimeException e) {
                return systemId;
            }
        }
    }

    static Parser hardenedParser(Configuration configuration) {
        val parser = new Parser(configuration);
        parser.setEntityResolver(SchemaOnlyEntityResolver.INSTANCE);
        return parser;
    }

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

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [10.0, 20.0] };
                var point2 = { "type": "Point", "coordinates": [10.0, 20.0] };
                geo.equalsExact(point1, point2) == true;
            ```

            **Notes:**

            - Only exact matches are considered equal. Differences in precision or coordinate order will result in `false`.
            - Suitable for testing identical geometries in scenarios requiring strict equality.
            """)
    public static Value equalsExact(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var point = { "type": "Point", "coordinates": [20.0, 20.0] };
                geo.disjoint(polygon, point) == true;
            ```

            **Notes:**

            - Disjoint geometries have no spatial overlap.
            - Use this function to confirm spatial separation between geometries.
            """)
    public static Value disjoint(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[10,0], [10,10], [20,10], [20,0], [10,0]]] };
                geo.touches(polygon1, polygon2) == true;
            ```

            **Notes:**

            - Use this function to determine adjacency without overlap.
            """)
    public static Value touches(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var line1 = { "type": "LineString", "coordinates": [[0,0], [10,10]] };
                var line2 = { "type": "LineString", "coordinates": [[0,10], [10,0]] };
                geo.crosses(line1, line2) == true;
            ```

            **Notes:**
            - Suitable for checking intersections between lines or other geometries that share some interior points.
            - Does not apply if one geometry fully contains the other.
            """)
    public static Value crosses(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (first, second) -> RelateNG.relate(first, second, RelatePredicate.crosses()));
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
            policy "example"
            permit
                var point = { "type": "Point", "coordinates": [5, 5] };
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                geo.within(point, polygon) == true;
            ```

            **Notes:**

            - Useful for containment checks where the geometry must be fully enclosed.
            """)
    public static Value within(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> thiz.within(that instanceof GeometryCollection ? that.union() : that));
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
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var point = { "type": "Point", "coordinates": [5, 5] };
                geo.contains(polygon, point) == true;
            ```

            **Notes:**

            - Suitable for verifying if a geometry encompasses another geometry entirely.
            """)
    public static Value contains(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                geo.overlaps(polygon1, polygon2) == true;
            ```

            **Notes:**

            - Use this function to check partial overlap without full containment.
            """)
    public static Value overlaps(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var line1 = { "type": "LineString", "coordinates": [[0,0], [10,10]] };
                var line2 = { "type": "LineString", "coordinates": [[0,10], [10,0]] };
                geo.intersects(line1, line2) == true;
            ```

            **Notes:**

            - Use this function to verify if geometries have any spatial overlap.
            """)
    public static Value intersects(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [3, 4] };
                geo.isWithinDistance(point1, point2, 5.0) == true;
            ```

            **Notes:**

            - Use this function for proximity checks between geometries.
            """)
    public static Value isWithinDistance(ObjectValue thisGeometry, ObjectValue thatGeometry, NumberValue distance) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry, (thiz, that) -> {
            requireNonempty(thiz, that);
            val threshold = distance.value().doubleValue();
            if (thiz.getEnvelopeInternal().distance(that.getEnvelopeInternal()) > threshold) {
                return false;
            }
            requireDistanceWork(thiz, that);
            return new DistanceOp(thiz, that, threshold).distance() <= threshold;
        });
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

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                geo.isWithinGeodesicDistance(point1, point2, 160) == true;
            ```

            **Notes:**

            - Suitable for geodesic distance checks, especially for large-scale geographic data.
            """)
    public static Value isWithinGeodesicDistance(ObjectValue thisGeometry, ObjectValue thatGeometry,
            NumberValue distance) {
        return testGeometryBiPredicate(thisGeometry, thatGeometry,
                (thiz, that) -> geodesicDistance(thiz, that) <= distance.value().doubleValue());
    }

    private static Value testGeometryBiPredicate(ObjectValue thisGeometry, ObjectValue thatGeometry,
            BiPredicate<Geometry, Geometry> predicate) {
        try {
            return Value.of(predicate.test(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry)));
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var line = { "type": "LineString", "coordinates": [[0,0], [1,1], [1,0], [0,0]] };
                geo.isSimple(line) == true;
            ```

            **Notes:**

            - Use this function to validate geometry simplicity, especially for topological checks.
            """)
    public static Value isSimple(ObjectValue geometry) {
        try {
            return Value.of(geoJsonToGeometry(geometry).isSimple());
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                geo.isValid(polygon) == true;
            ```

            **Notes:**

            - A valid geometry adheres to topological constraints such as no self-intersections.
            """)
    public static Value isValid(ObjectValue geometry) {
        try {
            return Value.of(geoJsonToGeometry(geometry).isValid());
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var ring = { "type": "LineString", "coordinates": [[0,0], [0,10], [10,10], [10,0], [0,0]] };
                geo.isClosed(ring) == true;
            ```

            **Notes:**

            - Only applicable to LineStrings and other linear geometries.
            - Use to ensure rings or loops are adequately closed.
            """)
    public static Value isClosed(ObjectValue geometry) {
        try {
            var jtsGeometry = geoJsonToGeometry(geometry);
            if (Geometry.TYPENAME_POINT.equals(jtsGeometry.getGeometryType())
                    || Geometry.TYPENAME_MULTIPOINT.equals(jtsGeometry.getGeometryType())) {
                return Value.TRUE;
            }
            return switch (jtsGeometry.getGeometryType()) {
            case Geometry.TYPENAME_LINESTRING      -> Value.of(((LineString) jtsGeometry).isClosed());
            case Geometry.TYPENAME_MULTILINESTRING -> Value.of(((MultiLineString) jtsGeometry).isClosed());
            default                                ->
                Value.error(ERROR_IS_CLOSED_NOT_APPLICABLE_FOR_S.formatted(jtsGeometry.getGeometryType()));
            };
        } catch (Exception e) {
            return geometryError(e);
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

            ```sapl
            policy "example"
            permit
                var point = { "type": "Point", "coordinates": [0, 0] };
                geo.area(geo.buffer(point, 10.0)) > 300;
            ```

            **Notes:**

            - A zero-width buffer extracts polygonal area. For a self-crossing ring it can retain only one lobe.
              Points and lines give an empty polygon.
            """)
    public static Value buffer(ObjectValue geometry, NumberValue bufferWidth) {
        return applyUnaryTransformation(geometry, g -> g.buffer(bufferWidth.value().doubleValue()));
    }

    @Function(docs = """
            ```boundary(GEOMETRY geometry)```: Returns the boundary of a geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the boundary. Boundary points keep their input heights.

            **Example:**

            ```sapl
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                geo.length(geo.boundary(polygon)) == 40;
            ```

            **Notes:**

            - Returns the outer boundary for polygonal geometries.
            - Returns line segments or points depending on input geometry type.
            """)
    public static Value boundary(ObjectValue geometry) {
        return applyUnaryTransformation(geometry, Geometry::getBoundary);
    }

    @Function(docs = """
            ```centroid(GEOMETRY geometry)```: Returns the geometric center (centroid) of the geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a Point geometry representing the centroid.

            **Example:**

            ```sapl
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                geo.centroid(polygon) == { "type": "Point", "coordinates": [5, 5] };
            ```

            **Notes:**

            - The centroid is the center of mass for the geometry.
            - For multi-part geometries, the result considers all components.
            """)
    public static Value centroid(ObjectValue geometry) {
        return applyUnaryTransformation(geometry, Geometry::getCentroid);
    }

    @Function(docs = """
            ```convexHull(GEOMETRY geometry)```: Returns the convex hull of the geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a Polygon geometry representing the convex hull. Hull vertices keep their input heights. The hull is computed in the XY plane.

            **Example:**

            ```sapl
            policy "example"
            permit
                var points = { "type": "MultiPoint", "coordinates": [[0,0], [0,10], [10,10], [10,0]] };
                geo.area(geo.convexHull(points)) == 100;
            ```

            **Notes:**

            - Computes the smallest convex polygon containing all points.
            - Useful for simplifying geometries or bounding datasets.
            """)
    public static Value convexHull(ObjectValue geometry) {
        return applyUnaryTransformation(geometry, Geometry::convexHull);
    }

    private static Value applyUnaryTransformation(ObjectValue thisGeometry, UnaryOperator<Geometry> transformation) {
        try {
            return boundedGeometryToGeoJSON(transformation.apply(geoJsonToGeometry(thisGeometry)));
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```union(GEOMETRY... geometries)```: Returns the union of an arbitrary number of geometries.

            **Inputs:**

            - `geometries`: A variable number of arguments of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry representing the union of inputs.

            **Example:**

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                geo.area(geo.union(polygon1, polygon2)) == 175;
            ```

            **Notes:**

            - Merges overlapping areas of geometries.
            - Accepts single geometries and multi geometries. GeometryCollection inputs are refused when combined with nonempty geometries.
            """)
    public static Value union(ObjectValue... geometries) {
        try {
            if (geometries.length == 0) {
                return geometryToGeoJSON(WGS84_FACTORY.createEmpty(-1));
            }

            var geomUnion = geoJsonToGeometry(geometries[0]);
            for (int i = 1; i < geometries.length; i++) {
                geomUnion = geomUnion.union(geoJsonToGeometry(geometries[i]));
            }
            return boundedGeometryToGeoJSON(geomUnion);
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```intersection(GEOMETRY thisGeometry, GEOMETRY thatGeometry)```: Returns the intersection of the geometries.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.

            **Output:**

            - Returns a geometry representing the common area of the inputs.

            **Example:**

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                geo.area(geo.intersection(polygon1, polygon2)) == 25;
            ```

            **Notes:**

            - Computes the overlap between geometries.
            - Useful for finding shared areas.
            """)
    public static Value intersection(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                geo.area(geo.difference(polygon1, polygon2)) == 75;
            ```

            **Notes:**

            - Computes the geometric difference by subtracting overlapping areas.
            - Useful for isolating non-intersecting regions.
            """)
    public static Value difference(ObjectValue thisGeometry, ObjectValue thatGeometry) {
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

            ```sapl
            policy "example"
            permit
                var polygon1 = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                var polygon2 = { "type": "Polygon", "coordinates": [[[5,5], [5,15], [15,15], [15,5], [5,5]]] };
                geo.area(geo.symDifference(polygon1, polygon2)) == 150;
            ```

            **Notes:**

            - Computes areas that are exclusive to each input geometry.
            - Useful for highlighting non-overlapping regions.
            """)
    public static Value symDifference(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        return applyBinaryTransformation(thisGeometry, thatGeometry, Geometry::symDifference);
    }

    private static Value applyBinaryTransformation(ObjectValue thisGeometry, ObjectValue thatGeometry,
            BinaryOperator<Geometry> transformation) {
        try {
            return boundedGeometryToGeoJSON(
                    transformation.apply(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry)));
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [3, 4] };
                geo.distance(point1, point2) == 5.0;
            ```

            **Notes:**

            - The distance is calculated based on the coordinate units.
            - Suitable for planar (non-geodesic) distance calculations.
            """)
    public static Value distance(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        try {
            val first  = geoJsonToGeometry(thisGeometry);
            val second = geoJsonToGeometry(thatGeometry);
            requireNonempty(first, second);
            requireDistanceWork(first, second);
            return Value.of(first.distance(second));
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                geo.geodesicDistance(point1, point2) <= 157.25;
            ```

            **Notes:**

            - Uses geodesic calculations suitable for geographic coordinates.
            - Ideal for large-scale or global distance computations.
            """)
    public static Value geodesicDistance(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        try {
            return Value.of(geodesicDistanceOfValues(thisGeometry, thatGeometry));
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```geoDistance(GEOMETRY thisGeometry, GEOMETRY thatGeometry, TEXT coordinateReferenceSystem)```:
            Returns the shortest geodesic distance between two geometries in meters.

            **Inputs:**

            - `thisGeometry`: A geometry object in GeoJSON format.
            - `thatGeometry`: Another geometry object in GeoJSON format.
            - `coordinateReferenceSystem`: A supported geographic EPSG code or the WGS84 alias `CRS:84`. ASCII letter case is ignored.
              Also see: (https://epsg.io).

            **Output:**

            - Returns a numeric value representing the geodesic distance in meters.

            **Example:**

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [0.001, 0.001] };
                geo.geoDistance(point1, point2, "EPSG:4326") <= 157.25;
            ```

            **Notes:**

            - Uses geodesic calculations suitable for geographic coordinates.
            - Ideal for large-scale or global distance computations.
            """)
    public static Value geoDistance(ObjectValue thisGeometry, ObjectValue thatGeometry,
            TextValue coordinateReferenceSystem) {
        try {
            return Value.of(geodesicDistance(thisGeometry, thatGeometry, coordinateReferenceSystem.value()));
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    private static double geodesicDistanceOfValues(ObjectValue thisGeometry, ObjectValue thatGeometry) {
        return geodesicDistance(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry));
    }

    private static double geodesicDistance(ObjectValue thisGeometry, ObjectValue thatGeometry,
            String coordinateReferenceSystem) {
        return geodesicDistance(geoJsonToGeometry(thisGeometry), geoJsonToGeometry(thatGeometry),
                coordinateReferenceSystem);
    }

    private static double geodesicDistance(Geometry thisGeometry, Geometry thatGeometry) {
        return geodesicDistance(thisGeometry, thatGeometry, WGS84);
    }

    private static double geodesicDistance(Geometry thisGeometry, Geometry thatGeometry,
            String coordinateReferenceSystem) {
        requireNonempty(thisGeometry, thatGeometry);
        val ellipsoid = GeographicCoordinateSystems.ellipsoidOf(coordinateReferenceSystem);
        requireDistanceWork(thisGeometry, thatGeometry);
        val nearestPoints = new DistanceOp(thisGeometry, thatGeometry).nearestPoints();
        val gc            = new GeodeticCalculator(ellipsoid);
        gc.setStartingGeographicPoint(nearestPoints[0].x, nearestPoints[0].y);
        gc.setDestinationGeographicPoint(nearestPoints[1].x, nearestPoints[1].y);
        return gc.getOrthodromicDistance();
    }

    @Function(docs = """
            ```length(GEOMETRY geometry)```: Returns the length of a geometry, including perimeter for polygons.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a numeric value representing the length.

            **Example:**

            ```sapl
            policy "example"
            permit
                var line = { "type": "LineString", "coordinates": [[0,0], [0,10], [10,10], [10,0], [0,0]] };
                geo.length(line) == 40.0;
            ```

            **Notes:**

            - Measures the total length or perimeter based on input geometry.
            """)
    public static Value length(ObjectValue geometry) {
        try {
            return Value.of(geoJsonToGeometry(geometry).getLength());
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```area(GEOMETRY geometry)```: Returns the area of a geometry.

            **Inputs:**

            - `geometry`: A geometry object in GeoJSON format.

            **Output:**

            - Returns a numeric value representing the area.

            **Example:**

            ```sapl
            policy "example"
            permit
                var polygon = { "type": "Polygon", "coordinates": [[[0,0], [0,10], [10,10], [10,0], [0,0]]] };
                geo.area(polygon) == 100.0;
            ```

            **Notes:**

            - Computes the area for polygonal geometries.
            - Units depend on coordinate system or projection used.
            """)
    public static Value area(ObjectValue geometry) {
        try {
            return Value.of(geoJsonToGeometry(geometry).getArea());
        } catch (Exception e) {
            return geometryError(e);
        }
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

            ```sapl
            policy "example"
            permit
                var collection1 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                var collection2 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
                geo.subset(collection1, collection2) == true;
            ```

            **Notes:**

            - Suitable for verifying hierarchical relationships between geometry collections.
            """)
    public static Value subset(ObjectValue thisGeometryCollection, ObjectValue thatGeometryCollection) {
        return testGeometryBiPredicate(thisGeometryCollection, thatGeometryCollection,
                GeographicFunctionLibrary::subset);
    }

    private static void enforcePairwiseBound(Geometry first, Geometry second) {
        if ((long) first.getNumGeometries() * second.getNumGeometries() > MAX_PAIRWISE_COMPARISONS) {
            throw new GeometryProblem(ERROR_TOO_MANY_COMPARISONS.formatted(MAX_PAIRWISE_COMPARISONS));
        }
    }

    private static Boolean subset(Geometry geometryCollectionThis, Geometry geometryCollectionThat) {
        enforcePairwiseBound(geometryCollectionThis, geometryCollectionThat);
        var resultSet = new BitSet(geometryCollectionThis.getNumGeometries());
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

            ```sapl
            policy "example"
            permit
                var collection1 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                var collection2 = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [1, 1] },
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                geo.atLeastOneMemberOf(collection1, collection2) == true;
            ```

            **Notes:**

            - Checks for partial membership between geometry collections.
            """)
    public static Value atLeastOneMemberOf(ObjectValue thisGeometryCollection, ObjectValue thatGeometryCollection) {
        return testGeometryBiPredicate(thisGeometryCollection, thatGeometryCollection,
                GeographicFunctionLibrary::atLeastOneMemberOf);
    }

    private static Boolean atLeastOneMemberOf(Geometry geometryCollectionThis, Geometry geometryCollectionThat) {
        enforcePairwiseBound(geometryCollectionThis, geometryCollectionThat);
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

            ```sapl
            policy "example"
            permit
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
                geo.bagSize(collection) == 2;
            ```

            **Notes:**

            - Useful for evaluating collection size.
            """)
    public static Value bagSize(ObjectValue geometryCollection) {
        try {
            return Value.of(geoJsonToGeometry(geometryCollection).getNumGeometries());
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```oneAndOnly(GEOMETRYCOLLECTION geometryCollection)```: Returns the only geometry in a collection if it contains exactly one geometry.

            **Inputs:**

            - `geometryCollection`: A geometry collection in GeoJSON format.

            **Output:**

            - Returns the single geometry if the collection contains exactly one geometry.
            - Returns an error otherwise.

            **Example:**

            ```sapl
            policy "example"
            permit
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                geo.oneAndOnly(collection) == { "type": "Point", "coordinates": [0, 0] };
            ```

            **Notes:**

            - Ensures uniqueness of geometry within a collection.
            - Returns an error if more than one geometry is present.
            """)
    public static Value oneAndOnly(ObjectValue geometryCollection) {
        try {
            if (geoJsonToGeometry(geometryCollection) instanceof GeometryCollection collection
                    && collection.getNumGeometries() == 1) {
                return geometryToGeoJSON(collection.getGeometryN(0));
            } else {
                return Value.error(ERROR_INCORRECT_NUMBER_OF_GEOMETRIES);
            }
        } catch (Exception e) {
            return geometryError(e);
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

            ```sapl
            policy "example"
            permit
                var point = { "type": "Point", "coordinates": [0, 0] };
                var collection = { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] }
                ] };
                geo.geometryIsIn(point, collection) == true;
            ```

            **Notes:**

            - Checks for the existence of a specific geometry within a collection.
            """)
    public static Value geometryIsIn(ObjectValue geometry, ObjectValue geometryCollection) {
        try {
            if (geoJsonToGeometry(geometryCollection) instanceof GeometryCollection collection) {
                val member = geoJsonToGeometry(geometry);
                for (int i = 0; i < collection.getNumGeometries(); i++) {
                    if (member.equals(collection.getGeometryN(i))) {
                        return Value.TRUE;
                    }
                }
                return Value.FALSE;
            }
            return Value.error(ERROR_NOT_A_GEOMETRY_COLLECTION);
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```geometryBag(GEOMETRY... geometries)```: Combines multiple geometries into a single geometry collection.

            **Inputs:**

            - `geometries`: A variable number of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry collection containing all input geometries.

            **Example:**

            ```sapl
            policy "example"
            permit
                var point1 = { "type": "Point", "coordinates": [0, 0] };
                var point2 = { "type": "Point", "coordinates": [1, 1] };
                geo.geometryBag(point1, point2) == { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
            ```

            **Notes:**

            - Useful for grouping geometries into collections.
            """)
    public static Value geometryBag(ObjectValue... geometries) {
        try {
            if (geometries.length > MAX_GEOMETRY_COUNT) {
                return Value.error(ERROR_GEOMETRY_TOO_MANY_MEMBERS.formatted(MAX_GEOMETRY_COUNT));
            }
            val members = new Geometry[geometries.length];
            for (int i = 0; i < geometries.length; i++) {
                members[i] = geoJsonToGeometry(geometries[i]);
            }
            return boundedGeometryToGeoJSON(GEOMETRY_FACTORY.createGeometryCollection(members));
        } catch (Exception e) {
            return geometryError(e);
        }
    }

    @Function(docs = """
            ```flattenGeometryBag(ARRAY geometriesArray)```: Flattens an array of geometries into a single geometry collection.

            **Inputs:**

            - `geometriesArray`: An array of geometry objects in GeoJSON format.

            **Output:**

            - Returns a geometry collection containing all geometries from the input array.
            - Returns an error if the array exceeds the geometry collection member limit.

            **Example:**

            ```sapl
            policy "example"
            permit
                var geometries = [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ];
                geo.flattenGeometryBag(geometries) == { "type": "GeometryCollection", "geometries": [
                    { "type": "Point", "coordinates": [0, 0] },
                    { "type": "Point", "coordinates": [1, 1] }
                ] };
            ```

            **Notes:**

            - Useful for combining geometries from arrays into collections.
            """)
    public static Value flattenGeometryBag(ArrayValue geometriesArray) {
        if (geometriesArray.size() > MAX_GEOMETRY_COUNT) {
            return Value.error(ERROR_GEOMETRY_TOO_MANY_MEMBERS.formatted(MAX_GEOMETRY_COUNT));
        }
        var objectValues = new ObjectValue[geometriesArray.size()];
        for (int i = 0; i < geometriesArray.size(); i++) {
            if (!(geometriesArray.get(i) instanceof ObjectValue geometry)) {
                return Value.error(ERROR_INVALID_GEOJSON);
            }
            objectValues[i] = geometry;
        }
        return geometryBag(objectValues);
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

            ```sapl
            policy "example"
            permit
                geo.milesToMeter(1.0) == 1609.344;
            ```

            **Notes:**

            - Useful for converting mile-based measurements to meters for calculations.
            """)
    public static Value milesToMeter(NumberValue value) {
        return Value.of(milesToMeterDouble(value.value().doubleValue()));
    }

    private static double milesToMeterDouble(double value) {
        return value * DistanceUtils.MILES_TO_KM * 1000.0D;
    }

    @Function(docs = """
            ```yardToMeter(NUMBER value)```: Converts a distance in yards to meters.

            **Inputs:**

            - `value`: A numeric value representing distance in yards.

            **Output:**

            - Returns a numeric value representing the converted distance in meters.

            **Example:**

            ```sapl
            policy "example"
            permit
                geo.yardToMeter(1.0) == 0.9144;
            ```

            **Notes:**

            - Converts yard-based measurements to meters for compatibility in geospatial calculations.
            """)
    public static Value yardToMeter(NumberValue value) {
        return Value.of(yardToMeterDouble(value.value().doubleValue()));
    }

    private static double yardToMeterDouble(double yards) {
        return milesToMeterDouble(yards / 1760.0D);
    }

    @Function(docs = """
            ```degreeToMeter(NUMBER value)```: Converts a distance in degrees to meters.

            **Inputs:**

            - `value`: A numeric value representing distance in degrees.

            **Output:**

            - Returns a numeric value representing the converted distance in meters.

            **Example:**

            ```sapl
            policy "example"
            permit
                geo.degreeToMeter(1.0) > 111000;
            ```

            **Notes:**

            - Converts degree-based distances to meters, useful for geographic coordinate transformations.
            """)
    public static Value degreeToMeter(NumberValue value) {
        return Value.of(value.value().doubleValue() * DistanceUtils.DEG_TO_KM * 1000);
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

            ```sapl
            policy "example"
            permit
                var kmlData = "<kml><Placemark><Point><coordinates>10,20</coordinates></Point></Placemark></kml>";
                geo.kmlToGeoJSON(kmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Use this function to transform KML data into a GeoJSON format for compatibility.
            """)
    public static Value kmlToGeoJSON(TextValue kml) {
        return parseKmlToGeoJSON(kml);
    }

    private static Value parseKmlToGeoJSON(TextValue kml) {
        if (exceedsInputBound(kml.value())) {
            return Value.error(ERROR_GEOMETRY_INPUT_TOO_LARGE.formatted(MAX_GEO_INPUT_BYTES));
        }
        List<Geometry> geometries;
        try {
            geometries = GeometryDocuments.read(kml.value(), KML_CONFIG, true);
        } catch (Exception e) {
            return Value.error(ERROR_FAILED_TO_PARSE_KML);
        }
        if (geometries.isEmpty()) {
            return Value.error(ERROR_NO_GEOMETRIES_IN_KML);
        } else if (geometries.size() == 1) {
            return boundedGeometryToGeoJSON(geometries.getFirst());
        }
        var geometryCollection = WGS84_FACTORY.createGeometryCollection(geometries.toArray(new Geometry[0]));
        return boundedGeometryToGeoJSON(geometryCollection);
    }

    @Function(docs = """
            ```wktToGeoJSON(TEXT wkt)```: Converts WKT data to GeoJSON format.

            **Inputs:**

            - `wkt`: A string containing Well-Known Text (WKT) geometry.

            **Output:**

            - Returns a GeoJSON object representing the converted WKT data.

            **Example:**

            ```sapl
            policy "example"
            permit
                var wktData = "POINT(10 20)";
                geo.wktToGeoJSON(wktData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Useful for converting WKT geometries into GeoJSON for processing.
            - The WKT input is bounded by size and nesting depth before parsing.
            - For portable 3D WKT, write the dimension tag separately, as in `POINT Z (1 2 3)` and
              `LINEARRING Z (0 0 1, 1 0 2, 0 1 3, 0 0 1)`. Java also reads `POINT (1 2 3)` and `LINEARRINGZ`.
            """)
    public static Value wktToGeoJSON(TextValue wkt) {
        val wktString = wkt.value();
        try {
            return boundedGeometryToGeoJSON(wktToGeometryWithinBounds(wktString));
        } catch (ParseException e) {
            return Value.error(ERROR_INVALID_WKT);
        } catch (GeometryProblem e) {
            return Value.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Value.error(wktGeometryError(e));
        }
    }

    @Function(docs = """
            ```gml3ToGeoJSON(TEXT gml)```: Converts GML 3 data to GeoJSON format.

            **Inputs:**

            - `gml`: A string containing GML 3 data.

            **Output:**
            - Returns a GeoJSON object representing the converted GML 3 data.

            **Example:**
            ```sapl
            policy "example"
            permit
                var gmlData = "<gml:Point xmlns:gml='http://www.opengis.net/gml'><gml:coordinates>10,20</gml:coordinates></gml:Point>";
                geo.gml3ToGeoJSON(gmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Designed for compatibility with GML 3 formatted data.
            """)
    public static Value gml3ToGeoJSON(TextValue gml) {
        return gmlToGeoJSON(gml, GML3_CONFIG);
    }

    @Function(docs = """
            ```gml2ToGeoJSON(TEXT gml)```: Converts GML 2 data to GeoJSON format.

            **Inputs:**

            - `gml`: A string containing GML 2 data.

            **Output:**

            - Returns a GeoJSON object representing the converted GML 2 data.

            **Example:**

            ```sapl
            policy "example"
            permit
                var gmlData = "<gml:Point xmlns:gml='http://www.opengis.net/gml'><gml:coordinates>10,20</gml:coordinates></gml:Point>";
                geo.gml2ToGeoJSON(gmlData) == { "type": "Point", "coordinates": [10, 20] };
            ```

            **Notes:**

            - Designed for compatibility with GML 2 formatted data.
            """)
    public static Value gml2ToGeoJSON(TextValue gml) {
        return gmlToGeoJSON(gml, GML2_CONFIG);
    }

    private static Value gmlToGeoJSON(TextValue gml, Configuration configuration) {
        if (exceedsInputBound(gml.value())) {
            return Value.error(ERROR_GEOMETRY_INPUT_TOO_LARGE.formatted(MAX_GEO_INPUT_BYTES));
        }
        List<Geometry> geometries;
        try {
            geometries = GeometryDocuments.read(gml.value(), configuration, false);
        } catch (Exception e) {
            return Value.error(ERROR_FAILED_TO_PARSE_GML);
        }
        if (geometries.isEmpty()) {
            return Value.error(ERROR_NO_GEOMETRIES_IN_GML);
        } else if (geometries.size() == 1) {
            return boundedGeometryToGeoJSON(geometries.getFirst());
        }
        var geometryCollection = WGS84_FACTORY.createGeometryCollection(geometries.toArray(new Geometry[0]));
        return boundedGeometryToGeoJSON(geometryCollection);
    }

    /*
     * Convert Value to Geometry and back
     */

    @SneakyThrows
    static Geometry geoJsonToGeometry(ObjectValue geoJson) {
        val json = ValueJsonMarshaller.toJsonString(geoJson);
        if (exceedsInputBound(json)) {
            throw new GeometryProblem(ERROR_GEOMETRY_INPUT_TOO_LARGE.formatted(MAX_GEO_INPUT_BYTES));
        }
        requireGeometryPositions(geoJson);
        val geometry = GEOJSON_READER.read(json);
        requireComplexityWithinBounds(geometry);
        return geometry;
    }

    private static boolean exceedsInputBound(String input) {
        return input.getBytes(StandardCharsets.UTF_8).length > MAX_GEO_INPUT_BYTES;
    }

    /**
     * Verifies that a geographic text input is within the shared parse boundary.
     *
     * @param input the input text to validate
     * @throws IllegalArgumentException if the input exceeds the configured maximum
     * size
     */
    public static void requireInputWithinBounds(String input) {
        if (exceedsInputBound(input)) {
            throw new GeometryProblem(ERROR_GEOMETRY_INPUT_TOO_LARGE.formatted(MAX_GEO_INPUT_BYTES));
        }
    }

    /**
     * Parses WKT after applying the shared text and nesting bounds.
     *
     * @param wkt the WKT input to parse
     * @return the parsed geometry
     * @throws ParseException if the WKT syntax is invalid
     * @throws IllegalArgumentException if the input exceeds a configured bound
     */
    public static Geometry wktToGeometryWithinBounds(String wkt) throws ParseException {
        requireInputWithinBounds(wkt);
        requireWktNestingWithinBounds(wkt);
        requireCompleteWkt(wkt);
        try {
            return new WKTReader().read(wkt);
        } catch (StackOverflowError e) {
            throw new GeometryProblem(ERROR_GEOMETRY_WKT_NESTING_TOO_DEEP.formatted(MAX_WKT_NESTING_DEPTH), e);
        }
    }

    private static void requireCompleteWkt(String input) throws ParseException {
        val start = input.indexOf('(');
        if (start < 0) {
            if (!input.strip().matches("(?i)[A-Z]+(?:\\s+Z|\\s+M|\\s+ZM)?\\s+EMPTY")) {
                throw new ParseException(ERROR_INVALID_WKT);
            }
            return;
        }
        int depth = 0;
        for (int index = start; index < input.length(); index++) {
            val character = input.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
                if (depth == 0) {
                    if (!input.substring(index + 1).isBlank()) {
                        throw new ParseException(ERROR_INVALID_WKT);
                    }
                    return;
                }
            }
        }
        throw new ParseException(ERROR_INVALID_WKT);
    }

    private static void requireComplexityWithinBounds(Geometry geometry) {
        if (geometry.getNumPoints() > MAX_GEOMETRY_VERTICES) {
            throw new GeometryProblem(ERROR_GEOMETRY_TOO_MANY_VERTICES.formatted(MAX_GEOMETRY_VERTICES));
        }
        if (geometry.getNumGeometries() > MAX_GEOMETRY_COUNT) {
            throw new GeometryProblem(ERROR_GEOMETRY_TOO_MANY_MEMBERS.formatted(MAX_GEOMETRY_COUNT));
        }
    }

    private static void requireWktNestingWithinBounds(String input) {
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            val character = input.charAt(i);
            if (character == '(') {
                depth++;
                if (depth > MAX_WKT_NESTING_DEPTH) {
                    throw new GeometryProblem(ERROR_GEOMETRY_WKT_NESTING_TOO_DEEP.formatted(MAX_WKT_NESTING_DEPTH));
                }
            } else if (character == ')' && depth > 0) {
                depth--;
            }
        }
    }

    /**
     * Converts a geometry to GeoJSON after applying the shared geometry complexity
     * limit.
     *
     * @param geometry the geometry to serialize
     * @return GeoJSON value, or an error value if the geometry is too complex or
     * serialization fails
     */
    public static Value boundedGeometryToGeoJSON(Geometry geometry) {
        try {
            requireComplexityWithinBounds(geometry);
            return geometryToGeoJSON(geometry);
        } catch (GeometryProblem e) {
            return Value.error(e.getMessage());
        }
    }

    static Value geometryToGeoJSON(Geometry geo) {
        try {
            return ValueJsonMarshaller.json(GEOJSON_WRITER.write(geo).replace("\"LinearRing\"", "\"LineString\"")
                    .replace("\"MultiSurface\"", "\"MultiPolygon\"").replace("\"MultiCurve\"", "\"MultiLineString\""));
        } catch (Exception e) {
            return Value.error(ERROR_GEOMETRY_TO_GEO_JSON);
        }
    }

    private static Value geometryError(Exception exception) {
        return Value.error(exception instanceof GeometryProblem ? exception.getMessage() : ERROR_INVALID_GEOJSON);
    }

    private static void requireDistanceWork(Geometry first, Geometry second) {
        val left  = DistancePrimitives.of(first);
        val right = DistancePrimitives.of(second);
        val pairs = left.facets * right.facets + left.polygonEdges * right.locations
                + right.polygonEdges * left.locations;
        if (pairs > MAX_PAIRWISE_COMPARISONS) {
            throw new GeometryProblem(ERROR_DISTANCE_COMPARISON_LIMIT.formatted(MAX_PAIRWISE_COMPARISONS));
        }
    }

    private static final class DistancePrimitives {
        private long facets;
        private long polygonEdges;
        private long locations;

        static DistancePrimitives of(Geometry geometry) {
            val primitives = new DistancePrimitives();
            geometry.apply((GeometryFilter) primitives::count);
            return primitives;
        }

        private void count(Geometry component) {
            if (component instanceof GeometryCollection) {
                return;
            }
            locations++;
            switch (component) {
            case Point ignored   -> facets++;
            case LineString line -> facets += line.getNumPoints() - 1L;
            case Polygon polygon -> {
                val edges = polygon.getNumPoints() - polygon.getNumInteriorRing() - 1L;
                facets       += edges;
                polygonEdges += edges;
            }
            default              -> throw new GeometryProblem(ERROR_INVALID_GEOJSON);
            }
        }
    }

    private static void requireNonempty(Geometry first, Geometry second) {
        if (hasEmptyComponent(first) || hasEmptyComponent(second)) {
            throw new GeometryProblem(ERROR_EMPTY_DISTANCE);
        }
    }

    private static boolean hasEmptyComponent(Geometry geometry) {
        if (geometry.isEmpty()) {
            return true;
        }
        if (geometry instanceof GeometryCollection collection) {
            for (int index = 0; index < collection.getNumGeometries(); index++) {
                if (hasEmptyComponent(collection.getGeometryN(index))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireGeometryPositions(ObjectValue object) {
        val kind = object.get("type");
        if (!(kind instanceof TextValue(String name))) {
            throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
        if ("GeometryCollection".equals(name)) {
            if (!(object.get("geometries") instanceof ArrayValue members)) {
                throw new GeometryProblem(ERROR_INVALID_GEOJSON);
            }
            for (val member : members) {
                if (!(member instanceof ObjectValue geometry)) {
                    throw new GeometryProblem(ERROR_INVALID_GEOJSON);
                }
                requireGeometryPositions(geometry);
            }
            return;
        }
        if (!(object.get("coordinates") instanceof ArrayValue coordinates)) {
            throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
        switch (name) {
        case "Point"                    -> requirePosition(coordinates, true);
        case "LineString", "MultiPoint" -> requirePositions(coordinates, 1, true);
        case "MultiLineString"          -> requirePositions(coordinates, 2, true);
        case "Polygon"                  -> requirePolygon(coordinates, true);
        case "MultiPolygon"             -> {
            for (val polygon : coordinates) {
                requirePolygon(polygon, false);
            }
        }
        default                         -> throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
    }

    private static void requirePolygon(Value value, boolean allowEmpty) {
        if (!(value instanceof ArrayValue rings) || (rings.isEmpty() && !allowEmpty)) {
            throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
        if (rings.size() == 1 && rings.getFirst() instanceof ArrayValue shell && shell.isEmpty()) {
            return;
        }
        for (val ring : rings) {
            if (!(ring instanceof ArrayValue positions) || positions.size() < 4
                    || !positions.getFirst().equals(positions.getLast())) {
                throw new GeometryProblem(ERROR_INVALID_GEOJSON);
            }
            requirePositions(positions, 1, false);
        }
    }

    private static void requirePositions(Value value, int depth, boolean allowEmpty) {
        if (!(value instanceof ArrayValue elements)) {
            throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
        if (depth == 0) {
            requirePosition(elements, allowEmpty);
            return;
        }
        for (val element : elements) {
            requirePositions(element, depth - 1, depth > 1);
        }
    }

    private static void requirePosition(ArrayValue elements, boolean allowEmpty) {
        if (allowEmpty && elements.isEmpty()) {
            return;
        }
        if (elements.size() < 2) {
            throw new GeometryProblem(ERROR_INVALID_GEOJSON);
        }
        for (val element : elements) {
            if (!(element instanceof NumberValue number) || !Double.isFinite(number.value().doubleValue())) {
                throw new GeometryProblem(ERROR_INVALID_GEOJSON);
            }
        }
    }

    private static String wktGeometryError(IllegalArgumentException exception) {
        val message = exception.getMessage();
        if (message.contains("closed linestring")) {
            return ERROR_INVALID_WKT_RING_CLOSED;
        }
        if (message.contains("LineString")) {
            return ERROR_INVALID_WKT_LINE;
        }
        if (message.contains("LinearRing")) {
            return ERROR_INVALID_WKT_RING_SIZE;
        }
        if (message.contains("shell is empty")) {
            return ERROR_INVALID_WKT_SHELL;
        }
        return ERROR_INVALID_WKT;
    }

    static final class GeometryProblem extends IllegalArgumentException {
        GeometryProblem(String message) {
            super(message);
        }

        GeometryProblem(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
