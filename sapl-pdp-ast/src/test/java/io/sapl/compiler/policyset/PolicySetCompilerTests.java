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
package io.sapl.compiler.policyset;

import static io.sapl.util.SaplTesting.compilePolicySet;
import static io.sapl.util.SaplTesting.evaluatePolicySet;
import static io.sapl.util.SaplTesting.subscriptionContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.Decision;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;

/**
 * Tests for PolicySetCompiler covering variable compilation and combining
 * algorithm delegation.
 */
@DisplayName("PolicySetCompiler")
class PolicySetCompilerTests {

    @Nested
    @DisplayName("Variable definition errors")
    class VariableDefinitionErrors {

        @Test
        @DisplayName("duplicate variable definition throws SaplCompilerException")
        void duplicateVariableDefinitionThrows() {
            assertThatThrownBy(() -> compilePolicySet("""
                    set "test"
                    first-vote or abstain errors propagate

                    var myVar = 1;
                    var myVar = 2;

                    policy "test"
                    permit
                    """)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Redefinition of variable myVar");
        }

    }

    @Nested
    @DisplayName("first-vote or abstain errors propagate algorithm")
    class FirstApplicableAlgorithm {

        @Test
        @DisplayName("delegates to FirstApplicableCompiler")
        void delegatesToFirstApplicableCompiler() {
            val compiled = compilePolicySet("""
                    set "test"
                    first-vote or abstain errors propagate

                    policy "first"
                    permit

                    policy "second"
                    deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }
}
