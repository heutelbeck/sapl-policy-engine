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
package io.sapl.compiler.index;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.naive.NaivePolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.compiler.index.IndexTestFixtures.andOfOrs;
import static io.sapl.compiler.index.IndexTestFixtures.documentWithApplicability;
import static io.sapl.compiler.index.IndexTestFixtures.stubDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("IndexFactory")
class IndexFactoryTests {

    @MethodSource
    @ParameterizedTest(name = "{0} -> {1}")
    void whenStrategyThenCorrectImplementation(String strategy, Class<?> expectedType) {
        val docs = List.of(stubDocument("p1"));
        val ctx  = SaplTesting.compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(strategy)).build());
        val index = IndexFactory.createIndex(docs, ctx);
        assertThat(index).isInstanceOf(expectedType);
    }

    static Stream<Arguments> whenStrategyThenCorrectImplementation() {
        return Stream.of(arguments("NAIVE", NaivePolicyIndex.class), arguments("CANONICAL", CanonicalPolicyIndex.class),
                arguments("SMTDD", SmtddPolicyIndex.class), arguments("AUTO", NaivePolicyIndex.class));
    }

    @Test
    @DisplayName("unknown strategy throws")
    void whenUnknownStrategyThenThrows() {
        val docs = List.of(stubDocument("p1"));
        val ctx  = SaplTesting.compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of("BOGUS")).build());
        assertThatThrownBy(() -> IndexFactory.createIndex(docs, ctx)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    @DisplayName("explicit CANONICAL degrades to the naive index when an applicability exceeds the DNF clause limit")
    void whenCanonicalApplicabilityExceedsClauseLimitThenFallsBackToNaive() {
        val docs = List.of(documentWithApplicability("p1", andOfOrs(20)));
        val ctx  = SaplTesting.compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of("CANONICAL")).build());

        val index = IndexFactory.createIndex(docs, ctx);

        assertThat(index).isInstanceOf(NaivePolicyIndex.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("an index with more nodes than maxIndexNodes is refused, whatever kinds of nodes it is built from")
    void whenIndexExceedsNodeLimitThenSmtddIsRefused(String description, List<String> conditions) {
        val documents = policies(conditions);

        assertThatThrownBy(() -> SmtddPolicyIndex.create(documents, 1))
                .isInstanceOf(IndexSizeLimitExceededException.class);
    }

    static Stream<Arguments> whenIndexExceedsNodeLimitThenSmtddIsRefused() {
        return Stream.of(arguments("two comparisons", List.of("subject > 1", "subject > 2")),
                arguments("two patterns on one operand", List.of("subject =~ \"a\"", "subject =~ \"b\"")),
                arguments("two equalities on one operand", List.of("subject == \"a\"", "subject == \"b\"")),
                arguments("two memberships on one operand",
                        List.of("subject in [\"a\", \"b\"]", "subject in [\"c\", \"d\"]")),
                arguments("one comparison", List.of("subject > 1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("when the SMTDD index exceeds maxIndexNodes the configured fallback takes over")
    void whenSmtddExceedsNodeLimitThenFallbackIndexIsUsed(String strategy, Class<?> expectedType) {
        val documents = policies(List.of("subject =~ \"a\"", "subject =~ \"b\""));
        val ctx       = SaplTesting.compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(strategy))
                .put("maxIndexNodes", Value.of(1)).put("minPoliciesForIndexing", Value.of(1)).build());

        assertThat(IndexFactory.createIndex(documents, ctx)).isInstanceOf(expectedType);
    }

    static Stream<Arguments> whenSmtddExceedsNodeLimitThenFallbackIndexIsUsed() {
        return Stream.of(arguments("SMTDD", NaivePolicyIndex.class), arguments("AUTO", CanonicalPolicyIndex.class));
    }

    private static List<CompiledDocument> policies(List<String> conditions) {
        return conditions.stream().<CompiledDocument>map(condition -> SaplTesting
                .compilePolicyFull("policy \"%s\" permit %s;".formatted(condition.replace('"', '\''), condition)))
                .toList();
    }

}
