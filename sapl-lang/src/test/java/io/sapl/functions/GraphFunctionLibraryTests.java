/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
/*
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class GraphFunctionLibraryTests {

    @Test
    void reachable_includesMissingLeaves_andHandlesUnknownRoot() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "necronomicon": ["pnakotic-manuscripts"],
                  "pnakotic-manuscripts": ["rlyeh-text"]
                }
                """);
        val initial = Val.ofJson("""
                ["necronomicon","miskatonic-journal"]
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("necronomicon",
                "pnakotic-manuscripts", "rlyeh-text", "miskatonic-journal");
    }

    @Test
    void reachablePaths_buildsShortestPaths_fromSingleRoot() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "azathoth": ["nyarlathotep","shub-niggurath"],
                  "nyarlathotep": ["haunter-of-the-dark"],
                  "haunter-of-the-dark": [],
                  "shub-niggurath": ["dark-young"]
                }
                """);
        val initial = Val.ofJson("""
                "azathoth"
                """);

        val pathsArray = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        val paths = new ArrayList<JsonNode>();
        pathsArray.forEach(paths::add);

        assertThat(paths).contains(Val.ofJson("""
                ["azathoth"]
                """).get(), Val.ofJson("""
                ["azathoth","shub-niggurath","dark-young"]
                """).get(), Val.ofJson("""
                ["azathoth","nyarlathotep","haunter-of-the-dark"]
                """).get());
    }

    @Test
    void reachable_handlesCycles_withoutInfiniteLoop() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "necronomicon": ["book-of-eibon"],
                  "book-of-eibon": ["necronomicon","unaussprechlichen-kulten"],
                  "unaussprechlichen-kulten": []
                }
                """);
        val initial = Val.ofJson("""
                "necronomicon"
                """);

        val reachable = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(reachable).extracting(JsonNode::asText).containsExactlyInAnyOrder("necronomicon", "book-of-eibon",
                "unaussprechlichen-kulten");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            '{"celaeno-fragments":[]}'                   , '[]'                        , 0
            '{}'                                          , '["king-in-yellow"]'        , 1
            '{"pnakotic-manuscripts":["rlyeh-text"]}'    , '[]'                        , 0
            '{}'                                          , 'null'                      , 0
            '{"book-of-eibon":["testaments"]}'           , 'undefined'                 , 0
            """)
    void reachable_handlesEmptyInputs(String graphJson, String initialJson, int expectedSize)
            throws JsonProcessingException {
        val graph   = Val.ofJson(graphJson);
        val initial = "null".equals(initialJson) ? Val.NULL
                : "undefined".equals(initialJson) ? Val.UNDEFINED : Val.ofJson(initialJson);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).hasSize(expectedSize);
    }

    @Test
    void reachable_handlesNumericNodeIds() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "1": [2, 3],
                  "2": [4],
                  "3": [],
                  "4": []
                }
                """);
        val initial = Val.ofJson("1");

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("1", "2", "3", "4");
    }

    @Test
    void reachable_handlesBooleanNodeIds() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "true": ["false"],
                  "false": []
                }
                """);
        val initial = Val.ofJson("true");

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("true", "false");
    }

    @Test
    void reachable_ignoresNonArrayAdjacencyValues() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "de-vermis-mysteriis": ["cultes-des-goules"],
                  "cultes-des-goules": "corrupted-catalog-entry",
                  "book-of-eibon": 666,
                  "unaussprechlichen-kulten": true,
                  "celaeno-fragments": null
                }
                """);
        val initial = Val.ofJson("""
                ["de-vermis-mysteriis", "book-of-eibon", "unaussprechlichen-kulten", "celaeno-fragments"]
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("de-vermis-mysteriis",
                "cultes-des-goules", "book-of-eibon", "unaussprechlichen-kulten", "celaeno-fragments");
    }

    @Test
    void reachable_handlesNullNodesInAdjacencyLists() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "necronomicon": [null, "pnakotic-manuscripts"],
                  "pnakotic-manuscripts": []
                }
                """);
        val initial = Val.ofJson("""
                "necronomicon"
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("necronomicon", "null",
                "pnakotic-manuscripts");
    }

    @Test
    void reachable_handlesDisconnectedComponents() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "innsmouth-cult-texts": ["dagon-liturgy"],
                  "dagon-liturgy": [],
                  "arkham-lodge-records": ["yog-sothoth-rituals"],
                  "yog-sothoth-rituals": []
                }
                """);
        val initial = Val.ofJson("""
                "innsmouth-cult-texts"
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("innsmouth-cult-texts", "dagon-liturgy")
                .doesNotContain("arkham-lodge-records", "yog-sothoth-rituals");
    }

    @Test
    void reachable_handlesSelfLoops() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "de-vermis-mysteriis": ["de-vermis-mysteriis", "cultes-des-goules"],
                  "cultes-des-goules": []
                }
                """);
        val initial = Val.ofJson("""
                "de-vermis-mysteriis"
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("de-vermis-mysteriis",
                "cultes-des-goules");
    }

    @Test
    void reachablePaths_handlesMultipleRoots() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "pnakotic-manuscripts": ["rlyeh-text"],
                  "rlyeh-text": [],
                  "book-of-eibon": ["testaments-of-carnamagos"],
                  "testaments-of-carnamagos": []
                }
                """);
        val initial = Val.ofJson("""
                ["pnakotic-manuscripts", "book-of-eibon"]
                """);

        val pathsArray = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        val paths = new ArrayList<JsonNode>();
        pathsArray.forEach(paths::add);

        assertThat(paths).contains(Val.ofJson("""
                ["pnakotic-manuscripts"]
                """).get(), Val.ofJson("""
                ["pnakotic-manuscripts","rlyeh-text"]
                """).get(), Val.ofJson("""
                ["book-of-eibon"]
                """).get(), Val.ofJson("""
                ["book-of-eibon","testaments-of-carnamagos"]
                """).get());
    }

    @Test
    void reachablePaths_handlesCycles_returnsShortestPath() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "miskatonic-library-catalog": ["restricted-section"],
                  "restricted-section": ["miskatonic-library-catalog", "necronomicon-translation"],
                  "necronomicon-translation": []
                }
                """);
        val initial = Val.ofJson("""
                "miskatonic-library-catalog"
                """);

        val pathsArray = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        val paths = new ArrayList<JsonNode>();
        pathsArray.forEach(paths::add);

        assertThat(paths).contains(Val.ofJson("""
                ["miskatonic-library-catalog"]
                """).get(), Val.ofJson("""
                ["miskatonic-library-catalog","restricted-section"]
                """).get(), Val.ofJson("""
                ["miskatonic-library-catalog","restricted-section","necronomicon-translation"]
                """).get());
    }

    @Test
    void reachablePaths_ignoresNonArrayAdjacencies() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "r-lyeh-text": ["sussex-manuscript"],
                  "sussex-manuscript": "damaged-catalog-entry"
                }
                """);
        val initial = Val.ofJson("""
                "r-lyeh-text"
                """);

        val pathsArray = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        val paths = new ArrayList<JsonNode>();
        pathsArray.forEach(paths::add);

        assertThat(paths).contains(Val.ofJson("""
                ["r-lyeh-text"]
                """).get(), Val.ofJson("""
                ["r-lyeh-text","sussex-manuscript"]
                """).get()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            '{"sign-of-koth":[]}'            , '[]'                          , 0
            '{}'                             , '["nyarlathotep-testament"]'  , 1
            """)
    void reachablePaths_handlesEmptyInputs(String graphJson, String initialJson, int expectedPaths)
            throws JsonProcessingException {
        val graph   = Val.ofJson(graphJson);
        val initial = Val.ofJson(initialJson);

        val result = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        assertThat(result).hasSize(expectedPaths);
    }
}
