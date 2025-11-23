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

class OnlyOneApplicableTests {
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
                only-one-applicable

                policy "never matches 1"
                permit subject == "non-matching1"

                policy "never matches 2"
                deny subject == "non-matching2"
                """;
        assertDecision(evaluatePolicy(source), Decision.NOT_APPLICABLE);
    }

    @Test
    void singlePermit_returnsPermit() {
        val source = """
                set "test"
                only-one-applicable

                policy "permit policy"
                permit

                policy "never matches"
                deny subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }

    @Test
    void singleDeny_returnsDeny() {
        val source = """
                set "test"
                only-one-applicable

                policy "deny policy"
                deny

                policy "never matches"
                permit subject == "non-matching"
                """;
        assertDecision(evaluatePolicy(source), Decision.DENY);
    }

    @Test
    void multipleApplicable_returnsIndeterminate() {
        val source = """
                set "test"
                only-one-applicable

                policy "permit policy"
                permit

                policy "deny policy"
                deny
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void multiplePermits_returnsIndeterminate() {
        val source = """
                set "test"
                only-one-applicable

                policy "permit policy 1"
                permit

                policy "permit policy 2"
                permit
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void multipleDenies_returnsIndeterminate() {
        val source = """
                set "test"
                only-one-applicable

                policy "deny policy 1"
                deny

                policy "deny policy 2"
                deny
                """;
        assertDecision(evaluatePolicy(source), Decision.INDETERMINATE);
    }

    @Test
    void singleApplicablePermit_withNotApplicable_returnsPermit() {
        val source = """
                set "test"
                only-one-applicable

                policy "permit policy"
                permit

                policy "not applicable 1"
                deny subject == "non-matching1"

                policy "not applicable 2"
                permit subject == "non-matching2"
                """;
        assertDecision(evaluatePolicy(source), Decision.PERMIT);
    }
}
