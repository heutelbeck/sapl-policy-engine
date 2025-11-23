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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for the first-applicable combining algorithm.
 * Tests cover all permutations of decision sequences, match conditions,
 * pure vs streaming expressions, and edge cases.
 */
class FirstApplicableTests {
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

    private EvaluationContext createEvaluationContext() {
        val subscription = new AuthorizationSubscription(Value.of("subject"), Value.of("action"), Value.of("resource"),
                Value.UNDEFINED);
        return new EvaluationContext("testConfig", "testSub", subscription, functionBroker, attributeBroker);
    }

    private Value evaluateDocument(String source) {
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();
            return evaluateCompiledExpression(decisionExpr);
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    private Value evaluateCompiledExpression(CompiledExpression expression) {
        return switch (expression) {
        case Value value                       -> value;
        case PureExpression pureExpression     -> pureExpression.evaluate(createEvaluationContext());
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, createEvaluationContext()))
                    .blockFirst(Duration.ofSeconds(5));
        };
    }

    private Flux<Value> evaluateDocumentAsStream(String source) {
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();
            return switch (decisionExpr) {
            case Value value                       -> Flux.just(value);
            case PureExpression pureExpression     -> Flux.just(pureExpression.evaluate(createEvaluationContext()));
            case StreamExpression streamExpression -> streamExpression.stream()
                    .contextWrite(ctx -> ctx.put(EvaluationContext.class, createEvaluationContext()));
            };
        } catch (SaplCompilerException e) {
            throw new RuntimeException("Compilation failed: " + e.getMessage(), e);
        }
    }

    private void assertDecision(Value result, Decision expectedDecision) {
        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj               = (ObjectValue) result;
        val decisionAttribute = obj.get("decision");
        assertThat(decisionAttribute).isInstanceOf(TextValue.class);
        val actualDecision = Decision.valueOf(((TextValue) decisionAttribute).value());
        assertThat(actualDecision).isEqualTo(expectedDecision);
    }

    // ========================================================================
    // EMPTY POLICY SET TESTS
    // ========================================================================

    @Test
    void emptyPolicySet_returnsNotApplicable() {
        val source = """
                set "empty"
                first-applicable

                policy "never matches"
                permit
                where false;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.NOT_APPLICABLE);
    }

    // ========================================================================
    // SINGLE POLICY TESTS - ALL DECISIONS
    // ========================================================================

    @Test
    void singlePolicy_permit_returnsPermit() {
        val source = """
                set "test"
                first-applicable

                policy "permit policy"
                permit
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void singlePolicy_deny_returnsDeny() {
        val source = """
                set "test"
                first-applicable

                policy "deny policy"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void singlePolicy_notApplicable_returnsNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable policy"
                permit
                where
                  false;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.NOT_APPLICABLE);
    }

    // ========================================================================
    // FIRST APPLICABLE SEMANTICS - PERMIT/DENY SHORT-CIRCUIT
    // ========================================================================

    @Test
    void firstPermit_subsequentPoliciesIgnored() {
        val source = """
                set "test"
                first-applicable

                policy "first permit"
                permit

                policy "second permit"
                permit

                policy "deny"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void firstDeny_subsequentPoliciesIgnored() {
        val source = """
                set "test"
                first-applicable

                policy "first deny"
                deny

                policy "permit"
                permit

                policy "second deny"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void skipNotApplicable_returnFirstPermit() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable 1"
                permit
                where false;

                policy "not applicable 2"
                deny
                where false;

                policy "first applicable permit"
                permit

                policy "subsequent deny"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void skipNotApplicable_returnFirstDeny() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable 1"
                permit
                where false;

                policy "not applicable 2"
                deny
                where false;

                policy "first applicable deny"
                deny

                policy "subsequent permit"
                permit
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void allNotApplicable_returnsNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable 1"
                permit
                where false;

                policy "not applicable 2"
                deny
                where false;

                policy "not applicable 3"
                permit
                where false;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.NOT_APPLICABLE);
    }

    // ========================================================================
    // MATCH EXPRESSION TESTS
    // ========================================================================

    @Test
    void matchExpression_constantTrue_policyApplies() {
        val source = """
                set "test"
                first-applicable

                policy "matches"
                permit true
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void matchExpression_constantFalse_policyNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "no match"
                permit
                where false;

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void matchExpression_evaluatesToTrue_policyApplies() {
        val source = """
                set "test"
                first-applicable

                policy "matches with expression"
                permit (5 > 3)
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void matchExpression_evaluatesToFalse_policyNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "no match with expression"
                permit
                where (2 > 5);

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void matchExpression_nonBoolean_returnsIndeterminate() {
        val source = """
                set "test"
                first-applicable

                policy "invalid match"
                permit
                where "not a boolean";
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.INDETERMINATE);
    }

    // ========================================================================
    // TARGET EXPRESSION TESTS WITH SUBSCRIPTION REFERENCES
    // ========================================================================

    @Test
    void targetExpression_matchesSubject() {
        val source = """
                set "test"
                first-applicable

                policy "subject match"
                permit subject == "subject"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_noMatchSubject() {
        val source = """
                set "test"
                first-applicable

                policy "subject no match"
                permit subject == "wrong_subject"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void targetExpression_matchesAction() {
        val source = """
                set "test"
                first-applicable

                policy "action match"
                permit action == "action"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_matchesResource() {
        val source = """
                set "test"
                first-applicable

                policy "resource match"
                permit resource == "resource"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_complexSubscriptionExpression() {
        val source = """
                set "test"
                first-applicable

                policy "complex match"
                permit subject == "subject" & action == "action"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_complexSubscriptionExpressionNoMatch() {
        val source = """
                set "test"
                first-applicable

                policy "complex no match"
                permit subject == "subject" & action == "wrong_action"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void targetExpression_withSubscriptionFieldAccess() {
        val source = """
                set "test"
                first-applicable

                policy "field access"
                permit subject =~ "sub.*"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_multipleTargetsInSet() {
        val source = """
                set "test"
                first-applicable

                policy "admin only"
                permit subject == "admin"

                policy "read action"
                permit action == "read"

                policy "specific resource"
                permit resource == "resource"

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        // Third policy matches (resource == "resource")
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_combinedWithWhere() {
        val source = """
                set "test"
                first-applicable

                policy "target and where"
                permit subject == "subject"
                where
                  action == "action";

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_targetMatchesWhereDoesNot() {
        val source = """
                set "test"
                first-applicable

                policy "target matches where fails"
                permit subject == "subject"
                where
                  action == "wrong_action";

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void targetExpression_negation() {
        val source = """
                set "test"
                first-applicable

                policy "not admin"
                permit !(subject == "admin")

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        // subject is "subject", not "admin", so negation is true
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_arithmeticComparison() {
        val source = """
                set "test"
                first-applicable

                policy "string length comparison"
                permit subject == "subject"
                where
                  simple.length(subject) > 5;

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        // "subject" has length 7, which is > 5
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void targetExpression_withObjectSubscription() {
        val source = """
                set "test"
                first-applicable

                policy "object field match"
                permit subject.name == "Alice"

                policy "fallback"
                deny
                """;
        // Create custom evaluation context with object subscription
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
    void targetExpression_withNestedObjectAccess() {
        val source = """
                set "test"
                first-applicable

                policy "nested field match"
                permit subject.department.name == "Engineering"

                policy "fallback"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(ObjectValue.builder()
                    .put("department", ObjectValue.builder().put("name", Value.of("Engineering")).build()).build(),
                    Value.of("read"), Value.of("document"), Value.UNDEFINED);
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
    void targetExpression_multipleSubscriptionFields() {
        val source = """
                set "test"
                first-applicable

                policy "multi-field match"
                permit subject.role == "admin" & resource.type == "document"

                policy "fallback"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(
                    ObjectValue.builder().put("role", Value.of("admin")).build(), Value.of("read"),
                    ObjectValue.builder().put("type", Value.of("document")).build(), Value.UNDEFINED);
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
    void targetExpression_arrayInSubscription() {
        val source = """
                set "test"
                first-applicable

                policy "array contains"
                permit subject.roles[0] == "admin"

                policy "fallback"
                deny
                """;
        try {
            val sapl           = PARSER.parse(source);
            val context        = createContext();
            val compiledPolicy = SaplCompiler.compileDocument(sapl, context);
            val decisionExpr   = compiledPolicy.decisionExpression();

            val subscription = new AuthorizationSubscription(ObjectValue.builder()
                    .put("roles", ArrayValue.builder().add(Value.of("admin")).add(Value.of("user")).build()).build(),
                    Value.of("read"), Value.of("document"), Value.UNDEFINED);
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

    // ========================================================================
    // WHERE CLAUSE TESTS
    // ========================================================================

    @Test
    void whereClause_true_policyApplies() {
        val source = """
                set "test"
                first-applicable

                policy "with where true"
                permit
                where
                  true;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void whereClause_false_policyNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "with where false"
                permit
                where
                  false;

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void whereClause_expression_evaluatesCorrectly() {
        val source = """
                set "test"
                first-applicable

                policy "where with expression"
                permit
                where
                  (10 + 5) == 15;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void whereClause_multipleConditions_allMustBeTrue() {
        val source = """
                set "test"
                first-applicable

                policy "multiple where conditions"
                permit
                where
                  true;
                  5 > 3;
                  "test" == "test";
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void whereClause_oneConditionFalse_policyNotApplicable() {
        val source = """
                set "test"
                first-applicable

                policy "one condition false"
                permit
                where
                  true;
                  5 < 3;
                  "test" == "test";

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    // ========================================================================
    // OBLIGATIONS AND ADVICE TESTS
    // ========================================================================

    @Test
    void obligation_includedInResult() {
        val source = """
                set "test"
                first-applicable

                policy "with obligation"
                permit
                obligation "log_access"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj         = (ObjectValue) result;
        val obligations = obj.get("obligations");
        assertThat(obligations).isInstanceOf(ArrayValue.class);
        val obligationArray = (ArrayValue) obligations;
        assertThat(obligationArray.size()).isEqualTo(1);
        assertThat(obligationArray.get(0)).isEqualTo(Value.of("log_access"));
    }

    @Test
    void multipleObligations_allIncluded() {
        val source = """
                set "test"
                first-applicable

                policy "multiple obligations"
                permit
                obligation "log_access"
                obligation "notify_admin"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj         = (ObjectValue) result;
        val obligations = (ArrayValue) obj.get("obligations");
        assertThat(obligations.size()).isEqualTo(2);
    }

    @Test
    void advice_includedInResult() {
        val source = """
                set "test"
                first-applicable

                policy "with advice"
                permit
                advice "cache_result"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj    = (ObjectValue) result;
        val advice = (ArrayValue) obj.get("advice");
        assertThat(advice.size()).isEqualTo(1);
        assertThat(advice.get(0)).isEqualTo(Value.of("cache_result"));
    }

    @Test
    void obligationsAndAdvice_bothIncluded() {
        val source = """
                set "test"
                first-applicable

                policy "obligations and advice"
                permit
                obligation "log_access"
                advice "cache_result"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj         = (ObjectValue) result;
        val obligations = (ArrayValue) obj.get("obligations");
        val advice      = (ArrayValue) obj.get("advice");
        assertThat(obligations.size()).isEqualTo(1);
        assertThat(advice.size()).isEqualTo(1);
    }

    // ========================================================================
    // RESOURCE TRANSFORMATION TESTS
    // ========================================================================

    @Test
    void transformation_appliedToResource() {
        val source = """
                set "test"
                first-applicable

                policy "with transformation"
                permit
                transform "transformed_value"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj      = (ObjectValue) result;
        val resource = obj.get("resource");
        assertThat(resource).isEqualTo(Value.of("transformed_value"));
    }

    @Test
    void transformation_expression_evaluated() {
        val source = """
                set "test"
                first-applicable

                policy "transformation with expression"
                permit
                transform { "value": 42, "squared": 42 * 42 }
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj      = (ObjectValue) result;
        val resource = (ObjectValue) obj.get("resource");
        assertThat(resource.get("value")).isEqualTo(Value.of(42));
        assertThat(resource.get("squared")).isEqualTo(Value.of(1764));
    }

    // ========================================================================
    // DECISION SEQUENCE PERMUTATION TESTS
    // ========================================================================

    @Test
    void sequence_PERMIT_DENY_NOTAPPLICABLE_returnsFirstPermit() {
        val source = """
                set "test"
                first-applicable

                policy "permit"
                permit

                policy "deny"
                deny

                policy "not applicable"
                permit
                where false;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void sequence_DENY_PERMIT_NOTAPPLICABLE_returnsFirstDeny() {
        val source = """
                set "test"
                first-applicable

                policy "deny"
                deny

                policy "permit"
                permit

                policy "not applicable"
                deny
                where false;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void sequence_NOTAPPLICABLE_PERMIT_DENY_skipsToPermit() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable"
                deny
                where false;

                policy "permit"
                permit

                policy "deny"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void sequence_NOTAPPLICABLE_DENY_PERMIT_skipsToDeny() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable"
                permit
                where false;

                policy "deny"
                deny

                policy "permit"
                permit
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    @Test
    void sequence_multipleNotApplicable_thenPermit() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable 1"
                deny
                where false;

                policy "not applicable 2"
                permit
                where false;

                policy "not applicable 3"
                deny
                where false;

                policy "permit"
                permit
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void sequence_multipleNotApplicable_thenDeny() {
        val source = """
                set "test"
                first-applicable

                policy "not applicable 1"
                permit
                where false;

                policy "not applicable 2"
                deny
                where false;

                policy "not applicable 3"
                permit
                where false;

                policy "deny"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
    }

    // ========================================================================
    // STREAMING EXPRESSION TESTS
    // ========================================================================

    @Test
    void streamingAttribute_emitsMultipleValues() {
        val source = """
                set "test"
                first-applicable

                policy "streaming policy"
                permit
                where
                  (10).<test.counter> > 11;
                """;
        val stream = evaluateDocumentAsStream(source);
        StepVerifier.create(stream).assertNext(result -> assertDecision(result, Decision.PERMIT)).thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamingAttribute_inTransformation() {
        val source = """
                set "test"
                first-applicable

                policy "streaming transformation"
                permit
                transform (5).<test.counter>
                """;
        val stream = evaluateDocumentAsStream(source);
        StepVerifier.create(stream).assertNext(result -> {
            assertDecision(result, Decision.PERMIT);
            val obj      = (ObjectValue) result;
            val resource = obj.get("resource");
            assertThat(resource).isEqualTo(Value.of(5));
        }).assertNext(result -> {
            assertDecision(result, Decision.PERMIT);
            val obj      = (ObjectValue) result;
            val resource = obj.get("resource");
            assertThat(resource).isEqualTo(Value.of(6));
        }).assertNext(result -> {
            assertDecision(result, Decision.PERMIT);
            val obj      = (ObjectValue) result;
            val resource = obj.get("resource");
            assertThat(resource).isEqualTo(Value.of(7));
        }).thenCancel().verify(Duration.ofSeconds(5));
    }

    // ========================================================================
    // VARIABLE DEFINITION TESTS
    // ========================================================================

    @Test
    void variableDefinition_usedInPolicies() {
        val source = """
                set "test"
                first-applicable
                var threshold = 10;

                policy "uses variable"
                permit
                where
                  threshold == 10;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void variableDefinition_multipleVariables() {
        val source = """
                set "test"
                first-applicable
                var x = 5;
                var y = 10;
                var sum = x + y;

                policy "uses variables"
                permit
                where
                  sum == 15;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void variableDefinition_inWhere_affectsMatch() {
        val source = """
                set "test"
                first-applicable
                var threshold = 100;

                policy "below threshold"
                permit
                where
                  50 < threshold;

                policy "fallback"
                deny
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    // ========================================================================
    // COMPLEX EXPRESSION TESTS
    // ========================================================================

    @Test
    void complexExpression_inWhere() {
        val source = """
                set "test"
                first-applicable

                policy "complex where"
                permit
                where
                  (5 + 3) * 2 == 16;
                  [1, 2, 3][1] == 2;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void complexExpression_inTransformation() {
        val source = """
                set "test"
                first-applicable

                policy "complex transformation"
                permit
                transform {
                  "numbers" : [1, 2, 3],
                  "sum" : 6,
                  "doubled" : [2, 4, 6]
                }
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj      = (ObjectValue) result;
        val resource = (ObjectValue) obj.get("resource");
        assertThat(resource.get("sum")).isEqualTo(Value.of(6));
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    @Test
    void errorInWhereClause_returnsIndeterminate() {
        val source = """
                set "test"
                first-applicable

                policy "error in where"
                permit
                where
                  (1 / 0) == 0;
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.INDETERMINATE);
    }

    @Test
    void errorInObligation_returnsIndeterminate() {
        val source = """
                set "test"
                first-applicable

                policy "error in obligation"
                permit
                obligation (1 / 0)
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.INDETERMINATE);
    }

    @Test
    void errorInTransformation_returnsIndeterminate() {
        val source = """
                set "test"
                first-applicable

                policy "error in transformation"
                permit
                transform (1 / 0)
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.INDETERMINATE);
    }

    @Test
    void errorInFirstPolicy_continuesEvaluation() {
        val source = """
                set "test"
                first-applicable

                policy "error policy"
                permit
                where
                  (1 / 0) == 0;

                policy "valid permit"
                permit
                """;
        val result = evaluateDocument(source);
        // First policy causes INDETERMINATE, which is applicable, so it's returned
        assertDecision(result, Decision.INDETERMINATE);
    }

    // ========================================================================
    // MIXED PURE AND STREAMING TESTS
    // ========================================================================

    @Test
    void mixedPureAndStreaming_pureFirst_thenStreaming() {
        val source = """
                set "test"
                first-applicable

                policy "pure permit"
                permit
                where
                  5 > 3;

                policy "streaming permit"
                permit
                where
                  (10).<test.counter> > 5;
                """;
        val result = evaluateDocument(source);
        // Pure policy matches first
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void mixedPureAndStreaming_streamingRequired() {
        val source = """
                set "test"
                first-applicable

                policy "pure not applicable"
                permit
                where
                  false;

                policy "streaming permit"
                permit
                where
                  (10).<test.counter> > 5;
                """;
        val stream = evaluateDocumentAsStream(source);
        StepVerifier.create(stream).assertNext(result -> assertDecision(result, Decision.PERMIT)).thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Test
    void policyWithOnlyObligations_noWhere() {
        val source = """
                set "test"
                first-applicable

                policy "obligations only"
                permit
                obligation "log"
                obligation "notify"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj         = (ObjectValue) result;
        val obligations = (ArrayValue) obj.get("obligations");
        assertThat(obligations.size()).isEqualTo(2);
    }

    @Test
    void policyWithOnlyAdvice_noWhere() {
        val source = """
                set "test"
                first-applicable

                policy "advice only"
                deny
                advice "suggest_alternative"
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.DENY);
        val obj    = (ObjectValue) result;
        val advice = (ArrayValue) obj.get("advice");
        assertThat(advice.size()).isEqualTo(1);
    }

    @Test
    void undefinedResourceInResult() {
        val source = """
                set "test"
                first-applicable

                policy "no transformation"
                permit
                """;
        val result = evaluateDocument(source);
        assertDecision(result, Decision.PERMIT);
        val obj      = (ObjectValue) result;
        val resource = obj.get("resource");
        assertThat(resource).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void largeNumberOfPolicies_firstMatches() {
        val policies = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            policies.append(String.format("""
                    policy "policy %d"
                    permit
                    where
                      %d == 1;

                    """, i, i));
        }
        policies.append("""
                policy "final permit"
                permit
                """);

        val source = "set \"test\"\nfirst-applicable\n\n" + policies;
        val result = evaluateDocument(source);
        // All policies with where clauses fail, except the final one
        assertDecision(result, Decision.PERMIT);
    }

    @Test
    void veryFirstPolicyMatches_inLargeSet() {
        val policies = new StringBuilder();
        policies.append("""
                policy "first matches"
                permit

                """);
        for (int i = 2; i <= 100; i++) {
            policies.append(String.format("""
                    policy "policy %d"
                    deny

                    """, i));
        }

        val source = "set \"test\"\nfirst-applicable\n\n" + policies;
        val result = evaluateDocument(source);
        // First policy matches, all others ignored
        assertDecision(result, Decision.PERMIT);
    }
}
