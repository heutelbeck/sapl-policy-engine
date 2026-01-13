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
package io.sapl.compiler.combining;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.CompiledPolicySet;
import io.sapl.compiler.pdp.PDPCompiler;
import io.sapl.compiler.policyset.PolicySetBody;
import io.sapl.compiler.policyset.PolicySetDecision;
import io.sapl.compiler.policyset.PurePolicySetBody;
import io.sapl.compiler.policyset.StreamPolicySetBody;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FirstApplicableCompiler covering short-circuit optimization,
 * pure evaluation, and streaming evaluation paths.
 */
@DisplayName("FirstApplicableCompiler")
class FirstApplicableCompilerTests {

    // --- Short-circuit tests (compile-time determined) ---

    @Test
    @DisplayName("Short-circuit: first policy with true target and static body returns immediate decision")
    void shortCircuit_firstPolicyStaticPermit() {
        val policySet = """
                set "guild-access" first-applicable
                policy "always-permit"
                permit

                policy "fallback-deny"
                deny
                """;

        val compiled = compilePolicySet(policySet);

        assertThat(compiled.policies()).as("Should short-circuit to PolicySetDecision")
                .isInstanceOf(PolicySetDecision.class);

        val decision = (PolicySetDecision) compiled.policies();
        assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
    }

    @Test
    @DisplayName("Short-circuit: first policy with false target skipped, second static policy applies")
    void shortCircuit_firstPolicyFalseTarget_secondApplies() {
        val policySet = """
                set "guild-access" first-applicable
                policy "never-matches"
                permit false

                policy "always-deny"
                deny
                """;

        val compiled = compilePolicySet(policySet);

        // First policy has false target (not applicable), second has true target +
        // static body
        // But shortCircuitIfPredetermined returns empty on first non-matching case
        // So this falls through to pure/stream evaluation
        assertThat(compiled.policies()).as("Falls through to pure evaluation since first policy doesn't short-circuit")
                .isInstanceOf(PurePolicySetBody.class);

        val result = evaluatePure(compiled, subscriptionContext());
        assertThat(result.decision()).isEqualTo(Decision.DENY);
    }

    // --- Pure evaluation tests ---

    @Test
    @DisplayName("Pure: policy set with runtime target matching evaluates first applicable")
    void pure_runtimeTargetMatching() {
        val policySet = """
                set "watch-duties" first-applicable
                policy "captain-only"
                permit subject == "Vimes"

                policy "sergeant-fallback"
                permit subject == "Colon"

                policy "default-deny"
                deny
                """;

        val compiled = compilePolicySet(policySet);

        assertThat(compiled.policies()).as("Should compile to pure body").isInstanceOf(PurePolicySetBody.class);

        // Test with Vimes - first policy matches
        val vimesCtx    = subscriptionContext(Value.of("Vimes"), Value.of("patrol"), Value.of("city"), Value.NULL);
        val vimesResult = evaluatePure(compiled, vimesCtx);
        assertThat(vimesResult.decision()).isEqualTo(Decision.PERMIT);

        // Test with Colon - second policy matches
        val colonCtx    = subscriptionContext(Value.of("Colon"), Value.of("patrol"), Value.of("city"), Value.NULL);
        val colonResult = evaluatePure(compiled, colonCtx);
        assertThat(colonResult.decision()).isEqualTo(Decision.PERMIT);

        // Test with Nobby - neither captain nor sergeant policy matches,
        // but default-deny has no target (implicitly true) so it applies
        val nobbyCtx    = subscriptionContext(Value.of("Nobby"), Value.of("patrol"), Value.of("city"), Value.NULL);
        val nobbyResult = evaluatePure(compiled, nobbyCtx);
        assertThat(nobbyResult.decision()).isEqualTo(Decision.DENY);
    }

    @Test
    @DisplayName("Pure: first matching policy with body condition determines result")
    void pure_bodyConditionEvaluation() {
        val policySet = """
                set "library-access" first-applicable
                policy "wizards-reading"
                permit subject == "Rincewind"
                where
                  action == "read";

                policy "default-deny"
                deny
                """;

        val compiled = compilePolicySet(policySet);
        assertThat(compiled.policies()).isInstanceOf(PurePolicySetBody.class);

        // Rincewind reading - target matches, body condition matches
        val readCtx    = subscriptionContext(Value.of("Rincewind"), Value.of("read"), Value.of("book"), Value.NULL);
        val readResult = evaluatePure(compiled, readCtx);
        assertThat(readResult.decision()).isEqualTo(Decision.PERMIT);

        // Rincewind running - target matches, body condition fails
        // Per XACML first-applicable: should continue to next policy (default-deny)
        val runCtx    = subscriptionContext(Value.of("Rincewind"), Value.of("run"), Value.of("away"), Value.NULL);
        val runResult = evaluatePure(compiled, runCtx);
        assertThat(runResult.decision()).isEqualTo(Decision.DENY);
    }

    // --- Streaming evaluation tests ---

    @Test
    @DisplayName("Stream: policy with attribute in body creates streaming evaluation")
    void stream_attributeInBodyCreatesStream() {
        val policySet = """
                set "time-based-access" first-applicable
                policy "time-check"
                permit
                where
                  <test.attr>;

                policy "fallback"
                deny
                """;

        val attrBroker = attributeBroker("test.attr", Value.TRUE);
        val compiled   = compilePolicySet(policySet, attrBroker);

        assertThat(compiled.policies()).as("Should compile to stream body due to attribute access")
                .isInstanceOf(StreamPolicySetBody.class);

        val streamBody   = (StreamPolicySetBody) compiled.policies();
        val subscription = new AuthorizationSubscription(Value.of("alice"), Value.of("read"), Value.of("doc"),
                Value.NULL);
        val ctx          = evaluationContext(subscription, attrBroker);

        StepVerifier.create(streamBody.stream().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
    }

    // --- Helper methods ---

    private static CompiledPolicySet compilePolicySet(String source) {
        val document = document(source);
        val compiled = PDPCompiler.compileDocument(document, compilationContext());
        assertThat(compiled).isInstanceOf(CompiledPolicySet.class);
        return (CompiledPolicySet) compiled;
    }

    private static CompiledPolicySet compilePolicySet(String source, AttributeBroker attrBroker) {
        val document = document(source);
        val compiled = PDPCompiler.compileDocument(document, compilationContext(attrBroker));
        assertThat(compiled).isInstanceOf(CompiledPolicySet.class);
        return (CompiledPolicySet) compiled;
    }

    private static PolicySetDecision evaluatePure(CompiledPolicySet compiled, EvaluationContext ctx) {
        val body = compiled.policies();
        assertThat(body).isInstanceOf(PurePolicySetBody.class);
        return ((PurePolicySetBody) body).evaluateBody(ctx);
    }

}
