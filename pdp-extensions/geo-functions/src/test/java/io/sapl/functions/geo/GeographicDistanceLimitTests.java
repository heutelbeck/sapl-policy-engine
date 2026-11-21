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

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.functions.geo.GeographicFunctionLibrary.ERROR_DISTANCE_COMPARISON_LIMIT;
import static io.sapl.functions.geo.GeographicFunctionLibrary.ERROR_EMPTY_DISTANCE;
import static io.sapl.functions.geo.GeographicFunctionLibrary.ERROR_UNSUPPORTED_CRS;
import static io.sapl.functions.geo.GeographicFunctionLibrary.MAX_PAIRWISE_COMPARISONS;
import static io.sapl.functions.geo.GeographicFunctionLibrary.distance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.geoDistance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isWithinDistance;
import static io.sapl.functions.geo.GeographicFunctionLibrary.isWithinGeodesicDistance;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("Distance computation limits")
class GeographicDistanceLimitTests {

    private static final Value OVER_LIMIT = Value
            .error(ERROR_DISTANCE_COMPARISON_LIMIT.formatted(MAX_PAIRWISE_COMPARISONS));

    static Stream<Arguments> distanceFunctions() {
        return Stream.of(distanceCase("distance", GeographicFunctionLibrary::distance, Value.of(0)),
                distanceCase("isWithinDistance", (a, b) -> isWithinDistance(a, b, Value.of(0)), Value.TRUE),
                distanceCase("geodesicDistance", GeographicFunctionLibrary::geodesicDistance, Value.of(0)),
                distanceCase("isWithinGeodesicDistance", (a, b) -> isWithinGeodesicDistance(a, b, Value.of(0)),
                        Value.TRUE),
                distanceCase("geoDistance", (a, b) -> geoDistance(a, b, Value.of("EPSG:4326")), Value.of(0)));
    }

    private static Arguments distanceCase(String name, BiFunction<ObjectValue, ObjectValue, Value> operation,
            Value expected) {
        return arguments(name, operation, expected);
    }

    @MethodSource("distanceFunctions")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Distance permits exactly one million segment pairs")
    void whenLinesReachPrimitivePairLimitThenDistanceObservesIt(String name,
            BiFunction<ObjectValue, ObjectValue, Value> operation, Value expected) {
        val atLimit = line(1000);
        assertThat(operation.apply(atLimit, atLimit)).as(name).isEqualTo(expected);
        assertThat(operation.apply(line(101), line(9901))).as(name).isEqualTo(OVER_LIMIT);
    }

    @Test
    @DisplayName("Nested polygon holes count toward boundary and containment comparisons")
    void whenNestedPolygonHasHoleThenDistanceBudgetsBoundaryAndContainment() {
        val hole = new ArrayList<String>();
        for (int x = 0; x < 125; x++) {
            hole.add("[%d,0]".formatted(x));
        }
        for (int y = 0; y < 125; y++) {
            hole.add("[125,%d]".formatted(y));
        }
        for (int x = 125; x > 0; x--) {
            hole.add("[%d,125]".formatted(x));
        }
        for (int y = 125; y > 0; y--) {
            hole.add("[0,%d]".formatted(y));
        }
        hole.add("[0,0]");
        val polygon = (ObjectValue) json(
                """
                        {"type":"GeometryCollection","geometries":[{"type":"MultiPolygon","coordinates":[[[[-1,-1],[126,-1],[126,126],[-1,126],[-1,-1]],[%s]]]}]}
                        """
                        .formatted(String.join(",", hole)));
        val points  = (ObjectValue) json("""
                {"type":"MultiPoint","coordinates":[%s]}
                """.formatted(String.join(",", Collections.nCopies(1000, "[-1,-1]"))));
        assertThat(Stream.of(distance(polygon, points), distance(points, polygon))).containsOnly(OVER_LIMIT);
    }

    @Test
    @DisplayName("Distance reports empty geometries and unknown systems before the work limit")
    void whenDistanceInputsAreInvalidThenSpecificErrorPrecedesWorkLimit() {
        val invalid  = Value.of("EPSG:3857");
        val longLine = line(9901);
        val empty    = (ObjectValue) json("""
                {"type":"Point","coordinates":[]}
                """);
        assertThat(geoDistance(line(101), longLine, invalid))
                .isEqualTo(Value.error(ERROR_UNSUPPORTED_CRS.formatted("EPSG:3857")));
        assertThat(geoDistance(empty, longLine, invalid)).isEqualTo(Value.error(ERROR_EMPTY_DISTANCE));
    }

    private static ObjectValue line(int segments) {
        val positions = IntStream.rangeClosed(0, segments).mapToObj("[%d,0]"::formatted).collect(joining(","));
        return (ObjectValue) json("""
                {"type":"LineString","coordinates":[%s]}
                """.formatted(positions));
    }
}
