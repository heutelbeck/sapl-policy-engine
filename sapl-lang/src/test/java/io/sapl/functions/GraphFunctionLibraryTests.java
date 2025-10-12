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

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class GraphFunctionLibraryTests {

    @Test
    void reachable_includesMissingLeaves_andHandlesUnknownRoot() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "A": ["B"]
                }
                """);
        val initial = Val.ofJson("""
                ["A","C"]
                """);

        val result = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(result).extracting(JsonNode::asText).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void reachablePaths_buildsShortestPaths_fromSingleRoot() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "X": ["Y","W"],
                  "Y": ["Z"],
                  "Z": [],
                  "W": []
                }
                """);
        val initial = Val.ofJson("""
                "X"
                """);

        val pathsArray = GraphFunctionLibrary.reachablePaths(graph, initial).getArrayNode();

        val paths = new ArrayList<JsonNode>();
        pathsArray.forEach(paths::add);

        assertThat(paths).contains(Val.ofJson("""
                ["X"]
                """).get(), Val.ofJson("""
                ["X","W"]
                """).get(), Val.ofJson("""
                ["X","Y","Z"]
                """).get());
    }

    @Test
    void reachable_handlesCycles_withoutInfiniteLoop() throws JsonProcessingException {
        val graph   = Val.ofJson("""
                {
                  "A": ["B"],
                  "B": ["A","C"],
                  "C": []
                }
                """);
        val initial = Val.ofJson("""
                "A"
                """);

        val reachable = GraphFunctionLibrary.reachable(graph, initial).getArrayNode();

        assertThat(reachable).extracting(JsonNode::asText).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void walk_emitsPairs_forNestedObjectsAndArrays() throws JsonProcessingException {
        val input = Val.ofJson("""
                {
                  "a": { "b": 1 },
                  "c": [2, 3]
                }
                """);

        val pairsArray = GraphFunctionLibrary.walk(input).getArrayNode();

        val pairs = new ArrayList<JsonNode>();
        pairsArray.forEach(pairs::add);

        assertThat(pairs).contains(Val.ofJson("""
                [["a","b"],1]
                """).get(), Val.ofJson("""
                [["c",0],2]
                """).get(), Val.ofJson("""
                [["c",1],3]
                """).get());
    }
}
