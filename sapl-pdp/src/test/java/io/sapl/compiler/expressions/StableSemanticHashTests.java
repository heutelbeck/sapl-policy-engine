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
package io.sapl.compiler.expressions;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.ArrayCompiler.PureArray;
import io.sapl.compiler.expressions.FunctionCallCompiler.PureFunction;
import io.sapl.compiler.expressions.ObjectCompiler.PureObject;
import io.sapl.compiler.index.SemanticHashing;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.TEST_LOCATION;
import static io.sapl.util.SaplTesting.FUNCTION_BROKER;
import static io.sapl.util.SaplTesting.compileExpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The canonical order of commutative operands and the policy index rely on
 * semantic hashes that are the same in every run. These hashes are pinned to
 * fixed values, so a hash that reaches an identity hash of an enum constant, a
 * lambda or a class fails here.
 */
@DisplayName("Semantic hashes are the same in every run")
class StableSemanticHashTests {

    static Stream<Arguments> expressionsWithPinnedHashes() {
        return Stream.of(arguments("subject[(null)]", -764173734563211036L),
                arguments("subject[(undefined)]", -1892761000384017670L),
                arguments("subject |- { @[?(@.x == subject.y)] : filter.remove }", 2385289746521450151L),
                arguments("subject |- { @[(subject.i + 1)] : filter.remove }", -4546784658836758107L),
                arguments("subject |- { @.a..b[0]..[1][1:3][0,2][\"c\",\"d\"].*..* : filter.remove }",
                        -8529791330948343321L),
                arguments("[subject.a, null]", 903651302154579585L),
                arguments("{\"a\": subject.a, \"b\": undefined}", -8692575815329754311L),
                arguments("standard.length([subject.a, null])", 3310912069041310825L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expressionsWithPinnedHashes")
    @DisplayName("an expression hashes to the same value in every run")
    void whenExpressionIsCompiledThenSemanticHashIsTheSameInEveryRun(String source, long expected) {
        assertThat(compileExpression(source)).isInstanceOfSatisfying(PureOperator.class,
                operator -> assertThat(operator.semanticHash()).isEqualTo(expected));
    }

    @Test
    @DisplayName("filter paths that differ only in a path expression hash differently")
    void whenFilterPathsDifferInExpressionThenHashesDiffer() {
        val first  = (PureOperator) compileExpression("subject |- { @[?(@.x == subject.y)] : filter.remove }");
        val second = (PureOperator) compileExpression("subject |- { @[?(@.x == subject.z)] : filter.remove }");
        assertThat(first.semanticHash()).isNotEqualTo(second.semanticHash());
    }

    static Stream<Arguments> operatorsWithStreamingOperand() {
        val stream = (StreamOperator) compileExpression("<test.attr>");
        return Stream.of(
                arguments("array", new PureArray(List.of(Value.of(1), stream), TEST_LOCATION), -2389104228586902381L),
                arguments("object", new PureObject(new String[] { "a" }, List.of(stream), TEST_LOCATION),
                        -2472522856254264337L),
                arguments("function call", new PureFunction("standard.length", List.of(stream), TEST_LOCATION,
                        FUNCTION_BROKER.bindFunction("standard.length", 1)), -1938677148961586654L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operatorsWithStreamingOperand")
    @DisplayName("a streaming operand contributes its kind, the same in every run")
    void whenOperandIsStreamingThenSemanticHashIsTheSameInEveryRun(String description, PureOperator operator,
            long expected) {
        assertThat(operator.semanticHash()).isEqualTo(expected);
    }

    @Test
    @DisplayName("an operand hashes as a constant, a pure operator or the kind of a stream")
    void whenOperandIsHashedThenEachStratumHashesByItsStableContent() {
        val constant = Value.of("x");
        val pure     = (PureOperator) compileExpression("subject.a");
        val stream   = (CompiledExpression) compileExpression("<test.attr>");
        assertThat(new long[] { SemanticHashing.expressionHash(constant), SemanticHashing.expressionHash(pure),
                SemanticHashing.expressionHash(stream) }).containsExactly(SemanticHashing.valueHash(constant),
                        pure.semanticHash(), SemanticHashing.kindHash(stream.getClass()));
    }
}
