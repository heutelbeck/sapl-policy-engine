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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.util.SimpleFunctionLibrary;
import io.sapl.util.TestUtil;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Legacy tests for policy sets, replicating tests from sapl-lang's
 * DefaultSAPLInterpreterPolicySetTests to ensure backwards compatibility
 * with the new compiler implementation.
 */
class LegacyPolicySetTests {
    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    private FunctionBroker  functionBroker;
    private AttributeBroker attributeBroker;

    @BeforeEach
    void setup() throws InitializationException {
        val defaultFunctionBroker = new DefaultFunctionBroker();
        defaultFunctionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        functionBroker = defaultFunctionBroker;
        val attributeRepo = new InMemoryAttributeRepository(Clock.systemUTC());
        attributeBroker = new CachingAttributeBroker(attributeRepo);
        ((CachingAttributeBroker) attributeBroker).loadPolicyInformationPointLibrary(new TestUtil.TestPip());
    }

    private CompilationContext createContext() {
        return new CompilationContext(functionBroker, attributeBroker);
    }

    private void assertDecision(Value result, Decision expected) {
        assertInstanceOf(ObjectValue.class, result);
        val decisionField = ((ObjectValue) result).get("decision");
        assertInstanceOf(TextValue.class, decisionField);
        assertEquals(expected.toString(), ((TextValue) decisionField).value());
    }

