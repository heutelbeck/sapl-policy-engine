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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import static io.sapl.functions.geo.GeographicFunctionLibrary.ERROR_INVALID_GEOJSON;
import static io.sapl.functions.geo.GeographicFunctionLibrary.area;
import static io.sapl.functions.geo.GeographicFunctionLibrary.crosses;
import static io.sapl.functions.geo.GeographicFunctionLibrary.within;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("GeoJSON coordinate arrays and linear rings")
class GeoJsonGeometryTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static ObjectValue geometry(String document) {
        return (ObjectValue) MAPPER.readValue(document, Value.class);
    }

    @Test
    @DisplayName("A line can cross a line in a geometry collection")
    void whenALineCrossesACollectionMemberThenTheCrossingIsSymmetric() {
        val collection = geometry("""
                {"type":"GeometryCollection","geometries":[{"type":"LineString","coordinates":[[0,0],[10,10]]}]}
                """);
        val crossing   = geometry("""
                {"type":"LineString","coordinates":[[0,10],[10,0]]}
                """);
        val disjoint   = geometry("""
                {"type":"LineString","coordinates":[[30,10],[40,10]]}
                """);
        assertThat(crosses(collection, crossing)).isEqualTo(Value.TRUE);
        assertThat(crosses(crossing, collection)).isEqualTo(Value.TRUE);
        assertThat(crosses(collection, disjoint)).isEqualTo(Value.FALSE);
    }

    static Stream<Arguments> missingCoordinateArrays() {
        return Stream.of("Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon")
                .flatMap(kind -> Stream.of("", ",\"coordinates\":null", ",\"coordinates\":1")
                        .map(member -> arguments(kind, member)));
    }

    @MethodSource("missingCoordinateArrays")
    @ParameterizedTest(name = "{0} with coordinate member [{1}]")
    @DisplayName("Each geometry with positions requires a coordinate array")
    void whenGeometryLacksCoordinateArrayThenItIsRefused(String kind, String member) {
        val document   = """
                {"type":"%s"%s}
                """.formatted(kind, member);
        val collection = """
                {"type":"GeometryCollection","geometries":[%s]}
                """.formatted(document);
        assertThat(Stream.of(document, collection).map(GeoJsonGeometryTests::geometry)
                .map(GeographicFunctionLibrary::area)).containsOnly(Value.error(ERROR_INVALID_GEOJSON));
    }

    static Stream<Arguments> invalidPolygonRings() {
        return Stream.of(arguments("three positions", "[[[0,0],[2,0],[0,0]]]"),
                arguments("open ring", "[[[0,0],[2,0],[0,1],[0,1]]]"),
                arguments("unclosed height", "[[[0,0,1],[2,0,1],[0,1,1],[0,0,2]]]"),
                arguments("unequal dimensionality", "[[[0,0,1],[2,0],[0,1],[0,0]]]"),
                arguments("unclosed fourth ordinate", "[[[0,0,1,2],[2,0],[0,1],[0,0,1,3]]]"),
                arguments("different exact decimal positions", "[[[0.1,0],[2,0],[0,1],[0.10000000000000000001,0]]]"),
                arguments("short hole", "[[[0,0],[2,0],[0,2],[0,0]],[[0,0],[1,1],[0,0]]]"),
                arguments("empty hole", "[[[0,0],[2,0],[0,2],[0,0]],[]]"),
                arguments("multiple empty rings", "[[],[]]"));
    }

    @MethodSource("invalidPolygonRings")
    @ParameterizedTest(name = "{0}")
    @DisplayName("A malformed ring invalidates every containing geometry")
    void whenPolygonHasInvalidRingThenItsGeometryIsRefused(String description, String rings) {
        val polygon      = """
                {"type":"Polygon","coordinates":%s}
                """.formatted(rings);
        val multiPolygon = """
                {"type":"MultiPolygon","coordinates":[%s]}
                """.formatted(rings);
        val collection   = """
                {"type":"GeometryCollection","geometries":[%s]}
                """.formatted(multiPolygon);
        assertThat(Stream.of(polygon, multiPolygon, collection).map(document -> area(geometry(document))))
                .as(description).containsOnly(Value.error(ERROR_INVALID_GEOJSON));
    }

    @Test
    @DisplayName("A geofence handles a valid location after rejecting an incomplete position")
    void whenLocationIsMalformedThenTheNextValidLocationCanStillBeChecked() {
        val facility  = geometry(
                """
                        {"type":"Polygon","coordinates":[[[13.38,52.50],[13.42,52.50],[13.42,52.53],[13.38,52.53],[13.38,52.50]]]}
                        """);
        val malformed = geometry("""
                {"type":"Point","coordinates":[13.405]}
                """);
        val location  = geometry("""
                {"type":"Point","coordinates":[13.405,52.52]}
                """);
        assertThat(Stream.of(malformed, location).map(point -> within(point, facility)))
                .containsExactly(Value.error(ERROR_INVALID_GEOJSON), Value.TRUE);
    }

    static Stream<Arguments> validGeometries() {
        return Stream.of(arguments("Polygon", "[]", 0), arguments("Polygon", "[[]]", 0), arguments("Point", "[]", 0),
                arguments("MultiPoint", "[]", 0), arguments("LineString", "[]", 0),
                arguments("MultiLineString", "[]", 0), arguments("MultiPolygon", "[]", 0),
                arguments("MultiPolygon", "[[[]]]", 0),
                arguments("Polygon", "[[[0,0,1,2],[2,0,1,2],[0,1,1,2],[0.0,0,1,2.00]]]", 1));
    }

    @MethodSource("validGeometries")
    @ParameterizedTest(name = "{0} with coordinates {1}")
    @DisplayName("Empty geometries and rings closed in every ordinate have an area")
    void whenGeometryIsEmptyOrRingIsClosedThenItsAreaIsAvailable(String kind, String coordinates, int expected) {
        val document = """
                {"type":"%s","coordinates":%s}
                """.formatted(kind, coordinates);
        assertThat(area(geometry(document))).isEqualTo(Value.of(expected));
    }

}
