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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DenyUnlessPermitTests {
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
        assertInstanceOf(ObjectValue.class, result, "Result should be an ObjectValue");
        val decisionField = ((ObjectValue) result).get("decision");
        assertInstanceOf(TextValue.class, decisionField, "Decision field should be a TextValue");
        assertEquals(expected.toString(), ((TextValue) decisionField).value(), "Decision should be " + expected);
    }

    private void assertObligations(Value result, List<String> expectedObligations) {
        assertInstanceOf(ObjectValue.class, result, "Result should be an ObjectValue");
        val obligationsField = ((ObjectValue) result).get("obligations");
        assertInstanceOf(ArrayValue.class, obligationsField, "Obligations field should be an ArrayValue");
        val obligations = (ArrayValue) obligationsField;
        assertEquals(expectedObligations.size(), obligations.size(), "Number of obligations should match");
        for (int i = 0; i < expectedObligations.size(); i++) {
            val obligation = obligations.get(i);
            assertInstanceOf(ObjectValue.class, obligation, "Obligation should be an ObjectValue");
            val typeField = ((ObjectValue) obligation).get("type");
            assertInstanceOf(TextValue.class, typeField, "Type field should be a TextValue");
            assertEquals(expectedObligations.get(i), ((TextValue) typeField).value(), "Obligation type should match");
        }
    }

    private void assertAdvice(Value result, List<String> expectedAdvice) {
        assertInstanceOf(ObjectValue.class, result, "Result should be an ObjectValue");
        val adviceField = ((ObjectValue) result).get("advice");
        assertInstanceOf(ArrayValue.class, adviceField, "Advice field should be an ArrayValue");
        val advice = (ArrayValue) adviceField;
        assertEquals(expectedAdvice.size(), advice.size(), "Number of advice should match");
        for (int i = 0; i < expectedAdvice.size(); i++) {
            val adviceItem = advice.get(i);
            assertInstanceOf(ObjectValue.class, adviceItem, "Advice should be an ObjectValue");
            val typeField = ((ObjectValue) adviceItem).get("type");
            assertInstanceOf(TextValue.class, typeField, "Type field should be a TextValue");
            assertEquals(expectedAdvice.get(i), ((TextValue) typeField).value(), "Advice type should match");
        }
    }

    private void assertResource(Value result, Value expectedResource) {
        assertInstanceOf(ObjectValue.class, result, "Result should be an ObjectValue");
        val resourceField = ((ObjectValue) result).get("resource");
        assertEquals(expectedResource, resourceField, "Resource should match");
    }

    // ========== Basic Scenarios ==========

    @Test
    void noPoliciesMatch_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "never matches"
                permit subject == "non-matching"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            // No matching policies should return DENY (default for deny-unless-permit)
            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void singlePolicyPermit_returnsPermit() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit policy"
                permit
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void singlePolicyDeny_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "deny policy"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void singlePolicyNotApplicable_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "not applicable"
                permit subject == "non-matching"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            // Policy doesn't match, should return DENY (default)
            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Algorithm Semantics ==========

    @Test
    void anyPermitWithoutUncertainty_returnsPermit() {
        val source = """
                set "test"
                deny-unless-permit

                policy "deny policy"
                deny

                policy "permit policy"
                permit

                policy "another deny"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void transformationUncertainty_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with transformation 1"
                permit
                transform "resource1"

                policy "permit with transformation 2"
                permit
                transform "resource2"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            // Transformation uncertainty should result in DENY
            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void allNotApplicable_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "not applicable 1"
                permit subject == "non-matching1"

                policy "not applicable 2"
                permit subject == "non-matching2"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            // No policies match, should return DENY (default)
            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void mixOfDenyAndNotApplicable_returnsDeny() {
        val source = """
                set "test"
                deny-unless-permit

                policy "deny policy"
                deny

                policy "not applicable"
                permit subject == "non-matching"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.DENY);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Obligation and Advice Tests ==========

    @Test
    void permitDecision_includesPermitObligations() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with obligation"
                permit
                obligation {"type": "log"}

                policy "deny with obligation"
                deny
                obligation {"type": "deny_log"}
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertObligations(result, List.of("log"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void denyDecision_includesDenyObligations() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with obligation"
                permit subject == "non-matching"
                obligation {"type": "permit_log"}

                policy "deny with obligation"
                deny
                obligation {"type": "deny_log"}
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.DENY);
            assertObligations(result, List.of("deny_log"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void multiplePolicies_collectsAllPermitObligations() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit 1"
                permit
                obligation {"type": "log1"}

                policy "permit 2"
                permit
                obligation {"type": "log2"}
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertObligations(result, List.of("log1", "log2"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void permitDecision_includesPermitAdvice() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with advice"
                permit
                advice {"type": "cache"}

                policy "deny with advice"
                deny
                advice {"type": "deny_advice"}
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertAdvice(result, List.of("cache"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Transformation Tests ==========

    @Test
    void singlePermitWithTransformation_returnsPermitWithResource() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with transformation"
                permit
                transform "modified_resource"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertResource(result, Value.of("modified_resource"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void permitWithoutTransformationAndPermitWithTransformation_usesTransformation() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit without transformation"
                permit

                policy "permit with transformation"
                permit
                transform "modified_resource"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertResource(result, Value.of("modified_resource"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Subscription Reference Tests ==========

    @Test
    void targetExpression_withSubscriptionFields() {
        val source = """
                set "test"
                deny-unless-permit

                policy "subject match"
                permit subject == "Alice"

                policy "fallback"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("Alice"), Value.of("read"), Value.of("document"),
                    Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void targetExpression_withObjectFieldAccess() {
        val source = """
                set "test"
                deny-unless-permit

                policy "object field match"
                permit subject.name == "Alice"

                policy "fallback"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(
                    ObjectValue.builder().put("name", Value.of("Alice")).build(), Value.of("read"),
                    Value.of("document"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void decisionExpression_withSubscriptionReference() {
        val source = """
                set "test"
                deny-unless-permit

                policy "transform based on subject"
                permit
                transform subject.name
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(
                    ObjectValue.builder().put("name", Value.of("Alice")).build(), Value.of("read"),
                    Value.of("document"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertResource(result, Value.of("Alice"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Streaming Tests ==========

    @Test
    void streamingAttribute_inDecisionExpression() {
        val source = """
                set "test"
                deny-unless-permit

                policy "streaming permit"
                permit
                where
                    (10).<test.counter> > 11;
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            assertInstanceOf(StreamExpression.class, decisionExpr,
                    "Should be StreamExpression due to streaming attribute");

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = ((StreamExpression) decisionExpr).stream()
                    .contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                    .blockFirst(Duration.ofSeconds(5));

            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void streamingAttribute_withDenyPolicy() {
        val source = """
                set "test"
                deny-unless-permit

                policy "streaming permit"
                permit
                where
                    (10).<test.counter> > 11;

                policy "deny policy"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            assertInstanceOf(StreamExpression.class, decisionExpr,
                    "Should be StreamExpression due to streaming attribute");

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = ((StreamExpression) decisionExpr).stream()
                    .contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                    .blockFirst(Duration.ofSeconds(5));

            // Should still be PERMIT since one policy permits
            assertDecision(result, Decision.PERMIT);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    // ========== Complex Scenarios ==========

    @Test
    void complexScenario_permitWithObligationsAdviceAndTransformation() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit with everything"
                permit
                obligation {"type": "log"}
                advice {"type": "cache"}
                transform "modified"

                policy "deny"
                deny
                obligation {"type": "deny_log"}
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            assertDecision(result, Decision.PERMIT);
            assertObligations(result, List.of("log"));
            assertAdvice(result, List.of("cache"));
            assertResource(result, Value.of("modified"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    @Test
    void complexScenario_multiplePermitsWithoutTransformationUncertainty() {
        val source = """
                set "test"
                deny-unless-permit

                policy "permit 1"
                permit
                obligation {"type": "log1"}

                policy "permit 2"
                permit
                advice {"type": "cache"}

                policy "permit 3 with transformation"
                permit
                transform "modified"
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"),
                    Value.of("resource"), Value.UNDEFINED);
            val evalContext  = new EvaluationContext("testConfig", "testSub", subscription, functionBroker,
                    attributeBroker);

            val result = switch (decisionExpr) {
            case Value value                       -> value;
            case PureExpression pureExpression     -> pureExpression.evaluate(evalContext);
            case StreamExpression streamExpression ->
                streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                        .blockFirst(Duration.ofSeconds(5));
            };

            // Only one transformation - no uncertainty
            assertDecision(result, Decision.PERMIT);
            assertObligations(result, List.of("log1"));
            assertAdvice(result, List.of("cache"));
            assertResource(result, Value.of("modified"));
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }
}
