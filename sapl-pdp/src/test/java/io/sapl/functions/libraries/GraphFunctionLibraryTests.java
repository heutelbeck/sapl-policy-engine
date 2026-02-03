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
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("GraphFunctionLibrary")
class GraphFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(GraphFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void reachableWhenMultipleRootsIncludingUnknownThenReturnsAllReachableNodes() {
        val graph   = ObjectValue.builder().put("necronomicon", Value.ofArray(Value.of("pnakotic-manuscripts")))
                .put("pnakotic-manuscripts", Value.ofArray(Value.of("rlyeh-text"))).build();
        val initial = Value.ofArray(Value.of("necronomicon"), Value.of("miskatonic-journal"));

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("necronomicon"), Value.of("pnakotic-manuscripts"),
                Value.of("rlyeh-text"), Value.of("miskatonic-journal"));
    }

    @Test
    void reachablePathsWhenSingleRootThenBuildsShortestPaths() {
        val graph   = ObjectValue.builder()
                .put("azathoth", Value.ofArray(Value.of("nyarlathotep"), Value.of("shub-niggurath")))
                .put("nyarlathotep", Value.ofArray(Value.of("haunter-of-the-dark")))
                .put("haunter-of-the-dark", Value.EMPTY_ARRAY)
                .put("shub-niggurath", Value.ofArray(Value.of("dark-young"))).build();
        val initial = Value.of("azathoth");

        val result = GraphFunctionLibrary.reachablePaths(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val paths = (ArrayValue) result;
        assertThat(paths).contains(Value.ofArray(Value.of("azathoth")),
                Value.ofArray(Value.of("azathoth"), Value.of("shub-niggurath"), Value.of("dark-young")),
                Value.ofArray(Value.of("azathoth"), Value.of("nyarlathotep"), Value.of("haunter-of-the-dark")));
    }

    @Test
    void reachableWhenGraphHasCyclesThenHandlesWithoutInfiniteLoop() {
        val graph   = ObjectValue.builder().put("necronomicon", Value.ofArray(Value.of("book-of-eibon")))
                .put("book-of-eibon", Value.ofArray(Value.of("necronomicon"), Value.of("unaussprechlichen-kulten")))
                .put("unaussprechlichen-kulten", Value.EMPTY_ARRAY).build();
        val initial = Value.of("necronomicon");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("necronomicon"), Value.of("book-of-eibon"),
                Value.of("unaussprechlichen-kulten"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyInputCases")
    void reachableWhenEmptyInputsThenReturnsExpectedSize(String description, ObjectValue graph, Value initial,
            int expectedSize) {
        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(expectedSize);
    }

    private static Stream<Arguments> emptyInputCases() {
        return Stream.of(arguments("empty graph with empty initial",
                ObjectValue.builder().put("celaeno-fragments", Value.EMPTY_ARRAY).build(), Value.EMPTY_ARRAY, 0),
                arguments("empty graph with unknown root", Value.EMPTY_OBJECT,
                        Value.ofArray(Value.of("king-in-yellow")), 1),
                arguments(
                        "graph with nodes but empty initial", ObjectValue.builder()
                                .put("pnakotic-manuscripts", Value.ofArray(Value.of("rlyeh-text"))).build(),
                        Value.EMPTY_ARRAY, 0));
    }

    @Test
    void reachableWhenNumericNodeIdsThenHandlesCorrectly() {
        val graph   = ObjectValue.builder().put("1", Value.ofArray(Value.of("2"), Value.of("3")))
                .put("2", Value.ofArray(Value.of("4"))).put("3", Value.EMPTY_ARRAY).put("4", Value.EMPTY_ARRAY).build();
        val initial = Value.of("1");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("1"), Value.of("2"), Value.of("3"), Value.of("4"));
    }

    @Test
    void reachableWhenNonArrayAdjacencyValuesThenIgnoresThem() {
        val graph   = ObjectValue.builder().put("de-vermis-mysteriis", Value.ofArray(Value.of("cultes-des-goules")))
                .put("cultes-des-goules", Value.of("corrupted-catalog-entry")).put("book-of-eibon", Value.of(666))
                .put("unaussprechlichen-kulten", Value.TRUE).put("celaeno-fragments", Value.NULL).build();
        val initial = Value.ofArray(Value.of("de-vermis-mysteriis"), Value.of("book-of-eibon"),
                Value.of("unaussprechlichen-kulten"), Value.of("celaeno-fragments"));

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("de-vermis-mysteriis"),
                Value.of("cultes-des-goules"), Value.of("book-of-eibon"), Value.of("unaussprechlichen-kulten"),
                Value.of("celaeno-fragments"));
    }

    @Test
    void reachableWhenDisconnectedComponentsThenOnlyReachesConnectedNodes() {
        val graph   = ObjectValue.builder().put("innsmouth-cult-texts", Value.ofArray(Value.of("dagon-liturgy")))
                .put("dagon-liturgy", Value.EMPTY_ARRAY)
                .put("arkham-lodge-records", Value.ofArray(Value.of("yog-sothoth-rituals")))
                .put("yog-sothoth-rituals", Value.EMPTY_ARRAY).build();
        val initial = Value.of("innsmouth-cult-texts");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("innsmouth-cult-texts"), Value.of("dagon-liturgy"))
                .doesNotContain(Value.of("arkham-lodge-records"), Value.of("yog-sothoth-rituals"));
    }

    @Test
    void reachableWhenSelfLoopsThenHandlesCorrectly() {
        val graph   = ObjectValue.builder()
                .put("de-vermis-mysteriis",
                        Value.ofArray(Value.of("de-vermis-mysteriis"), Value.of("cultes-des-goules")))
                .put("cultes-des-goules", Value.EMPTY_ARRAY).build();
        val initial = Value.of("de-vermis-mysteriis");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("de-vermis-mysteriis"),
                Value.of("cultes-des-goules"));
    }

    @Test
    void reachablePathsWhenMultipleRootsThenBuildsPathsForAll() {
        val graph   = ObjectValue.builder().put("pnakotic-manuscripts", Value.ofArray(Value.of("rlyeh-text")))
                .put("rlyeh-text", Value.EMPTY_ARRAY)
                .put("book-of-eibon", Value.ofArray(Value.of("testaments-of-carnamagos")))
                .put("testaments-of-carnamagos", Value.EMPTY_ARRAY).build();
        val initial = Value.ofArray(Value.of("pnakotic-manuscripts"), Value.of("book-of-eibon"));

        val result = GraphFunctionLibrary.reachablePaths(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val paths = (ArrayValue) result;
        assertThat(paths).contains(Value.ofArray(Value.of("pnakotic-manuscripts")),
                Value.ofArray(Value.of("pnakotic-manuscripts"), Value.of("rlyeh-text")),
                Value.ofArray(Value.of("book-of-eibon")),
                Value.ofArray(Value.of("book-of-eibon"), Value.of("testaments-of-carnamagos")));
    }

    @Test
    void reachablePathsWhenCyclesThenReturnsShortestPath() {
        val graph   = ObjectValue.builder()
                .put("miskatonic-library-catalog", Value.ofArray(Value.of("restricted-section")))
                .put("restricted-section",
                        Value.ofArray(Value.of("miskatonic-library-catalog"), Value.of("necronomicon-translation")))
                .put("necronomicon-translation", Value.EMPTY_ARRAY).build();
        val initial = Value.of("miskatonic-library-catalog");

        val result = GraphFunctionLibrary.reachablePaths(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val paths = (ArrayValue) result;
        assertThat(paths).contains(Value.ofArray(Value.of("miskatonic-library-catalog")),
                Value.ofArray(Value.of("miskatonic-library-catalog"), Value.of("restricted-section")),
                Value.ofArray(Value.of("miskatonic-library-catalog"), Value.of("restricted-section"),
                        Value.of("necronomicon-translation")));
    }

    @Test
    void reachablePathsWhenNonArrayAdjacenciesThenIgnoresThem() {
        val graph   = ObjectValue.builder().put("r-lyeh-text", Value.ofArray(Value.of("sussex-manuscript")))
                .put("sussex-manuscript", Value.of("damaged-catalog-entry")).build();
        val initial = Value.of("r-lyeh-text");

        val result = GraphFunctionLibrary.reachablePaths(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val paths = (ArrayValue) result;
        assertThat(paths).containsExactlyInAnyOrder(Value.ofArray(Value.of("r-lyeh-text")),
                Value.ofArray(Value.of("r-lyeh-text"), Value.of("sussex-manuscript")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyPathInputCases")
    void reachablePathsWhenEmptyInputsThenReturnsExpectedPaths(String description, ObjectValue graph, Value initial,
            int expectedPaths) {
        val result = GraphFunctionLibrary.reachablePaths(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).hasSize(expectedPaths);
    }

    private static Stream<Arguments> emptyPathInputCases() {
        return Stream.of(
                arguments("empty initial array", ObjectValue.builder().put("sign-of-koth", Value.EMPTY_ARRAY).build(),
                        Value.EMPTY_ARRAY, 0),
                arguments("unknown root in empty graph", Value.EMPTY_OBJECT,
                        Value.ofArray(Value.of("nyarlathotep-testament")), 1));
    }
}
