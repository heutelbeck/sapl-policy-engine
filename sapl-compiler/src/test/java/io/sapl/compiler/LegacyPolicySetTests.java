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

    // ========== Basic Policy Set Tests ==========

    @Test
    void setPermit() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" permit
                """, Decision.PERMIT);
    }

    @Test
    void setDeny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" deny
                """, Decision.DENY);
    }

    @Test
    void setNotApplicable_viaTargetMismatch() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" deny subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void noApplicablePolicies() {
        assertDecision("""
                set "tests" deny-overrides
                for true
                policy "testp" deny subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void setIndeterminate() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp" permit where "a" > 4;
                """, Decision.INDETERMINATE);
    }

    // ========== Deny-Overrides Algorithm Tests ==========

    @Test
    void denyOverridesPermitAndDeny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" deny
                """, Decision.DENY);
    }

    @Test
    void denyOverridesPermitAndNotApplicableAndDeny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" permit subject == "non-matching"
                policy "testp3" deny
                """, Decision.DENY);
    }

    @Test
    void denyOverridesPermitAndIndeterminateAndDeny() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" permit where "a" < 5;
                policy "testp3" deny
                """, Decision.DENY);
    }

    // ========== Import Tests ==========

    @Test
    void importsDuplicatesByPolicySetIgnored() {
        assertDecision("""
                import filter.replace
                import filter.replace
                set "tests" deny-overrides
                policy "testp1" permit where true;
                """, Decision.PERMIT);
    }

    @Test
    void importsInSetAvailableInPolicy() {
        assertDecision("""
                import simple.append
                set "tests" deny-overrides
                policy "testp1" permit where append("a", "b") == "ab";
                """, Decision.PERMIT);
    }

    // ========== Variable Scoping Tests ==========

    @Test
    void variablesOnSetLevel() {
        assertDecision("""
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit var1 == true
                """, Decision.PERMIT);
    }

    @Test
    void variablesOnSetLevelError() {
        assertDecision("""
                set "tests" deny-overrides
                policy "testp1" deny where var var1 = true / null; var1;
                """, Decision.INDETERMINATE);
    }

    @Test
    void variablesOverwriteInPolicy() {
        assertDecision("""
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit where var var2 = 10; var2 == 10;
                policy "testp2" deny where !(var1 == true);
                """, Decision.PERMIT);
    }

    @Test
    void shadowingSubjectWithNull() {
        assertDecision("""
                set "test" deny-overrides
                policy "test" deny where subject == null;
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void variablesInPolicyMustNotLeakIntoNextPolicy() {
        assertDecision("""
                set "test" deny-overrides
                var ps1 = true;
                policy "pol1" deny where var p1 = 10; p1 == 10;
                policy "pol2" permit where p1 == undefined;
                """, Decision.DENY);
    }

    // ========== Additional Combining Algorithm Tests ==========

    @Test
    void permitOverridesAlgorithm() {
        assertDecision("""
                set "test" permit-overrides
                policy "deny policy" deny
                policy "permit policy" permit
                """, Decision.PERMIT);
    }

    @Test
    void denyUnlessPermitAlgorithm() {
        assertDecision("""
                set "test" deny-unless-permit
                policy "not applicable" permit subject == "non-matching"
                """, Decision.DENY);
    }

    @Test
    void permitUnlessDenyAlgorithm() {
        assertDecision("""
                set "test" permit-unless-deny
                policy "not applicable" deny subject == "non-matching"
                """, Decision.PERMIT);
    }

    @Test
    void onlyOneApplicableAlgorithm() {
        assertDecision("""
                set "test" only-one-applicable
                policy "permit policy" permit
                policy "deny policy" deny
                """, Decision.INDETERMINATE);
    }

    @Test
    void firstApplicableAlgorithm() {
        assertDecision("""
                set "test" first-applicable
                policy "not applicable" permit subject == "non-matching"
                policy "permit policy" permit
                policy "deny policy" deny
                """, Decision.PERMIT);
    }

    // ========== Target Expression (for clause) Tests ==========

    @Test
    void targetExpressionTrue() {
        assertDecision("""
                set "test" deny-overrides
                for true
                policy "permit policy" permit
                """, Decision.PERMIT);
    }

    @Test
    void targetExpressionFalse_viaPolicyTarget() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit subject == "non-matching"
                """, Decision.NOT_APPLICABLE);
    }

    @Test
    void targetExpressionWithVariables() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit where var shouldApply = true; shouldApply;
                """, Decision.PERMIT);
    }

    @Test
    void targetExpressionError() {
        assertDecision("""
                set "test" deny-overrides
                policy "permit policy" permit where "invalid" > 5;
                """, Decision.INDETERMINATE);
    }

    // ========== Complex Policy Set Tests ==========

    @Test
    void multiplePoliciesWithMixedDecisions() {
        assertDecision("""
                set "test" deny-overrides
                policy "not applicable 1" permit subject == "non-matching"
                policy "permit policy" permit
                policy "not applicable 2" deny subject == "non-matching"
                """, Decision.PERMIT);
    }

    @Test
    void nestedConditionsInPolicies() {
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
    void policySetWithObligations() {
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
    void policySetWithAdvice() {
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
    void policySetWithTransformation() {
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
    void policySetWithMultipleTransformations_returnsIndeterminateOrDeny() {
        val result = evaluatePolicySet("""
                set "test" deny-overrides
                policy "permit with transform 1" permit transform { "version": 1 }
                policy "permit with transform 2" permit transform { "version": 2 }
                """);
        assertDecision(result, Decision.INDETERMINATE);
    }
}
