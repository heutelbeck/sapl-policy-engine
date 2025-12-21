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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.util.SimpleFunctionLibrary;
import io.sapl.util.TestUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for policy target expression evaluation with enforced schemas.
 * <p>
 * Validates the interaction between:
 * <ul>
 * <li>Enforced schema validation</li>
 * <li>Explicit target expressions (after permit/deny)</li>
 * <li>Implicit true target (no expression after permit/deny)</li>
 * </ul>
 * <p>
 * The effective target for a policy is: schema_check AND target_expression
 * where target_expression defaults to true if not specified.
 */
class PolicyTargetExpressionTests {

    private static final SAPLParser PARSER = new DefaultSAPLParser();

    // Schema that requires subject to have "role" and "userId" fields
    private static final String SUBJECT_SCHEMA = """
            {
                "type": "object",
                "required": ["role", "userId"],
                "properties": {
                    "role": { "type": "string" },
                    "userId": { "type": "string" }
                }
            }
            """;

    // Subscription where subject MATCHES the schema
    private static final AuthorizationSubscription SCHEMA_MATCHING_SUBSCRIPTION = new AuthorizationSubscription(
            ObjectValue.builder().put("role", Value.of("editor")).put("userId", Value.of("user123")).build(),
            Value.of("read"), Value.of("document"), Value.UNDEFINED);

    // Subscription where subject does NOT match the schema (missing required
    // fields)
    private static final AuthorizationSubscription SCHEMA_NOT_MATCHING_SUBSCRIPTION = new AuthorizationSubscription(
            ObjectValue.builder().put("name", Value.of("Alice")).build(), Value.of("read"), Value.of("document"),
            Value.UNDEFINED);

    // Subscription where subject matches schema AND target expression (role ==
    // "editor")
    private static final AuthorizationSubscription TARGET_MATCHING_SUBSCRIPTION = SCHEMA_MATCHING_SUBSCRIPTION;

    // Subscription where subject matches schema but NOT target expression (role !=
    // "editor")
    private static final AuthorizationSubscription TARGET_NOT_MATCHING_SUBSCRIPTION = new AuthorizationSubscription(
            ObjectValue.builder().put("role", Value.of("viewer")).put("userId", Value.of("user456")).build(),
            Value.of("read"), Value.of("document"), Value.UNDEFINED);

    @Nested
    @DisplayName("Policy with enforced schema and explicit target expression")
    class EnforcedSchemaWithExplicitTarget {

        private static final String POLICY = """
                subject enforced schema %s

                policy "editor access"
                permit subject.role == "editor"
                """.formatted(SUBJECT_SCHEMA);

        @ParameterizedTest(name = "TraceLevel.{0}: schema matches, target matches -> PERMIT")
        @EnumSource(TraceLevel.class)
        void whenSchemaMatchesAndTargetMatches_thenPermit(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, TARGET_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.PERMIT);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: schema matches, target does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenSchemaMatchesAndTargetDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, TARGET_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: schema does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenSchemaDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, SCHEMA_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("With COVERAGE: schema matches and target matches -> PERMIT")
        void whenCoverageEnabled_andSchemaAndTargetMatch_thenPermit() {
            val result = evaluatePolicy(POLICY, TARGET_MATCHING_SUBSCRIPTION, TraceLevel.COVERAGE);
            assertDecision(result, Decision.PERMIT);
        }

        @Test
        @DisplayName("With COVERAGE: schema matches but target does not -> NOT_APPLICABLE")
        void whenCoverageEnabled_andSchemaMatchesButTargetDoesNot_thenNotApplicable() {
            val result = evaluatePolicy(POLICY, TARGET_NOT_MATCHING_SUBSCRIPTION, TraceLevel.COVERAGE);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Without COVERAGE: trace does not contain coverage fields")
        void whenCoverageDisabled_thenNoCoverageFields() {
            val result = evaluatePolicy(POLICY, TARGET_MATCHING_SUBSCRIPTION, TraceLevel.STANDARD);
            assertDecision(result, Decision.PERMIT);
            assertThat(result).isInstanceOf(ObjectValue.class);
            val resultObj = (ObjectValue) result;
            assertThat(resultObj.containsKey("conditions")).isFalse();
        }
    }

    @Nested
    @DisplayName("Policy with enforced schema and implicit true target")
    class EnforcedSchemaWithImplicitTarget {

        private static final String POLICY = """
                subject enforced schema %s

                policy "any valid user"
                permit
                """.formatted(SUBJECT_SCHEMA);

