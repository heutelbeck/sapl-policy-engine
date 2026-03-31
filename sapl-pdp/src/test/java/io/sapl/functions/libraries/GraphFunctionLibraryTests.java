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
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "necronomicon":          ["pnakotic-manuscripts"],
                  "pnakotic-manuscripts":  ["rlyeh-text"]
                }
                """);
        val initial = Value.ofArray(Value.of("necronomicon"), Value.of("miskatonic-journal"));

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("necronomicon"), Value.of("pnakotic-manuscripts"),
                Value.of("rlyeh-text"), Value.of("miskatonic-journal"));
    }

    @Test
    void reachablePathsWhenSingleRootThenBuildsShortestPaths() {
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "azathoth":            ["nyarlathotep", "shub-niggurath"],
                  "nyarlathotep":        ["haunter-of-the-dark"],
                  "haunter-of-the-dark": [],
                  "shub-niggurath":      ["dark-young"]
                }
                """);
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
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "necronomicon":             ["book-of-eibon"],
                  "book-of-eibon":            ["necronomicon", "unaussprechlichen-kulten"],
                  "unaussprechlichen-kulten":  []
                }
                """);
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
        return Stream.of(arguments("empty graph with empty initial", Value.ofJson("""
                { "celaeno-fragments": [] }
                """), Value.EMPTY_ARRAY, 0),
                arguments("empty graph with unknown root", Value.EMPTY_OBJECT,
                        Value.ofArray(Value.of("king-in-yellow")), 1),
                arguments("graph with nodes but empty initial", Value.ofJson("""
                        { "pnakotic-manuscripts": ["rlyeh-text"] }
                        """), Value.EMPTY_ARRAY, 0));
    }

    @Test
    void reachableWhenNumericNodeIdsThenHandlesCorrectly() {
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "1": ["2", "3"],
                  "2": ["4"],
                  "3": [],
                  "4": []
                }
                """);
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
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "innsmouth-cult-texts":  ["dagon-liturgy"],
                  "dagon-liturgy":         [],
                  "arkham-lodge-records":   ["yog-sothoth-rituals"],
                  "yog-sothoth-rituals":   []
                }
                """);
        val initial = Value.of("innsmouth-cult-texts");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("innsmouth-cult-texts"), Value.of("dagon-liturgy"))
                .doesNotContain(Value.of("arkham-lodge-records"), Value.of("yog-sothoth-rituals"));
    }

    @Test
    void reachableWhenSelfLoopsThenHandlesCorrectly() {
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "de-vermis-mysteriis": ["de-vermis-mysteriis", "cultes-des-goules"],
                  "cultes-des-goules":   []
                }
                """);
        val initial = Value.of("de-vermis-mysteriis");

        val result = GraphFunctionLibrary.reachable(graph, initial);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val resultArray = (ArrayValue) result;
        assertThat(resultArray).containsExactlyInAnyOrder(Value.of("de-vermis-mysteriis"),
                Value.of("cultes-des-goules"));
    }

    @Test
    void reachablePathsWhenMultipleRootsThenBuildsPathsForAll() {
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "pnakotic-manuscripts":      ["rlyeh-text"],
                  "rlyeh-text":                [],
                  "book-of-eibon":             ["testaments-of-carnamagos"],
                  "testaments-of-carnamagos":  []
                }
                """);
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
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "miskatonic-library-catalog": ["restricted-section"],
                  "restricted-section":         ["miskatonic-library-catalog", "necronomicon-translation"],
                  "necronomicon-translation":   []
                }
                """);
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
        val graph   = (ObjectValue) Value.ofJson("""
                {
                  "r-lyeh-text":       ["sussex-manuscript"],
                  "sussex-manuscript": "damaged-catalog-entry"
                }
                """);
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
        return Stream.of(arguments("empty initial array", Value.ofJson("""
                { "sign-of-koth": [] }
                """), Value.EMPTY_ARRAY, 0), arguments("unknown root in empty graph", Value.EMPTY_OBJECT,
                Value.ofArray(Value.of("nyarlathotep-testament")), 1));
    }

    @Test
    @DisplayName("transitiveClosure precomputes reachable sets for all nodes")
    void transitiveClosureWhenLinearChainThenEachNodeMapsToAllDescendants() {
        val graph = (ObjectValue) Value.ofJson("""
                {
                  "elder-god":     ["great-old-one"],
                  "great-old-one": ["outer-god"],
                  "outer-god":     []
                }
                """);

        val closure = (ObjectValue) GraphFunctionLibrary.transitiveClosure(graph);

        assertThat((ArrayValue) closure.get("elder-god")).containsExactlyInAnyOrder(Value.of("elder-god"),
                Value.of("great-old-one"), Value.of("outer-god"));
        assertThat((ArrayValue) closure.get("great-old-one")).containsExactlyInAnyOrder(Value.of("great-old-one"),
                Value.of("outer-god"));
        assertThat((ArrayValue) closure.get("outer-god")).containsExactlyInAnyOrder(Value.of("outer-god"));
    }

    @Test
    @DisplayName("transitiveClosure handles cycles without infinite loop")
    void transitiveClosureWhenCycleThenAllNodesReachEachOther() {
        val graph = (ObjectValue) Value.ofJson("""
                {
                  "yog-sothoth": ["azathoth"],
                  "azathoth":    ["yog-sothoth"]
                }
                """);

        val closure = (ObjectValue) GraphFunctionLibrary.transitiveClosure(graph);

        assertThat((ArrayValue) closure.get("yog-sothoth")).containsExactlyInAnyOrder(Value.of("yog-sothoth"),
                Value.of("azathoth"));
        assertThat((ArrayValue) closure.get("azathoth")).containsExactlyInAnyOrder(Value.of("azathoth"),
                Value.of("yog-sothoth"));
    }

    @Test
    @DisplayName("transitiveClosure on empty graph returns empty object")
    void transitiveClosureWhenEmptyGraphThenEmptyResult() {
        assertThat(GraphFunctionLibrary.transitiveClosure(Value.EMPTY_OBJECT)).isEqualTo(Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("transitiveClosure enables O(1) role hierarchy lookup")
    void transitiveClosureWhenUsedForRoleLookupThenCorrect() {
        val hierarchy = (ObjectValue) Value.ofJson("""
                {
                  "admin":   ["manager"],
                  "manager": ["viewer"],
                  "viewer":  []
                }
                """);

        val closure    = (ObjectValue) GraphFunctionLibrary.transitiveClosure(hierarchy);
        val adminRoles = (ArrayValue) closure.get("admin");

        assertThat(adminRoles).contains(Value.of("admin"), Value.of("manager"), Value.of("viewer"));
        assertThat(adminRoles.contains(Value.of("viewer"))).isTrue();
    }

    @Test
    @DisplayName("transitiveClosureSet returns object-valued reachable sets for O(1) lookup")
    void transitiveClosureSetWhenLinearChainThenKeysAreReachableNodes() {
        val graph = (ObjectValue) Value.ofJson("""
                {
                  "admin":   ["manager"],
                  "manager": ["viewer"],
                  "viewer":  []
                }
                """);

        val closure  = (ObjectValue) GraphFunctionLibrary.transitiveClosureSet(graph);
        val adminSet = (ObjectValue) closure.get("admin");

        assertThat(adminSet.get("admin")).isEqualTo(Value.TRUE);
        assertThat(adminSet.get("manager")).isEqualTo(Value.TRUE);
        assertThat(adminSet.get("viewer")).isEqualTo(Value.TRUE);
        assertThat(adminSet.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("transitiveClosureSet on empty graph returns empty object")
    void transitiveClosureSetWhenEmptyThenEmpty() {
        assertThat(GraphFunctionLibrary.transitiveClosureSet(Value.EMPTY_OBJECT)).isEqualTo(Value.EMPTY_OBJECT);
    }

    private static ObjectValue entityGraph() {
        return (ObjectValue) Value.ofJson("""
                {
                  "elder-thing": {
                    "servants": ["shoggoth"],
                    "attributes": { "type": "Ancient", "powers": ["telepathy", "architecture"] }
                  },
                  "shoggoth": {
                    "servants": [],
                    "attributes": { "type": "Servitor", "powers": ["shapeshifting"] }
                  }
                }
                """);
    }

    @Test
    @DisplayName("transitiveClosure with edge key follows named edge field")
    void transitiveClosureWithEdgeKeyWhenEntityGraphThenFollowsNamedEdges() {
        val closure = (ObjectValue) GraphFunctionLibrary.transitiveClosure(entityGraph(), Value.of("servants"));

        assertThat((ArrayValue) closure.get("elder-thing")).containsExactlyInAnyOrder(Value.of("elder-thing"),
                Value.of("shoggoth"));
        assertThat((ArrayValue) closure.get("shoggoth")).containsExactlyInAnyOrder(Value.of("shoggoth"));
    }

    @Test
    @DisplayName("transitiveClosureSet with edge key returns object-keyed sets")
    void transitiveClosureSetWithEdgeKeyWhenEntityGraphThenObjectKeys() {
        val closure  = (ObjectValue) GraphFunctionLibrary.transitiveClosureSet(entityGraph(), Value.of("servants"));
        val elderSet = (ObjectValue) closure.get("elder-thing");

        assertThat(elderSet.get("elder-thing")).isEqualTo(Value.TRUE);
        assertThat(elderSet.get("shoggoth")).isEqualTo(Value.TRUE);
    }

    @Test
    @DisplayName("transitiveClosureProjection collects attributes from reached nodes")
    void transitiveClosureProjectionWhenEntityGraphThenCollectsAttributes() {
        val projection = (ObjectValue) GraphFunctionLibrary.transitiveClosureProjection(entityGraph(),
                Value.of("servants"), Value.of("powers"));

        assertThat((ArrayValue) projection.get("elder-thing")).containsExactlyInAnyOrder(Value.of("telepathy"),
                Value.of("architecture"), Value.of("shapeshifting"));
        assertThat((ArrayValue) projection.get("shoggoth")).containsExactlyInAnyOrder(Value.of("shapeshifting"));
    }

    @Test
    @DisplayName("transitiveClosureProjection with Cedar-style RBAC hierarchy")
    void transitiveClosureProjectionWhenRbacHierarchyThenCollectsPermissions() {
        val roles = (ObjectValue) Value.ofJson("""
                {
                  "admin":   { "children": ["manager"], "attributes": { "permissions": ["approve", "delete"] } },
                  "manager": { "children": ["viewer"],  "attributes": { "permissions": ["write"] } },
                  "viewer":  { "children": [],          "attributes": { "permissions": ["read"] } }
                }
                """);

        val perms = (ObjectValue) GraphFunctionLibrary.transitiveClosureProjection(roles, Value.of("children"),
                Value.of("permissions"));

        assertThat((ArrayValue) perms.get("admin")).containsExactlyInAnyOrder(Value.of("approve"), Value.of("delete"),
                Value.of("write"), Value.of("read"));
        assertThat((ArrayValue) perms.get("manager")).containsExactlyInAnyOrder(Value.of("write"), Value.of("read"));
        assertThat((ArrayValue) perms.get("viewer")).containsExactlyInAnyOrder(Value.of("read"));
    }

    @Test
    @DisplayName("playground hierarchical RBAC example: closure + multi-root reachable")
    void playgroundHierarchicalRbacExampleWithTransitiveClosure() {
        val rolesHierarchy = (ObjectValue) Value.ofJson("""
                {
                  "cso":                       ["security-manager", "it-operations-manager", "compliance-manager"],
                  "security-manager":          ["secops-analyst", "threat-hunter"],
                  "it-operations-manager":     ["site-reliability-engineer", "platform-admin"],
                  "compliance-manager":        ["internal-auditor", "risk-analyst"]
                }
                """);

        val closure        = (ObjectValue) GraphFunctionLibrary.transitiveClosure(rolesHierarchy);
        val subjectRoles   = Value.ofArray(Value.of("cso"), Value.of("market-analyst"));
        val effectiveRoles = (ArrayValue) GraphFunctionLibrary.reachable(closure, subjectRoles);

        assertThat(effectiveRoles).containsExactlyInAnyOrder(Value.of("cso"), Value.of("market-analyst"),
                Value.of("security-manager"), Value.of("it-operations-manager"), Value.of("compliance-manager"),
                Value.of("secops-analyst"), Value.of("threat-hunter"), Value.of("site-reliability-engineer"),
                Value.of("platform-admin"), Value.of("internal-auditor"), Value.of("risk-analyst"));
    }

    @Test
    @DisplayName("reachable on transitiveClosure with multiple roots returns union")
    void reachableOnClosureWhenMultipleRootsThenUnion() {
        val graph = (ObjectValue) Value.ofJson("""
                {
                  "cso":              ["security-manager"],
                  "security-manager": ["analyst"],
                  "analyst":          [],
                  "market-analyst":   []
                }
                """);

        val closure = (ObjectValue) GraphFunctionLibrary.transitiveClosure(graph);
        val roots   = Value.ofArray(Value.of("cso"), Value.of("market-analyst"));
        val result  = (ArrayValue) GraphFunctionLibrary.reachable(closure, roots);

        assertThat(result).containsExactlyInAnyOrder(Value.of("cso"), Value.of("security-manager"), Value.of("analyst"),
                Value.of("market-analyst"));
    }
}