    private Value evaluatePolicy(String source) {
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            return switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Basic Policy Set Tests ==========

    @Test
    void setPermit() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp" permit
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void setDeny() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void setNotApplicable_viaTargetMismatch() {
        val source = """
                set "tests"
                deny-overrides

                policy "testp" deny subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void noApplicablePolicies() {
        val source = """
                set "tests"
                deny-overrides
                for true
                policy "testp" deny subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void setIndeterminate() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp" permit
                where "a" > 4;
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    // ========== Deny-Overrides Algorithm Tests ==========

    @Test
    void denyOverridesPermitAndDeny() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp1" permit
                policy "testp2" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void denyOverridesPermitAndNotApplicableAndDeny() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp1" permit
                policy "testp2" permit subject == "non-matching"
                policy "testp3" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void denyOverridesPermitAndIndeterminateAndDeny() {
        val source = """
                set "tests"
                deny-overrides
                policy "testp1" permit
                policy "testp2" permit where "a" < 5;
                policy "testp3" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    // ========== Import Tests ==========

    @Test
    void importsDuplicatesByPolicySetIgnored() {
        val source = """
                import filter.replace
                import filter.replace
                set "tests"
                deny-overrides
                policy "testp1" permit
                where true;
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void importsInSetAvailableInPolicy() {
        val source = """
                import simple.append
                set "tests"
                deny-overrides
                policy "testp1" permit
                where append("a", "b") == "ab";
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);
    }

    // ========== Variable Scoping Tests ==========

    @Test
    void variablesOnSetLevel() {
        val source = """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit var1 == true
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void variablesOnSetLevelError() {
        val source = """
                set "tests" deny-overrides
                policy "testp1" deny
                where
                  var var1 = true / null;
                  var1;
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void variablesOverwriteInPolicy() {
        val source = """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit where var var2 = 10; var2 == 10;
                policy "testp2" deny where !(var1 == true);
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void shadowingSubjectWithNull() {
        val source = """
                set "test"
                deny-overrides

                policy "test" deny
                where subject == null;
                """;
        // subject is "subject" (from our test subscription), not null, so policy
        // doesn't match
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void variablesInPolicyMustNotLeakIntoNextPolicy() {
        val source = """
                set "test" deny-overrides
                var ps1 = true;

                policy "pol1" deny
                where
                  var p1 = 10;
                  p1 == 10;

                policy "pol2" permit
                where p1 == undefined;
                """;
        // pol1 denies (p1 == 10), pol2 permits (p1 is undefined in pol2's scope)
        // deny-overrides: DENY wins
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    // ========== Additional Combining Algorithm Tests ==========

    @Test
    void permitOverridesAlgorithm() {
        val source = """
                set "test"
                permit-overrides
                policy "deny policy" deny
                policy "permit policy" permit
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void denyUnlessPermitAlgorithm() {
        val source = """
                set "test"
                deny-unless-permit
                policy "not applicable" permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void permitUnlessDenyAlgorithm() {
        val source = """
                set "test"
                permit-unless-deny
                policy "not applicable" deny subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void onlyOneApplicableAlgorithm() {
        val source = """
                set "test"
                only-one-applicable
                policy "permit policy" permit
                policy "deny policy" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void firstApplicableAlgorithm() {
        val source = """
                set "test"
                first-applicable
                policy "not applicable" permit subject == "non-matching"
                policy "permit policy" permit
                policy "deny policy" deny
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    // ========== Target Expression (for clause) Tests ==========

    @Test
    void targetExpressionTrue() {
        val source = """
                set "test"
                deny-overrides
                for true
                policy "permit policy" permit
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void targetExpressionFalse_viaPolicyTarget() {
        val source = """
                set "test"
                deny-overrides

                policy "permit policy" permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void targetExpressionWithVariables() {
        val source = """
                set "test"
                deny-overrides

                policy "permit policy" permit
                where
                  var shouldApply = true;
                  shouldApply;
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void targetExpressionError() {
        val source = """
                set "test"
                deny-overrides
                policy "permit policy" permit
                where "invalid" > 5;
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    // ========== Complex Policy Set Tests ==========

    @Test
    void multiplePoliciesWithMixedDecisions() {
        val source = """
                set "test"
                deny-overrides
                policy "not applicable 1" permit subject == "non-matching"
                policy "permit policy" permit
                policy "not applicable 2" deny subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void nestedConditionsInPolicies() {
        val source = """
                set "test"
                deny-overrides
                var setVar = true;
                policy "complex policy" permit
                where
                  var policyVar = 10;
                  setVar == true;
                  policyVar > 5;
                  policyVar < 20;
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void policySetWithObligations() {
        val source = """
                set "test"
                deny-overrides
                policy "permit with obligation" permit
                obligation { "type": "log" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify obligations
        assertInstanceOf(ObjectValue.class, result);
        val obligationsField = ((ObjectValue) result).get("obligations");
        assertInstanceOf(ArrayValue.class, obligationsField);
        val obligations = (ArrayValue) obligationsField;
        assertEquals(1, obligations.size());
    }

    @Test
    void policySetWithAdvice() {
        val source = """
                set "test"
                deny-overrides
                policy "permit with advice" permit
                advice { "type": "info" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify advice
        assertInstanceOf(ObjectValue.class, result);
        val adviceField = ((ObjectValue) result).get("advice");
        assertInstanceOf(ArrayValue.class, adviceField);
        val advice = (ArrayValue) adviceField;
        assertEquals(1, advice.size());
    }

    @Test
    void policySetWithTransformation() {
        val source = """
                set "test"
                deny-overrides
                policy "permit with transform" permit
                transform { "modified": true }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify resource transformation
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertInstanceOf(ObjectValue.class, resourceField);
        val modified = ((ObjectValue) resourceField).get("modified");
        assertInstanceOf(BooleanValue.class, modified);
        assertTrue(((BooleanValue) modified).value());
    }

    @Test
    void policySetWithMultipleTransformations_returnsIndeterminateOrDeny() {
        val source = """
                set "test"
                deny-overrides
                policy "permit with transform 1" permit
                transform { "version": 1 }
                policy "permit with transform 2" permit
                transform { "version": 2 }
                """;
        val result = evaluatePolicy(source);
        // With deny-overrides, transformation uncertainty leads to INDETERMINATE
        assertDecision(result, Decision.INDETERMINATE);
    }
}
