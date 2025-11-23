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

class PermitOverridesTests {
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

    @Test
    void noPoliciesMatch_returnsNotApplicable() {
        val source = """
                set "test"
                permit-overrides

                policy "never matches"
                permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void singlePermit_returnsPermit() {
        val source = """
                set "test"
                permit-overrides

                policy "permit policy"
                permit
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void singleDeny_returnsDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny policy"
                deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void permitOverridesDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny policy"
                deny

                policy "permit policy"
                permit
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void transformationUncertainty_returnsIndeterminate() {
        val source = """
                set "test"
                permit-overrides

                policy "permit with transformation 1"
                permit
                transform "resource1"

                policy "permit with transformation 2"
                permit
                transform "resource2"
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void indeterminateWithoutPermit_returnsIndeterminate() {
        val source = """
                set "test"
                permit-overrides

                policy "permit policy 1"
                permit
                transform "resource1"

                policy "permit policy 2"
                permit
                transform "resource2"

                policy "deny policy"
                deny
                """;
        // Transformation uncertainty -> INDETERMINATE
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void onlyDeny_returnsDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny policy 1"
                deny

                policy "deny policy 2"
                deny
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void onlyNotApplicable_returnsNotApplicable() {
        val source = """
                set "test"
                permit-overrides

                policy "not applicable 1"
                permit subject == "non-matching1"

                policy "not applicable 2"
                deny subject == "non-matching2"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    // ========== Additional Tests from Legacy Implementation ==========

    @Test
    void permitWithIndeterminate_returnsPermit() {
        val source = """
                set "test"
                permit-overrides

                policy "permit policy" permit

                policy "indeterminate policy" permit
                where "a" > 5;
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void denyIndeterminateNotApplicable_returnsIndeterminate() {
        val source = """
                set "test"
                permit-overrides

                policy "deny policy" deny

                policy "indeterminate policy" permit
                where "a" < 5;

                policy "not applicable policy" permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void denyNotApplicable_returnsDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny policy" deny

                policy "not applicable" permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void singlePermitTransformationResource_verifiesResource() {
        val source = """
                set "test"
                permit-overrides

                policy "testp" permit
                transform { "value": true }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify resource transformation
        assertInstanceOf(ObjectValue.class, result);
        val resourceField = ((ObjectValue) result).get("resource");
        assertInstanceOf(ObjectValue.class, resourceField);
        val valueField = ((ObjectValue) resourceField).get("value");
        assertInstanceOf(BooleanValue.class, valueField);
        assertTrue(((BooleanValue) valueField).value());
    }

    @Test
    void collectObligationsFromDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny 1" deny
                obligation { "type": "obligation1" }
                advice { "type": "advice1" }

                policy "deny 2" deny
                obligation { "type": "obligation2" }
                advice { "type": "advice2" }

                policy "not applicable permit" permit subject == "non-matching"
                obligation { "type": "obligation3" }
                advice { "type": "advice3" }

                policy "not applicable deny" deny subject == "non-matching"
                obligation { "type": "obligation4" }
                advice { "type": "advice4" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.DENY);

        // Verify obligations from DENY policies only
        assertInstanceOf(ObjectValue.class, result);
        val obligationsField = ((ObjectValue) result).get("obligations");
        assertInstanceOf(ArrayValue.class, obligationsField);
        val obligations = (ArrayValue) obligationsField;
        assertEquals(2, obligations.size());

        val obl1 = ((ObjectValue) obligations.get(0)).get("type");
        assertEquals("obligation1", ((TextValue) obl1).value());
        val obl2 = ((ObjectValue) obligations.get(1)).get("type");
        assertEquals("obligation2", ((TextValue) obl2).value());
    }

    @Test
    void collectAdviceFromDeny() {
        val source = """
                set "test"
                permit-overrides

                policy "deny 1" deny
                obligation { "type": "obligation1" }
                advice { "type": "advice1" }

                policy "deny 2" deny
                obligation { "type": "obligation2" }
                advice { "type": "advice2" }

                policy "not applicable permit" permit subject == "non-matching"
                obligation { "type": "obligation3" }
                advice { "type": "advice3" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.DENY);

        // Verify advice from DENY policies only
        assertInstanceOf(ObjectValue.class, result);
        val adviceField = ((ObjectValue) result).get("advice");
        assertInstanceOf(ArrayValue.class, adviceField);
        val advice = (ArrayValue) adviceField;
        assertEquals(2, advice.size());

        val adv1 = ((ObjectValue) advice.get(0)).get("type");
        assertEquals("advice1", ((TextValue) adv1).value());
        val adv2 = ((ObjectValue) advice.get(1)).get("type");
        assertEquals("advice2", ((TextValue) adv2).value());
    }

    @Test
    void collectObligationsFromPermit() {
        val source = """
                set "test"
                permit-overrides

                policy "permit 1" permit
                obligation { "type": "obligation1" }
                advice { "type": "advice1" }

                policy "permit 2" permit
                obligation { "type": "obligation2" }
                advice { "type": "advice2" }

                policy "not applicable deny" deny subject == "non-matching"
                obligation { "type": "obligation3" }
                advice { "type": "advice3" }

                policy "not applicable permit" deny
                where false;
                obligation { "type": "obligation4" }
                advice { "type": "advice4" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify obligations from PERMIT policies only
        assertInstanceOf(ObjectValue.class, result);
        val obligationsField = ((ObjectValue) result).get("obligations");
        assertInstanceOf(ArrayValue.class, obligationsField);
        val obligations = (ArrayValue) obligationsField;
        assertEquals(2, obligations.size());

        val obl1 = ((ObjectValue) obligations.get(0)).get("type");
        assertEquals("obligation1", ((TextValue) obl1).value());
        val obl2 = ((ObjectValue) obligations.get(1)).get("type");
        assertEquals("obligation2", ((TextValue) obl2).value());
    }

    @Test
    void collectAdviceFromPermit() {
        val source = """
                set "test"
                permit-overrides

                policy "permit 1" permit
                obligation { "type": "obligation1" }
                advice { "type": "advice1" }

                policy "permit 2" permit
                obligation { "type": "obligation2" }
                advice { "type": "advice2" }

                policy "not applicable" deny subject == "non-matching"
                obligation { "type": "obligation3" }
                advice { "type": "advice3" }
                """;
        val result = evaluatePolicy(source);
        assertDecision(result, Decision.PERMIT);

        // Verify advice from PERMIT policies only
        assertInstanceOf(ObjectValue.class, result);
        val adviceField = ((ObjectValue) result).get("advice");
        assertInstanceOf(ArrayValue.class, adviceField);
        val advice = (ArrayValue) adviceField;
        assertEquals(2, advice.size());

        val adv1 = ((ObjectValue) advice.get(0)).get("type");
        assertEquals("advice1", ((TextValue) adv1).value());
        val adv2 = ((ObjectValue) advice.get(1)).get("type");
        assertEquals("advice2", ((TextValue) adv2).value());
    }
}
