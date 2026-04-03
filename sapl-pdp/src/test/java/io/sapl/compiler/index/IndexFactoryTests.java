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

import io.sapl.api.pdp.CompilerFlags;
import io.sapl.api.pdp.IndexingStrategy;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.sapl.compiler.index.IndexTestFixtures.stubDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("IndexFactory")
class IndexFactoryTests {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource
    void whenStrategyThenCorrectImplementation(IndexingStrategy strategy, Class<?> expectedType) {
        val docs = List.of(stubDocument("p1"));
        val ctx  = SaplTesting.compilationContext();
        ctx.setCompilerFlags(new CompilerFlags(strategy, false, 10, 1.5, 10_000));
        val index = IndexFactory.createIndex(docs, ctx);
        assertThat(index).isInstanceOf(expectedType);
    }

    static Stream<Arguments> whenStrategyThenCorrectImplementation() {
        return Stream.of(arguments(IndexingStrategy.NAIVE, NaivePolicyIndex.class),
                arguments(IndexingStrategy.CANONICAL, CanonicalPolicyIndex.class),
                arguments(IndexingStrategy.AUTO, NaivePolicyIndex.class));
    }

}
