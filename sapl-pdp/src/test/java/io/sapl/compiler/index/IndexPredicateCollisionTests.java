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
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two distinct text constants, {@code "Aa"} and {@code "BB"}, collide under
 * {@link String#hashCode()}. If predicate identity in the index depends on that
 * 32-bit hash, the two equality targets collapse into one predicate and the
 * index reports the wrong applicable document. This validates that every index
 * strategy selects the document whose target actually holds, matching the naive
 * baseline.
 */
@DisplayName("Index predicate identity under constant hash collisions")
class IndexPredicateCollisionTests {

    private static final String POLICY_AA = """
            policy "permitAa"
            permit
                subject == "Aa";
            """;

    private static final String POLICY_BB = """
            policy "permitBb"
            permit
                subject == "BB";
            """;

    @ParameterizedTest(name = "{0} index selects only the matching document")
    @ValueSource(strings = { "NAIVE", "CANONICAL", "SMTDD" })
    void whenCollidingTextTargetsThenOnlyTheMatchingDocumentApplies(String strategy) {
        val compileCtx = SaplTesting.compilationContext();
        compileCtx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(strategy)).build());
        val documents = List.of(DocumentCompiler.compileDocument(POLICY_AA, compileCtx),
                DocumentCompiler.compileDocument(POLICY_BB, compileCtx));

        val index = IndexFactory.createIndex(documents, compileCtx);

        val result = index.match(SaplTesting.subscriptionContext("""
                { "subject": "BB", "action": "read", "resource": "data" }
                """));

        assertThat(result.matchingDocuments()).extracting(d -> d.metadata().name()).containsExactly("permitBb");
    }
}