        @ParameterizedTest(name = "TraceLevel.{0}: schema matches -> PERMIT (implicit true target)")
        @EnumSource(TraceLevel.class)
        void whenSchemaMatches_thenPermit(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, SCHEMA_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.PERMIT);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: schema does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenSchemaDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, SCHEMA_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("With COVERAGE: schema matches -> PERMIT")
        void whenCoverageEnabled_andSchemaMatches_thenPermit() {
            val result = evaluatePolicy(POLICY, SCHEMA_MATCHING_SUBSCRIPTION, TraceLevel.COVERAGE);
            assertDecision(result, Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("Policy without enforced schema but with explicit target")
    class NoSchemaWithExplicitTarget {

        private static final String POLICY = """
                policy "editor only"
                permit subject.role == "editor"
                """;

        @ParameterizedTest(name = "TraceLevel.{0}: target matches -> PERMIT")
        @EnumSource(TraceLevel.class)
        void whenTargetMatches_thenPermit(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, TARGET_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.PERMIT);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: target does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenTargetDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, TARGET_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("With COVERAGE: target matches -> PERMIT")
        void whenCoverageEnabled_andTargetMatches_thenPermit() {
            val result = evaluatePolicy(POLICY, TARGET_MATCHING_SUBSCRIPTION, TraceLevel.COVERAGE);
            assertDecision(result, Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("Policy without enforced schema and implicit true target")
    class NoSchemaWithImplicitTarget {

        private static final String POLICY = """
                policy "allow all"
                permit
                """;

        @ParameterizedTest(name = "TraceLevel.{0}: any subscription -> PERMIT")
        @EnumSource(TraceLevel.class)
        void whenAnySubscription_thenPermit(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY, SCHEMA_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.PERMIT);
        }

        @Test
        @DisplayName("With COVERAGE: any subscription -> PERMIT")
        void whenCoverageEnabled_thenPermit() {
            val result = evaluatePolicy(POLICY, SCHEMA_MATCHING_SUBSCRIPTION, TraceLevel.COVERAGE);
            assertDecision(result, Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("Deny policies with schema and target combinations")
    class DenyPolicies {

        private static final String POLICY_WITH_SCHEMA_AND_TARGET = """
                subject enforced schema %s

                policy "deny non-editors"
                deny subject.role != "editor"
                """.formatted(SUBJECT_SCHEMA);

        @ParameterizedTest(name = "TraceLevel.{0}: schema matches, target matches -> DENY")
        @EnumSource(TraceLevel.class)
        void whenSchemaMatchesAndTargetMatches_thenDeny(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY_WITH_SCHEMA_AND_TARGET, TARGET_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.DENY);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: schema matches, target does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenSchemaMatchesAndTargetDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY_WITH_SCHEMA_AND_TARGET, TARGET_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }

        @ParameterizedTest(name = "TraceLevel.{0}: schema does not match -> NOT_APPLICABLE")
        @EnumSource(TraceLevel.class)
        void whenSchemaDoesNotMatch_thenNotApplicable(TraceLevel traceLevel) {
            val result = evaluatePolicy(POLICY_WITH_SCHEMA_AND_TARGET, SCHEMA_NOT_MATCHING_SUBSCRIPTION, traceLevel);
            assertDecision(result, Decision.NOT_APPLICABLE);
        }
    }

    // ========== Helper Methods ==========

    @SneakyThrows
    private Value evaluatePolicy(String policySource, AuthorizationSubscription subscription, TraceLevel traceLevel) {
        val components = createComponents();
        val context    = new CompilationContext(components.functionBroker(), components.attributeBroker(), traceLevel);
        val sapl       = PARSER.parse(policySource);
        val compiled   = SaplCompiler.compileDocument(sapl, context);

        val evalContext = new EvaluationContext("pdpId", "testConfig", "testSub", subscription,
                components.functionBroker(), components.attributeBroker());

        val matchExpr   = compiled.matchExpression();
        val matchResult = switch (matchExpr) {
                        case Value value                 -> value;
                        case PureExpression pureExpr     -> pureExpr.evaluate(evalContext);
                        case StreamExpression streamExpr ->
                            streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext))
                                    .blockFirst(Duration.ofSeconds(5));
                        };

        if (!Value.TRUE.equals(matchResult)) {
            return AuthorizationDecisionUtil.NOT_APPLICABLE;
        }

        val decisionExpr = compiled.decisionExpression();
        return switch (decisionExpr) {
        case Value value                 -> value;
        case PureExpression pureExpr     -> pureExpr.evaluate(evalContext);
        case StreamExpression streamExpr -> streamExpr.stream()
                .contextWrite(ctx -> ctx.put(EvaluationContext.class, evalContext)).blockFirst(Duration.ofSeconds(5));
        };
    }

    @SneakyThrows
    private PDPComponents createComponents() {
        return PolicyDecisionPointBuilder.withoutDefaults().withFunctionLibrary(SimpleFunctionLibrary.class)
                .withFunctionLibrary(SchemaValidationLibrary.class).withPolicyInformationPoint(new TestUtil.TestPip())
                .build();
    }

    private void assertDecision(Value result, Decision expectedDecision) {
        assertThat(result).isInstanceOf(ObjectValue.class);
        val decisionField = ((ObjectValue) result).get("decision");
        assertThat(decisionField).isEqualTo(Value.of(expectedDecision.toString()));
    }

}
