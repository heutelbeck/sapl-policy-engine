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

import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
                    first or abstain errors propagate

                    var myVar = 1;
                    var myVar = 2;

                    policy "test"
                    permit
                    """)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Redefinition of variable myVar");
        }

    }

    @Nested
    @DisplayName("first or abstain errors propagate algorithm")
    class FirstApplicableAlgorithm {

        @Test
        @DisplayName("delegates to FirstApplicableCompiler")
        void delegatesToFirstApplicableCompiler() {
            val compiled = compilePolicySet("""
                    set "test"
                    first or abstain errors propagate

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

    @Nested
    @DisplayName("Set outcome metadata")
    class SetOutcomeMetadata {

        static Stream<Arguments> outcomeUnionCases() {
            return Stream.of(arguments("priority deny or abstain", List.of("permit", "permit"), Outcome.PERMIT),
                    arguments("priority deny or abstain", List.of("permit", "deny"), Outcome.PERMIT_OR_DENY),
                    arguments("priority deny or abstain", List.of("deny", "suspend"), Outcome.DENY_OR_SUSPEND),
                    arguments("priority deny or abstain", List.of("permit", "suspend"), Outcome.PERMIT_OR_SUSPEND),
                    arguments("priority deny or abstain", List.of("permit", "deny", "suspend"),
                            Outcome.PERMIT_OR_DENY_OR_SUSPEND),
                    arguments("priority permit or deny", List.of("permit"), Outcome.PERMIT_OR_DENY),
                    arguments("priority deny or suspend", List.of("deny"), Outcome.DENY_OR_SUSPEND));
        }

        @ParameterizedTest(name = "{0} with member effects {1} yields {2}")
        @MethodSource("outcomeUnionCases")
        void whenCompilingSetThenOutcomeIsUnionOfMemberEffectsAndDefaultDecision(String algorithm, List<String> effects,
                Outcome expected) {
            val source = new StringBuilder("set \"test\"\n").append(algorithm).append('\n');
            for (var i = 0; i < effects.size(); i++) {
                source.append("policy \"p").append(i).append("\" ").append(effects.get(i)).append('\n');
            }

            val compiled = compilePolicySet(source.toString());

            assertThat(compiled.metadata().outcome()).isEqualTo(expected);
        }
    }
}
