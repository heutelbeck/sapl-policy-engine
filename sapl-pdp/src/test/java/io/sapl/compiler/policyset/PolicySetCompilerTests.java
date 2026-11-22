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

import io.sapl.api.model.Value;
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
    @DisplayName("Policy set variable scope")
    class SetVariableScope {

        @Test
        @DisplayName("A set variable is visible to the target and every policy")
        void whenSetVariableIsUsedByTargetAndEveryPolicyThenAllPoliciesContribute() {
            val compiled = compilePolicySet("""
                    set "building"
                    unanimous or abstain
                    for open
                    var open = subject;
                    policy "lobby" permit open; obligation "lobby"
                    policy "office" permit open; obligation "office"
                    policy "archive" permit open; obligation "archive"
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": true, "action": "read", "resource": "building" }
                    """);
            val decision = evaluatePolicySet(compiled, ctx).authorizationDecision();

            assertThat(decision).satisfies(vote -> assertThat(vote.decision()).isEqualTo(Decision.PERMIT),
                    vote -> assertThat(List.copyOf(vote.obligations())).containsExactlyInAnyOrder(Value.of("lobby"),
                            Value.of("office"), Value.of("archive")));
        }

        @Test
        @DisplayName("The set target cannot read the last policy's local variable")
        void whenLastPolicyDefinesLocalThenSetTargetCannotReadIt() {
            val compiled = compilePolicySet("""
                    set "building" first or abstain
                    for open == true
                    policy "office" permit
                    var open = true;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "visitor", "action": "read", "resource": "building" }
                    """);

            assertThat(evaluatePolicySet(compiled, ctx).authorizationDecision().decision())
                    .isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("The set schema cannot read the last policy's local schema")
        void whenLastPolicyDefinesLocalSchemaThenSetSchemaCannotReadIt() {
            assertThatThrownBy(() -> compilePolicySet("""
                    subject enforced schema buildingSchema
                    set "building" first or abstain
                    policy "office" permit
                    var buildingSchema = {"type":"boolean"};
                    """)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Schema must be an object, got: UndefinedValue");
        }

    }

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

    @Nested
    @DisplayName("Target relative-accessor guard")
    class TargetRelativeAccessorGuard {

        @Test
        @DisplayName("a top-level relative accessor combined with a subscription reference is rejected")
        void whenTargetCombinesRelativeAccessorWithSubscriptionReferenceThenRejected() {
            assertThatThrownBy(() -> compilePolicySet("""
                    set "test"
                    first or abstain errors propagate
                    for @ == subject

                    policy "p"
                    permit
                    """)).isInstanceOf(SaplCompilerException.class).hasMessageContaining("@ or #");
        }

        @Test
        @DisplayName("a relative accessor bound inside a condition step is allowed")
        void whenTargetHasRelativeAccessorBoundInConditionStepThenCompiles() {
            val compiled = compilePolicySet("""
                    set "test"
                    first or abstain errors propagate
                    for subject.items[?(@ > 1)] != []

                    policy "p"
                    permit
                    """);

            assertThat(compiled).isNotNull();
        }
    }
}
