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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;

@DisplayName("Topological predicates use the points represented by nonempty members")
class GeographicEmptyMembersTests {
    private static final String CORNER = "POINT (5 5)";
    private static final String SQUARE = "POLYGON ((5 5,15 5,15 15,5 15,5 5))";

    @Test
    @DisplayName("overlapping line members keep an interior point inside instead of on the boundary")
    void whenLineMembersOverlapThenTheirSharedPointSetHasTheSameInterior() {
        val broker = broker();
        val point  = geometry(broker, "POINT (1 0)");
        for (val wkt : List.of("LINESTRING (0 0,4 0)", "MULTILINESTRING ((0 0,4 0),(1 0,3 0))")) {
            val line     = geometry(broker, wkt);
            val outcomes = List.of(evaluate(broker, "contains", line, point), evaluate(broker, "within", point, line),
                    evaluate(broker, "touches", line, point), evaluate(broker, "touches", point, line));
            assertThat(outcomes).as("interior of %s", wkt).containsExactly(Value.TRUE, Value.TRUE, Value.FALSE,
                    Value.FALSE);
        }
    }

    @ParameterizedTest(name = "{0} at the corner, reverse={2}")
    @MethodSource("cornerPredicates")
    @DisplayName("an empty polygon member cannot turn a boundary point into an area")
    void whenEmptyPolygonAccompaniesBoundaryPointThenItsDimensionDoesNotAffectTheRelation(String predicate,
            boolean expected, boolean reverse) {
        val broker = broker();
        val point  = "GEOMETRYCOLLECTION (POLYGON EMPTY," + CORNER + ")";
        val first  = geometry(broker, reverse ? SQUARE : point);
        val second = geometry(broker, reverse ? point : SQUARE);
        assertThat(evaluate(broker, predicate, first, second)).isEqualTo(Value.of(expected));
    }

    static Stream<Arguments> cornerPredicates() {
        return Stream.of(arguments("contains", false, false), arguments("contains", false, true),
                arguments("touches", true, false), arguments("touches", true, true),
                arguments("overlaps", false, false), arguments("overlaps", false, true));
    }

    @ParameterizedTest(name = "{0} ignores empty members in 0D, 1D, 2D and mixed collections")
    @ValueSource(strings = { "disjoint", "touches", "crosses", "within", "contains", "overlaps", "intersects" })
    @DisplayName("adding empty and nested members preserves both operand directions")
    void whenEmptyMembersAreAddedThenTheRepresentedPointSetHasTheSameRelations(String predicate) {
        val broker = broker();
        val pairs  = List.of(List.of("POINT (5 5)", SQUARE),
                List.of("MULTIPOINT ((5 5),(8 8))", "MULTIPOINT ((8 8),(20 20))"),
                List.of("LINESTRING (0 10,20 10)", SQUARE), List.of("LINESTRING (0 0,10 0)", "LINESTRING (5 0,15 0)"),
                List.of("POLYGON ((0 0,10 0,10 10,0 10,0 0))", SQUARE),
                List.of("GEOMETRYCOLLECTION (POINT (20 20),LINESTRING (0 10,20 10))", SQUARE),
                List.of("GEOMETRYCOLLECTION EMPTY", SQUARE),
                List.of("GEOMETRYCOLLECTION EMPTY", "GEOMETRYCOLLECTION EMPTY"), List.of("POINT (5 5)", "POINT (5 5)"),
                List.of("LINESTRING (0 0,10 10)", "POINT (5 5)"),
                List.of("MULTILINESTRING ((0 0,4 0),(1 0,3 0))", "POINT (1 0)"));
        for (val pair : pairs) {
            val first   = geometry(broker, pair.getFirst());
            val second  = geometry(broker, pair.getLast());
            val forward = evaluate(broker, predicate, first, second);
            val reverse = evaluate(broker, predicate, second, first);
            assertThat(List.of(forward, reverse))
                    .allSatisfy(value -> assertThat(value).isInstanceOf(BooleanValue.class));
            for (val wrapper : List.of("GEOMETRYCOLLECTION (POLYGON EMPTY,%s)",
                    "GEOMETRYCOLLECTION (LINESTRING EMPTY,GEOMETRYCOLLECTION (POLYGON EMPTY,%s))",
                    "GEOMETRYCOLLECTION (POINT EMPTY,MULTILINESTRING EMPTY,GEOMETRYCOLLECTION (%s,POLYGON EMPTY))")) {
                val wrappedFirst  = geometry(broker, wrapper.formatted(pair.getFirst()));
                val wrappedSecond = geometry(broker, wrapper.formatted(pair.getLast()));
                val outcomes      = List.of(evaluate(broker, predicate, wrappedFirst, second),
                        evaluate(broker, predicate, first, wrappedSecond),
                        evaluate(broker, predicate, wrappedFirst, wrappedSecond),
                        evaluate(broker, predicate, second, wrappedFirst),
                        evaluate(broker, predicate, wrappedSecond, first),
                        evaluate(broker, predicate, wrappedSecond, wrappedFirst));
                assertThat(outcomes).as("%s: %s against %s", predicate, wrapper, pair).containsExactly(forward, forward,
                        forward, reverse, reverse, reverse);
            }
        }
    }

    private static DefaultFunctionBroker broker() {
        val broker = new DefaultFunctionBroker();
        broker.load(new GeographicFunctionLibrary());
        return broker;
    }

    private static ObjectValue geometry(DefaultFunctionBroker broker, String wkt) {
        val value = broker.evaluateFunction(new FunctionInvocation("geo.wktToGeoJSON", List.of(Value.of(wkt))));
        assertThat(value).as("valid ordinary fixture %s", wkt).isInstanceOf(ObjectValue.class);
        return (ObjectValue) value;
    }

    private static Value evaluate(DefaultFunctionBroker broker, String predicate, Value first, Value second) {
        return broker.evaluateFunction(new FunctionInvocation("geo." + predicate, List.of(first, second)));
    }
}
