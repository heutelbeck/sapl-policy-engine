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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.api.Test;

import static io.sapl.util.CombiningAlgorithmTestUtil.assertDecision;
import static io.sapl.util.CombiningAlgorithmTestUtil.evaluatePolicySet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy tests for policy sets, replicating tests from sapl-lang's
 * DefaultSAPLInterpreterPolicySetTests to ensure
 * backwards compatibility with the new compiler implementation.
 */
class LegacyPolicySetTests {

    // ========================================================================
    // Basic Policy Set Tests
    // ========================================================================

    @Test
    void when_policySetWithPermitPolicy_then_permit() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" permit
                """, Decision.PERMIT);
    }

    @Test
    void when_policySetWithDenyPolicy_then_deny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" deny
                """, Decision.DENY);
    }

    @Test
    void when_policySetWithTargetMismatch_then_notApplicable() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" deny subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void when_noApplicablePoliciesInSet_then_notApplicable() {
        assertDecision("""
                set "tests" deny-overrides
                for true
                policy "testp" deny subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void when_policySetWithError_then_indeterminate() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" permit where subject / 0 == 0;
                """, Decision.INDETERMINATE);
    }

    // ========================================================================
    // Deny-Overrides Algorithm Tests
    // ========================================================================

    @Test
    void when_denyOverridesWithPermitAndDeny_then_deny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" deny
                """, Decision.DENY);
    }

    @Test
    void when_denyOverridesWithPermitNotApplicableAndDeny_then_deny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" permit subject == "non-matching"
                policy "testp3" deny
                """, Decision.DENY);
    }

    @Test
    void when_denyOverridesWithPermitIndeterminateAndDeny_then_deny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" permit where subject / 0 == 0;
                policy "testp3" deny
                """, Decision.DENY);
    }

    // ========================================================================
    // Import Tests
    // ========================================================================

    @Test
    void when_duplicateImportsInPolicySet_then_ignored() {
        assertDecision("""
                import filter.replace
                import filter.replace
                set "tests" deny-overrides
                policy "testp1" permit where true;
                """, Decision.PERMIT);
    }

    @Test
    void when_importsInSet_then_availableInPolicy() {
        assertDecision("""
                import simple.append
                set "tests" deny-overrides
                policy "testp1" permit where append("a", "b") == "ab";
                """, Decision.PERMIT);
    }

    // ========================================================================
    // Variable Scoping Tests
    // ========================================================================

    @Test
    void when_variablesOnSetLevel_then_availableInPolicy() {
        assertDecision("""
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit var1 == true
                """, Decision.PERMIT);
    }

    @Test
    void when_variablesOnSetLevelWithError_then_indeterminate() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" deny where var var1 = subject / 0; var1 == 0;
                """, Decision.INDETERMINATE);
    }

    @Test
    void when_variablesOverwrittenInPolicy_then_policyVariableTakesPrecedence() {
        assertDecision("""
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit where var var2 = 10; var2 == 10;
                policy "testp2" deny where !(var1 == true);
                """, Decision.PERMIT);
    }

    @Test
    void when_shadowingSubjectWithNull_then_notApplicable() {
        assertDecision("""
                set "test" deny-overrides
                policy "test" deny where subject == null;
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void when_variablesInPolicy_then_mustNotLeakIntoNextPolicy() {
        assertDecision("""
                set "test" deny-overrides
                var ps1 = true;
                policy "pol1" deny where var p1 = 10; p1 == 10;
                policy "pol2" permit where p1 == undefined;
                """, Decision.DENY);
    }

    // ========================================================================
    // Additional Combining Algorithm Tests
    // ========================================================================

    @Test
    void when_permitOverridesAlgorithm_then_permitWins() {
        assertDecision("""
                set "test" permit-overrides
                policy "deny policy" deny
                policy "permit policy" permit
                """, Decision.PERMIT);
    }

    @Test
    void when_denyUnlessPermitAlgorithm_then_denyByDefault() {
        assertDecision("""
                set "test" deny-unless-permit
                policy "not applicable" permit subject == "non-matching"
                """, Decision.DENY);
    }

    @Test
    void when_permitUnlessDenyAlgorithm_then_permitByDefault() {
        assertDecision("""
                set "test" permit-unless-deny
                policy "not applicable" deny subject == "non-matching"
                """, Decision.PERMIT);
    }

    @Test
    void when_onlyOneApplicableAlgorithmWithMultipleApplicable_then_indeterminate() {
        assertDecision("""
                set "test" only-one-applicable
                policy "permit policy" permit
                policy "deny policy" deny
                """, Decision.INDETERMINATE);
    }

    @Test
    void when_firstApplicableAlgorithm_then_firstApplicablePolicyWins() {
        assertDecision("""
                set "test" first-applicable
                policy "not applicable" permit subject == "non-matching"
                policy "permit policy" permit
                policy "deny policy" deny
                """, Decision.PERMIT);
    }

    // ========================================================================
    // Target Expression (for clause) Tests
    // ========================================================================

    @Test
    void when_targetExpressionTrue_then_policySetApplies() {
        assertDecision("""
                set "test" deny-overrides
                for true
                policy "permit policy" permit
                """, Decision.PERMIT);
    }

    @Test
    void when_targetExpressionFalseViaPolicyTarget_then_notApplicable() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void when_targetExpressionWithVariables_then_variablesEvaluated() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit where var shouldApply = true; shouldApply;
                """, Decision.PERMIT);
    }

    @Test
    void when_targetExpressionWithError_then_indeterminate() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit where subject / 0 == 0;
                """, Decision.INDETERMINATE);
    }

    // ========================================================================
    // Complex Policy Set Tests
    // ========================================================================

    @Test
    void when_multiplePoliciesWithMixedDecisions_then_combiningAlgorithmApplied() {
        assertDecision("""
                set "test" deny-overrides
                policy "not applicable 1" permit subject == "non-matching"
                policy "permit policy" permit
                policy "not applicable 2" deny subject == "non-matching"
                """, Decision.PERMIT);
    }

    @Test
    void when_nestedConditionsInPolicies_then_allConditionsEvaluated() {
        assertDecision("""
                set "test" deny-overrides
                var setVar = true;
                policy "complex policy" permit
                where
                  var policyVar = 10;
                  setVar == true;
                  policyVar > 5;
                  policyVar < 20;
                """, Decision.PERMIT);
    }

    @Test
    void when_policySetWithObligations_then_obligationsIncluded() {
        val result = evaluatePolicySet("""
                set "test" deny-overrides
                policy "permit with obligation" permit obligation { "type": "log" }
                """);
        assertDecision(result, Decision.PERMIT);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObject = (ObjectValue) result;
        assertThat(resultObject).containsKey("obligations");
        assertThat(resultObject.get("obligations")).isInstanceOf(ArrayValue.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Value.class)).hasSize(1);
    }

    @Test
    void when_policySetWithAdvice_then_adviceIncluded() {
        val result = evaluatePolicySet("""
                set "test" deny-overrides
                policy "permit with advice" permit advice { "type": "info" }
                """);
        assertDecision(result, Decision.PERMIT);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObject = (ObjectValue) result;
        assertThat(resultObject).containsKey("advice");
        assertThat(resultObject.get("advice")).isInstanceOf(ArrayValue.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Value.class)).hasSize(1);
    }

    @Test
    void when_policySetWithTransformation_then_resourceTransformed() {
        val result = evaluatePolicySet("""
                set "test" deny-overrides
                policy "permit with transform" permit transform { "modified": true }
                """);
        assertDecision(result, Decision.PERMIT);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObject = (ObjectValue) result;
        assertThat(resultObject).containsKey("resource");
        assertThat(resultObject.get("resource")).isInstanceOf(ObjectValue.class);
        val resource = (ObjectValue) resultObject.get("resource");
        assertThat(resource).containsEntry("modified", Value.TRUE);
    }

    @Test
    void when_policySetWithMultipleTransformations_then_indeterminate() {
        val result = evaluatePolicySet("""
                set "test" deny-overrides
                policy "permit with transform 1" permit transform { "version": 1 }
                policy "permit with transform 2" permit transform { "version": 2 }
                """);
        assertDecision(result, Decision.INDETERMINATE);
    }
}
