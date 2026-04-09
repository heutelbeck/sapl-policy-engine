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

import java.util.List;
import java.util.stream.Stream;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
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

}
