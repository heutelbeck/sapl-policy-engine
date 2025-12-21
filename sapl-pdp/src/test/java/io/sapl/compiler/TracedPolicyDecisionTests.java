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

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TraceFields;
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.api.model.ValueJsonMarshaller.toPrettyString;
import static io.sapl.compiler.TracedPolicyDecision.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for TracedPolicyDecision emission from compiled policies.
 * <p>
 * All tests evaluate the compiled expressions and validate the traced decision
 * output structure and content.
 */
@DisplayName("TracedPolicyDecision")
class TracedPolicyDecisionTests {

    private static final DefaultSAPLParser PARSER = new DefaultSAPLParser();
    private static final boolean           DEBUG  = false;

    private CompilationContext context;

    @BeforeEach
    @SneakyThrows
    void setup() {
        context = PolicyDecisionPointBuilder.withoutDefaults().withFunctionLibrary(FilterFunctionLibrary.class).build()
                .compilationContext();
    }

    @Nested
    @DisplayName("Constant Policies")
    class ConstantPolicyTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("constantPolicyCases")
        @DisplayName("evaluates to correct traced decision")
        void whenConstantPolicy_thenTracedDecisionHasCorrectFields(String description, String policy,
                String expectedName, String expectedEntitlement, Decision expectedDecision, int expectedObligations,
                int expectedAdvice, Value expectedResource) {

            val traced = evaluatePolicy(policy);

            printDecision(description, traced);

            assertThat(getName(traced)).isEqualTo(expectedName);
            assertThat(getEntitlement(traced)).isEqualTo(expectedEntitlement);
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(getObligations(traced)).hasSize(expectedObligations);
            assertThat(getAdvice(traced)).hasSize(expectedAdvice);
            assertThat(getResource(traced)).isEqualTo(expectedResource);
            assertThat(getErrors(traced)).isEmpty();
        }

        static Stream<Arguments> constantPolicyCases() {
            return Stream.of(arguments("minimal permit", """
                    policy "invoke-elder-sign"
                    permit
                    """, "invoke-elder-sign", "PERMIT", Decision.PERMIT, 0, 0, Value.UNDEFINED),

                    arguments("minimal deny", """
                            policy "banish-shoggoth"
                            deny
                            """, "banish-shoggoth", "DENY", Decision.DENY, 0, 0, Value.UNDEFINED),

                    arguments("permit with true condition", """
                            policy "ritual-approved"
                            permit where true;
                            """, "ritual-approved", "PERMIT", Decision.PERMIT, 0, 0, Value.UNDEFINED),

                    arguments("permit with false condition yields NOT_APPLICABLE", """
                            policy "stars-not-aligned"
                            permit where false;
                            """, "stars-not-aligned", "PERMIT", Decision.NOT_APPLICABLE, 0, 0, Value.UNDEFINED),

                    arguments("deny with obligation", """
                            policy "seal-gate"
                            deny
                            obligation "invoke-closing-ritual"
                            """, "seal-gate", "DENY", Decision.DENY, 1, 0, Value.UNDEFINED),

                    arguments("permit with advice", """
                            policy "consult-tome"
                            permit
                            advice "check-sanity-first"
                            """, "consult-tome", "PERMIT", Decision.PERMIT, 0, 1, Value.UNDEFINED),

                    arguments("permit with transform", """
                            policy "redact-manuscript"
                            permit
                            transform "sanitized-content"
                            """, "redact-manuscript", "PERMIT", Decision.PERMIT, 0, 0, Value.of("sanitized-content")),

                    arguments("permit with all constraints", """
                            policy "archive-access-granted"
                            permit
                            obligation "log-forbidden-knowledge-access"
                            advice "ward-reading-room"
                            transform "redacted-manuscript"
                            """, "archive-access-granted", "PERMIT", Decision.PERMIT, 1, 1,
                            Value.of("redacted-manuscript")));
        }

        @Test
        @DisplayName("obligation content is preserved")
        void whenObligationHasContent_thenContentIsPreserved() {
            val policy = """
                    policy "require-ritual"
                    permit
                    obligation { "type": "invoke", "ritual": "elder-sign", "power": 7 }
                    """;

            val traced      = evaluatePolicy(policy);
            val obligations = getObligations(traced);

            printDecision("obligation content", traced);

            assertThat(obligations).hasSize(1);
            assertThat(obligations.getFirst()).isEqualTo(json("""
                    {"type": "invoke", "ritual": "elder-sign", "power": 7}
                    """));
        }

        @Test
        @DisplayName("multiple obligations are preserved")
        void whenMultipleObligations_thenAllPreserved() {
            val policy = """
                    policy "complex-ritual"
                    deny
                    obligation "prepare-altar"
                    obligation "light-candles"
                    obligation "chant-incantation"
                    """;

            val traced      = evaluatePolicy(policy);
            val obligations = getObligations(traced);

            printDecision("multiple obligations", traced);

            assertThat(obligations).hasSize(3).containsExactly(Value.of("prepare-altar"), Value.of("light-candles"),
                    Value.of("chant-incantation"));
        }
    }

    @Nested
    @DisplayName("Pure Expression Compilation")
    class PureExpressionCompilationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("pipFreePolicies")
        @DisplayName("PIP-free policies never compile to StreamExpression")
        void whenNoPipAccess_thenExpressionIsNeverStream(String description, String policy) {
            val compiled = compilePolicy(policy);

            assertThat(compiled.decisionExpression())
                    .as("PIP-free policy '%s' should compile to Value or PureExpression, not StreamExpression",
                            description)
                    .isNotInstanceOf(StreamExpression.class);
        }

        static Stream<Arguments> pipFreePolicies() {
            return Stream.of(
                    // Minimal policies
                    arguments("minimal permit", "policy \"p\" permit"), arguments("minimal deny", "policy \"p\" deny"),

                    // Boolean conditions
                    arguments("true condition", "policy \"p\" permit where true;"),
                    arguments("false condition", "policy \"p\" permit where false;"),
                    arguments("complex boolean", "policy \"p\" permit where true && (false || true);"),

                    // Arithmetic expressions
                    arguments("arithmetic condition", "policy \"p\" permit where 2 + 2 == 4;"),
                    arguments("complex arithmetic", "policy \"p\" permit where (10 * 5) / 2 > 20;"),

                    // String operations
                    arguments("string equality", "policy \"p\" permit where \"admin\" == \"admin\";"),
                    arguments("string in array", "policy \"p\" permit where \"read\" in [\"read\", \"write\"];"),

                    // Subject/action/resource access (no PIPs)
                    arguments("subject access", "policy \"p\" permit where subject.role == \"admin\";"),
                    arguments("action access", "policy \"p\" permit where action == \"read\";"),
                    arguments("resource access", "policy \"p\" permit where resource.classification != \"secret\";"),
                    arguments("environment access", "policy \"p\" permit where environment.time > 0;"),

                    // Single simple constraints
                    arguments("with string obligation", "policy \"p\" permit obligation \"log\""),
                    arguments("with string advice", "policy \"p\" deny advice \"notify\""),
                    arguments("with string transform", "policy \"p\" permit transform \"sanitized\""),

                    // Multiple constraints (now correctly pure - was incorrectly StreamExpression)
                    arguments("with multiple constraints",
                            "policy \"p\" permit obligation \"audit\" advice \"warn\" transform resource.data"),

                    // Transform with filter function (now correctly pure)
                    arguments("filter expression",
                            "policy \"p\" permit transform resource.items |- filter.blacken(\"secret\")"),

                    // Complex expressions without PIPs
                    arguments("nested object access",
                            "policy \"p\" permit where subject.department.manager.level > 3;"),
                    arguments("array indexing", "policy \"p\" permit where resource.tags[0] == \"public\";"),

                    // Policy sets without PIPs
                    arguments("policy set", """
                            set "access-control" deny-overrides
                            policy "admin" permit where subject.role == "admin";
                            policy "default" deny
                            """));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("fullyConstantPolicies")
        @DisplayName("Fully constant policies compile to Value")
        void whenFullyConstant_thenCompiledToValue(String description, String policy) {
            val compiled = compilePolicy(policy);

            assertThat(compiled.decisionExpression())
                    .as("Fully constant policy '%s' should compile to Value", description).isInstanceOf(Value.class);
        }

        static Stream<Arguments> fullyConstantPolicies() {
            return Stream.of(
                    // Minimal
                    arguments("minimal permit", "policy \"p\" permit"), arguments("minimal deny", "policy \"p\" deny"),

                    // With constant where clause
                    arguments("constant true condition", "policy \"p\" permit where true;"),
                    arguments("constant false condition", "policy \"p\" permit where false;"),
                    arguments("constant arithmetic", "policy \"p\" permit where 2 + 2 == 4;"),
                    arguments("constant string comparison", "policy \"p\" permit where \"admin\" == \"admin\";"),
                    arguments("constant array membership",
                            "policy \"p\" permit where \"read\" in [\"read\", \"write\"];"),

                    // With constant constraints
                    arguments("constant obligation", "policy \"p\" permit obligation \"log-access\""),
                    arguments("constant advice", "policy \"p\" deny advice \"notify-admin\""),
                    arguments("constant transform", "policy \"p\" permit transform \"sanitized-result\""),
                    arguments("all constant constraints",
                            "policy \"p\" permit obligation \"audit\" advice \"warn\" transform \"result\""),

                    // Complex constant expressions
                    arguments("nested constant object",
                            "policy \"p\" permit transform {\"level\": 5, \"nested\": {\"value\": true}}"),
                    arguments("constant array transform", "policy \"p\" permit transform [1, 2, 3, 4, 5]"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("pureExpressionPolicies")
        @DisplayName("Subscription-dependent policies compile to PureExpression")
        void whenSubscriptionDependent_thenCompiledToPureExpression(String description, String policy) {
            val compiled = compilePolicy(policy);

            assertThat(compiled.decisionExpression())
                    .as("Subscription-dependent policy '%s' should compile to PureExpression", description)
                    .isInstanceOf(PureExpression.class);
        }

        static Stream<Arguments> pureExpressionPolicies() {
            return Stream.of(
                    // Simple subscription access in where clause
                    arguments("subject field access", "policy \"p\" permit where subject.role == \"admin\";"),
                    arguments("action comparison", "policy \"p\" permit where action == \"read\";"),
                    arguments("resource field access", "policy \"p\" permit where resource.public == true;"),
                    arguments("environment access", "policy \"p\" permit where environment.time > 0;"),

                    // Complex subscription access in where clause
                    arguments("nested subject access",
                            "policy \"p\" permit where subject.department.manager.clearanceLevel >= 5;"),
                    arguments("array index on resource", "policy \"p\" permit where resource.tags[0] == \"public\";"),
                    arguments("multiple subscription vars",
                            "policy \"p\" permit where subject.role == \"admin\" && resource.owner == subject.id;"),

                    // Subscription-dependent constraints
                    arguments("subscription-based obligation", "policy \"p\" permit obligation subject.requiredAction"),
                    arguments("subscription-based advice", "policy \"p\" deny advice resource.recommendedAction"),
                    arguments("subscription-based transform", "policy \"p\" permit transform resource.sanitizedData"),

                    // Mixed constant body + subscription constraints
                    arguments("constant body with subscription obligation",
                            "policy \"p\" permit where true; obligation subject.auditInfo"),
                    arguments("constant body with subscription transform",
                            "policy \"p\" permit transform resource.publicView"),

                    // Multiple subscription-dependent constraints
                    arguments("all subscription constraints", """
                            policy "p" permit
                            obligation subject.requiredObligation
                            advice resource.suggestedAdvice
                            transform environment.transformConfig
                            """),

                    // Filter with subscription data
                    arguments("filter on subscription data",
                            "policy \"p\" permit transform resource.items |- filter.blacken(subject.redactPattern)"),

                    // Complex policy set with subscription access
                    arguments("policy set with subscription access", """
                            set "access-control" deny-overrides
                            policy "owner-access" permit where resource.owner == subject.id;
                            policy "admin-access" permit where subject.role == "admin";
                            policy "public-read" permit where action == "read" && resource.public == true;
                            policy "default-deny" deny
                            """),

                    // Policy set with subscription-dependent constraints
                    arguments("policy set with subscription constraints", """
                            set "audit-control" permit-overrides
                            policy "permitted" permit
                            obligation subject.auditLog
                            advice resource.cacheHint
                            transform resource.filteredView
                            """));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("combiningAlgorithmPolicySets")
        @DisplayName("All combining algorithms produce pure expressions when no PIPs accessed")
        void whenCombiningAlgorithmWithoutPips_thenExpressionIsNeverStream(String algorithm, String policySet) {
            val compiled = compilePolicy(policySet);

            assertThat(compiled.decisionExpression())
                    .as("Policy set with %s algorithm and no PIPs should not produce StreamExpression", algorithm)
                    .isNotInstanceOf(StreamExpression.class);
        }

        static Stream<Arguments> combiningAlgorithmPolicySets() {
            return Stream.of(arguments("deny-overrides", """
                    set "access" deny-overrides
                    policy "admin" permit where subject.role == "admin";
                    policy "user" permit where subject.role == "user" && action == "read";
                    policy "default" deny
                    """), arguments("permit-overrides", """
                    set "access" permit-overrides
                    policy "blocked" deny where resource.blocked == true;
                    policy "admin" permit where subject.role == "admin";
                    policy "default" deny
                    """), arguments("deny-unless-permit", """
                    set "access" deny-unless-permit
                    policy "admin" permit where subject.role == "admin";
                    policy "readonly" permit where action == "read";
                    """), arguments("permit-unless-deny", """
                    set "access" permit-unless-deny
                    policy "secret" deny where resource.classification == "secret";
                    policy "blocked" deny where subject.blocked == true;
                    """), arguments("only-one-applicable", """
                    set "access" only-one-applicable
                    policy "admin" permit where subject.role == "admin";
                    policy "user" permit where action == "read";
                    policy "contractor" deny where subject.role == "contractor";
                    """), arguments("first-applicable", """
                    set "access" first-applicable
                    policy "admin" permit where subject.role == "admin";
                    policy "blocked" deny where subject.blocked == true;
                    policy "default" permit
                    """));
        }

    }

    @Nested
    @DisplayName("Pure Expression Policies")
    class PureExpressionPolicyTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("conditionBasedPolicyCases")
        @DisplayName("condition-based policy evaluates correctly")
        void whenConditionBasedPolicy_thenEvaluatesCorrectly(String description, String policy, String variableName,
                Value variableValue, String expectedName, String expectedEntitlement, Decision expectedDecision) {
            val traced = evaluatePolicyWithVariables(policy, Map.of(variableName, variableValue));

            printDecision(description, traced);

            if (expectedName != null) {
                assertThat(getName(traced)).isEqualTo(expectedName);
            }
            if (expectedEntitlement != null) {
                assertThat(getEntitlement(traced)).isEqualTo(expectedEntitlement);
            }
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> conditionBasedPolicyCases() {
            return Stream.of(arguments("subject condition satisfied", """
                    policy "check-clearance"
                    permit where subject.clearanceLevel >= 5;
                    """, "subject", json("{\"clearanceLevel\": 7}"), "check-clearance", "PERMIT", Decision.PERMIT),

                    arguments("subject condition not satisfied", """
                            policy "check-clearance"
                            permit where subject.clearanceLevel >= 5;
                            """, "subject", json("{\"clearanceLevel\": 2}"), null, null, Decision.NOT_APPLICABLE),

                    arguments("action-based condition (deny)", """
                            policy "restrict-action"
                            deny where action.type == "summon";
                            """, "action", json("{\"type\": \"summon\"}"), "restrict-action", "DENY", Decision.DENY),

                    arguments("resource-based condition (deny)", """
                            policy "protect-necronomicon"
                            deny where resource.classification == "forbidden";
                            """, "resource", json("{\"classification\": \"forbidden\", \"name\": \"Necronomicon\"}"),
                            null, null, Decision.DENY),

                    arguments("complex boolean expression", """
                            policy "complex-check"
                            permit where subject.role == "investigator" && subject.sanity > 50;
                            """, "subject", json("{\"role\": \"investigator\", \"sanity\": 75}"), null, null,
                            Decision.PERMIT));
        }

        @Test
        @DisplayName("dynamic obligation with subject data")
        void whenDynamicObligation_thenSubjectDataIncluded() {
            val policy  = """
                    policy "log-access"
                    permit
                    obligation { "log": subject.name }
                    """;
            val subject = json("""
                    {"name": "Herbert West"}
                    """);

            val traced      = evaluatePolicyWithVariables(policy, Map.of("subject", subject));
            val obligations = getObligations(traced);

            printDecision("dynamic obligation", traced);

            assertThat(obligations).hasSize(1);
            assertThat(obligations.getFirst()).isEqualTo(json("""
                    {"log": "Herbert West"}
                    """));
        }

        @Test
        @DisplayName("dynamic transform with resource data")
        void whenDynamicTransform_thenResourceDataTransformed() {
            val policy   = """
                    policy "redact-sensitive"
                    permit
                    transform { "content": resource.content, "redacted": true }
                    """;
            val resource = json("""
                    {"content": "secret ritual", "author": "unknown"}
                    """);

            val traced = evaluatePolicyWithVariables(policy, Map.of("resource", resource));

            printDecision("dynamic transform", traced);

            assertThat(getResource(traced)).isEqualTo(json("""
                    {"content": "secret ritual", "redacted": true}
                    """));
        }
    }

    @Nested
    @DisplayName("Pure Expression Policies with Subscription Access")
    class PureExpressionWithSubscriptionAccessTests {

        @Test
        @DisplayName("policy with subscription-based obligation compiles to pure expression")
        void whenSubscriptionObligation_thenPureExpression() {
            val policy  = """
                    policy "subscription-obligation"
                    permit
                    obligation subject.dynamicValue
                    """;
            val subject = json("""
                    {"dynamicValue": "first-value"}
                    """);
            val evalCtx = createEvaluationContext(Map.of("subject", subject));

            val compiled = compilePolicy(policy);

            assertThat(compiled.decisionExpression()).isInstanceOf(PureExpression.class);

            val pureExpr = (PureExpression) compiled.decisionExpression();
            val traced   = pureExpr.evaluate(evalCtx);

            printDecision("subscription obligation", traced);
            assertThat(getName(traced)).isEqualTo("subscription-obligation");
            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getObligations(traced)).hasSize(1).first().isEqualTo(Value.of("first-value"));
        }

        @Test
        @DisplayName("policy with subscription-based transform compiles to pure expression")
        void whenSubscriptionTransform_thenPureExpression() {
            val policy   = """
                    policy "subscription-transform"
                    permit
                    transform resource.content
                    """;
            val resource = json("""
                    {"content": "streamed-content"}
                    """);
            val evalCtx  = createEvaluationContext(Map.of("resource", resource));

            val compiled = compilePolicy(policy);

            assertThat(compiled.decisionExpression()).isInstanceOf(PureExpression.class);

            val pureExpr = (PureExpression) compiled.decisionExpression();
            val traced   = pureExpr.evaluate(evalCtx);

            printDecision("subscription transform", traced);
            assertThat(getResource(traced)).isEqualTo(Value.of("streamed-content"));
        }
    }

    @Nested
    @DisplayName("Policy Set Combining")
    class PolicySetCombiningTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("combiningAlgorithmCases")
        @DisplayName("combining algorithm yields expected decision")
        void whenCombiningAlgorithm_thenExpectedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision) {
            val evalCtx = createEvaluationContext(variables);
            val traced  = evaluatePolicySet(policySet, evalCtx);

            printDecision(description, traced);

            assertThat(traced).isNotNull().isInstanceOf(ObjectValue.class);
            val obj = (ObjectValue) traced;
            assertThat(obj).containsEntry(TraceFields.DECISION, Value.of(expectedDecision.name()));
        }

        static Stream<Arguments> combiningAlgorithmCases() {
            return Stream.of(arguments("deny-overrides with deny wins", """
                    set "arkham-controls" deny-overrides
                    policy "allow-librarian" permit
                    policy "deny-after-midnight" deny
                    """, Map.of(), Decision.DENY),

                    arguments("permit-overrides with permit wins", """
                            set "miskatonic-access" permit-overrides
                            policy "deny-outsiders" deny
                            policy "allow-faculty" permit
                            """, Map.of(), Decision.PERMIT),

                    arguments("first-applicable returns first match", """
                            set "ritual-chamber" first-applicable
                            policy "check-initiate" permit where subject.isInitiate == true;
                            policy "default-deny" deny
                            """, Map.of("subject", json("{\"isInitiate\": true}")), Decision.PERMIT),

                    arguments("first-applicable falls through when not applicable", """
                            set "ritual-chamber" first-applicable
                            policy "check-initiate" permit where subject.isInitiate == true;
                            policy "default-deny" deny
                            """, Map.of("subject", json("{\"isInitiate\": false}")), Decision.DENY),

                    arguments("only-one-applicable when exactly one matches", """
                            set "restricted-archive" only-one-applicable
                            policy "senior-access" permit where subject.rank == "senior";
                            policy "junior-denied" deny where subject.rank == "junior";
                            """, Map.of("subject", json("{\"rank\": \"senior\"}")), Decision.PERMIT));
        }

        @Test
        @DisplayName("combining algorithm preserves obligations from winning policy")
        void whenCombining_thenObligationsPreserved() {
            val policySet = """
                    set "archive-with-logging" permit-overrides
                    policy "allow-with-logging"
                    permit
                    obligation "log-access"
                    obligation "notify-librarian"
                    """;
            val evalCtx   = createEvaluationContext(Map.of());

            val traced = evaluatePolicySet(policySet, evalCtx);

            printDecision("combining preserves obligations", traced);

            val obj         = (ObjectValue) traced;
            val obligations = (ArrayValue) obj.get(TraceFields.OBLIGATIONS);
            assertThat(obligations).hasSize(2).contains(Value.of("log-access"), Value.of("notify-librarian"));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("undefined variable access yields NOT_APPLICABLE (undefined != true)")
        void whenUndefinedVariable_thenNotApplicable() {
            val policy  = """
                    policy "missing-data"
                    permit where undefined.field == true;
                    """;
            val evalCtx = createEvaluationContext(Map.of());

            val compiled = compilePolicy(policy);
            val traced   = evaluateExpression(compiled.decisionExpression(), evalCtx);

            printDecision("undefined variable", traced);

            // Accessing undefined variable returns undefined, undefined == true is false
            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(getErrors(traced)).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bodyErrorCases")
        @DisplayName("body error yields INDETERMINATE with error captured")
        void whenBodyError_thenIndeterminateWithError(String description, String policy, Value subject) {
            val traced = evaluatePolicyWithVariables(policy, Map.of("subject", subject));

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getErrors(traced)).isNotEmpty().first().isInstanceOf(ErrorValue.class);
        }

        static Stream<Arguments> bodyErrorCases() {
            return Stream.of(arguments("type error in condition", """
                    policy "type-mismatch"
                    permit where subject.name > 5;
                    """, json("{\"name\": \"not-a-number\"}")),

                    arguments("division by zero", """
                            policy "divide-error"
                            permit where 10 / subject.divisor > 0;
                            """, json("{\"divisor\": 0}")));

        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("singleConstraintErrorCases")
        @DisplayName("single constraint error yields INDETERMINATE with error captured")
        void whenSingleConstraintError_thenIndeterminateWithError(String description, String policy,
                Map<String, Value> variables, String expectedErrorFragment) {
            val traced = evaluatePolicyWithVariables(policy, variables);

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getErrors(traced)).hasSize(1).first().isInstanceOf(ErrorValue.class);
            assertThat(getErrors(traced).getFirst().toString()).contains(expectedErrorFragment);
        }

        static Stream<Arguments> singleConstraintErrorCases() {
            return Stream.of(arguments("obligation error (division by zero)", """
                    policy "cursed-obligation"
                    permit
                    obligation 1 / subject.divisor
                    """, Map.of("subject", json("{\"divisor\": 0}")), "Division by zero"),

                    arguments("obligation error in string concatenation (emergency override)", """
                            policy "emergency override"
                            permit
                                resource.type == "patient_record"
                            where
                                resource.department != subject.department;
                                environment.location == "emergency room";
                            obligation {
                                         "type": "send email",
                                         "to": "ethical_board@princeton-plainsboro.med",
                                         "subject": "Emergency Access Detected",
                                         "message": "The record of patient " + resource.id +
                                                    " has been accessed from the emergency room by " +
                                                    subject.username +". Please review the process!" + 1/subject.zero
                                       }
                            """, Map.of("subject",
                            json("{\"username\": \"house\", \"department\": \"diagnostics\", \"zero\": 0}"), "resource",
                            json("{\"type\": \"patient_record\", \"id\": 123, \"department\": \"cardiology\"}"),
                            "environment", json("{\"location\": \"emergency room\"}")), "Division by zero"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("multipleErrorCases")
        @DisplayName("multiple errors are all captured")
        void whenMultipleErrors_thenAllCaptured(String description, String policy, Map<String, Value> variables,
                int expectedErrorCount) {
            val traced = evaluatePolicyWithVariables(policy, variables);

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getErrors(traced)).hasSize(expectedErrorCount).allMatch(ErrorValue.class::isInstance);
        }

        static Stream<Arguments> multipleErrorCases() {
            // Note: Undefined access (e.g. subject.missing.field) no longer produces
            // errors.
            // Tests use division by zero to create actual errors.
            return Stream.of(arguments("multiple obligation errors", """
                    policy "multiple-cursed-obligations"
                    permit
                    obligation 1 / subject.zero
                    obligation 1 / subject.anotherZero
                    """, Map.of("subject", json("{\"zero\": 0, \"anotherZero\": 0}")), 2),

                    arguments("obligation and advice errors", """
                            policy "doubly-cursed"
                            permit
                            obligation 1 / subject.divisor
                            advice 1 / subject.anotherDivisor
                            """, Map.of("subject", json("{\"divisor\": 0, \"anotherDivisor\": 0}")), 2),

                    arguments("obligation, advice and transform errors", """
                            policy "triply-cursed"
                            permit
                            obligation 1 / subject.zero
                            advice 1 / subject.anotherZero
                            transform 1 / resource.transformDivisor
                            """, Map.of("subject", json("{\"zero\": 0, \"anotherZero\": 0}"), "resource",
                            json("{\"transformDivisor\": 0}")), 3));
        }

        @Test
        @DisplayName("body error takes precedence over constraint errors")
        void whenBodyError_thenConstraintsNotEvaluated() {
            val policy  = """
                    policy "body-error-precedence"
                    permit where 1 / subject.divisor > 0;
                    obligation "should-not-matter"
                    """;
            val subject = json("""
                    {"divisor": 0}
                    """);

            val traced = evaluatePolicyWithVariables(policy, Map.of("subject", subject));

            printDecision("body error precedence", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getErrors(traced)).hasSize(1);
            assertThat(getErrors(traced).getFirst().toString()).contains("Division by zero");
        }
    }

    @Nested
    @DisplayName("Attribute Tracing")
    class AttributeTracingTests {

        private CompilationContext pipContext;

        @SneakyThrows
        @BeforeEach
        void setupPip() {
            val components = PolicyDecisionPointBuilder.withoutDefaults()
                    .withPolicyInformationPoint(new MiskatonicPip()).build();
            pipContext = new CompilationContext(components.functionBroker(), components.attributeBroker());
        }

        @Test
        @DisplayName("environment attribute access is traced")
        void whenEnvironmentAttribute_thenAttributeIsTraced() {
            val policy = """
                    policy "check-stars"
                    permit where <miskatonic.starsAligned> == true;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("environment attribute", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAttributes(traced)).isNotEmpty();

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            assertThat(firstAttribute).isNotNull().containsKey(TraceFields.INVOCATION);

            val invocation = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull()
                    .containsEntry(TraceFields.ATTRIBUTE_NAME, Value.of("miskatonic.starsAligned"))
                    .containsEntry(TraceFields.IS_ENVIRONMENT, Value.TRUE);
        }

        @Test
        @DisplayName("entity attribute access is traced with entity value")
        void whenEntityAttribute_thenAttributeAndEntityAreTraced() {
            val policy  = """
                    policy "check-sanity"
                    permit where subject.<miskatonic.sanityLevel> > 50;
                    """;
            val subject = Value.of("Herbert West");

            val traced = evaluatePolicyWithPip(policy, Map.of("subject", subject));

            printDecision("entity attribute", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAttributes(traced)).isNotEmpty();

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull()
                    .containsEntry(TraceFields.ATTRIBUTE_NAME, Value.of("miskatonic.sanityLevel"))
                    .containsEntry(TraceFields.IS_ENVIRONMENT, Value.FALSE)
                    .containsEntry(TraceFields.ENTITY, Value.of("Herbert West"));
        }

        @Test
        @DisplayName("multiple attribute accesses are all traced")
        void whenMultipleAttributes_thenAllAreTraced() {
            val policy  = """
                    policy "complex-check"
                    permit where <miskatonic.starsAligned> == true && subject.<miskatonic.sanityLevel> > 30;
                    """;
            val subject = Value.of("Randolph Carter");

            val traced = evaluatePolicyWithPip(policy, Map.of("subject", subject));

            printDecision("multiple attributes", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAttributes(traced)).hasSize(2);

            val attributeNames = getAttributes(traced).stream().map(attr -> extractAttributeName((ObjectValue) attr))
                    .toList();
            assertThat(attributeNames).containsExactlyInAnyOrder(Value.of("miskatonic.starsAligned"),
                    Value.of("miskatonic.sanityLevel"));

            val starsAttr = findAttributeByName(getAttributes(traced), "miskatonic.starsAligned");
            assertThat(starsAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.TRUE);
            val starsInvocation = (ObjectValue) starsAttr.get(TraceFields.INVOCATION);
            assertThat(starsInvocation).isNotNull().containsEntry(TraceFields.IS_ENVIRONMENT, Value.TRUE);

            val sanityAttr = findAttributeByName(getAttributes(traced), "miskatonic.sanityLevel");
            assertThat(sanityAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.of(85));
            val sanityInvocation = (ObjectValue) sanityAttr.get(TraceFields.INVOCATION);
            assertThat(sanityInvocation).isNotNull().containsEntry(TraceFields.IS_ENVIRONMENT, Value.FALSE)
                    .containsEntry(TraceFields.ENTITY, Value.of("Randolph Carter"));
        }

        @Test
        @DisplayName("attributes from body, obligation, advice and transform are all traced")
        void whenAttributesInAllPolicyParts_thenAllAreTraced() {
            val policy  = """
                    policy "full-ritual-check"
                    permit where <miskatonic.starsAligned> == true;
                    obligation { "sanity": subject.<miskatonic.sanityLevel> }
                    advice { "grimoires": <miskatonic.grimoireCount> }
                    transform { "knowledge": subject.<miskatonic.forbiddenKnowledge> }
                    """;
            val subject = Value.of("Randolph Carter");

            val traced = evaluatePolicyWithPip(policy, Map.of("subject", subject));

            printDecision("attributes in all policy parts", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAttributes(traced)).hasSize(4);

            val attributeNames = getAttributes(traced).stream().map(attr -> extractAttributeName((ObjectValue) attr))
                    .toList();
            assertThat(attributeNames).containsExactlyInAnyOrder(Value.of("miskatonic.starsAligned"),
                    Value.of("miskatonic.sanityLevel"), Value.of("miskatonic.grimoireCount"),
                    Value.of("miskatonic.forbiddenKnowledge"));

            val starsAttr = findAttributeByName(getAttributes(traced), "miskatonic.starsAligned");
            assertThat(starsAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.TRUE);

            val sanityAttr = findAttributeByName(getAttributes(traced), "miskatonic.sanityLevel");
            assertThat(sanityAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.of(85));

            val grimoireAttr = findAttributeByName(getAttributes(traced), "miskatonic.grimoireCount");
            assertThat(grimoireAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.of(13));

            val knowledgeAttr = findAttributeByName(getAttributes(traced), "miskatonic.forbiddenKnowledge");
            assertThat(knowledgeAttr).isNotNull().containsEntry(TraceFields.VALUE, Value.of("Necronomicon excerpts"));

            assertThat(getObligations(traced)).hasSize(1).first().isEqualTo(json("{\"sanity\": 85}"));
            assertThat(getAdvice(traced)).hasSize(1).first().isEqualTo(json("{\"grimoires\": 13}"));
            assertThat(getResource(traced)).isEqualTo(json("{\"knowledge\": \"Necronomicon excerpts\"}"));
        }

        @Test
        @DisplayName("invocation includes default timing parameters")
        void whenAttributeWithDefaults_thenDefaultTimingParametersAreTraced() {
            val policy = """
                    policy "check-stars-defaults"
                    permit where <miskatonic.starsAligned> == true;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("invocation with default timing", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().containsEntry(TraceFields.FRESH, Value.FALSE)
                    .containsEntry(TraceFields.INITIAL_TIMEOUT, Value.of(3000))
                    .containsEntry(TraceFields.POLL_INTERVAL, Value.of(30000))
                    .containsEntry(TraceFields.BACKOFF, Value.of(1000)).containsEntry(TraceFields.RETRIES, Value.of(3));
        }

        @Test
        @DisplayName("invocation includes custom timing parameters")
        void whenAttributeWithCustomTiming_thenCustomTimingParametersAreTraced() {
            val policy = """
                    policy "check-stars-custom-timing"
                    permit where <miskatonic.starsAligned[{initialTimeOutMs: 5000, pollIntervalMs: 60000, backoffMs: 2000, retries: 5}]> == true;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("invocation with custom timing", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().containsEntry(TraceFields.FRESH, Value.FALSE)
                    .containsEntry(TraceFields.INITIAL_TIMEOUT, Value.of(5000))
                    .containsEntry(TraceFields.POLL_INTERVAL, Value.of(60000))
                    .containsEntry(TraceFields.BACKOFF, Value.of(2000)).containsEntry(TraceFields.RETRIES, Value.of(5));
        }

        @Test
        @DisplayName("invocation includes fresh flag when set to true")
        void whenAttributeWithFreshTrue_thenFreshFlagIsTraced() {
            val policy = """
                    policy "check-stars-fresh"
                    permit where <miskatonic.starsAligned[{fresh: true}]> == true;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("invocation with fresh=true", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().as("fresh flag should be true when explicitly set")
                    .containsEntry(TraceFields.FRESH, Value.TRUE);
        }

        @Test
        @DisplayName("invocation includes arguments for environment attribute with parameters")
        void whenEnvironmentAttributeWithArguments_thenArgumentsAreTraced() {
            val policy = """
                    policy "ritual-power-check"
                    permit where <miskatonic.ritualPower(100, 50)> > 100;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("environment attribute with arguments", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            assertThat(firstAttribute).isNotNull().containsEntry(TraceFields.VALUE, Value.of(150));
            val invocation = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull()
                    .containsEntry(TraceFields.ATTRIBUTE_NAME, Value.of("miskatonic.ritualPower"))
                    .containsEntry(TraceFields.IS_ENVIRONMENT, Value.TRUE).containsKey(TraceFields.ARGUMENTS);

            val arguments = (ArrayValue) invocation.get(TraceFields.ARGUMENTS);
            assertThat(arguments).hasSize(2).containsExactly(Value.of(100), Value.of(50));
        }

        @Test
        @DisplayName("invocation includes arguments for entity attribute with parameters")
        void whenEntityAttributeWithArguments_thenArgumentsAreTraced() {
            val policy  = """
                    policy "cultist-rank-check"
                    permit where subject.<miskatonic.cultistRank("Esoteric Order of Dagon")> != "unknown";
                    """;
            val subject = Value.of("Obed Marsh");

            val traced = evaluatePolicyWithPip(policy, Map.of("subject", subject));

            printDecision("entity attribute with arguments", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            assertThat(firstAttribute).isNotNull().containsEntry(TraceFields.VALUE,
                    Value.of("Obed Marsh of Esoteric Order of Dagon"));
            val invocation = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull()
                    .containsEntry(TraceFields.ATTRIBUTE_NAME, Value.of("miskatonic.cultistRank"))
                    .containsEntry(TraceFields.IS_ENVIRONMENT, Value.FALSE)
                    .containsEntry(TraceFields.ENTITY, Value.of("Obed Marsh")).containsKey(TraceFields.ARGUMENTS);

            val arguments = (ArrayValue) invocation.get(TraceFields.ARGUMENTS);
            assertThat(arguments).hasSize(1).containsExactly(Value.of("Esoteric Order of Dagon"));
        }

        @Test
        @DisplayName("invocation includes fresh and custom timing together")
        void whenAttributeWithFreshAndCustomTiming_thenBothAreTraced() {
            val policy = """
                    policy "fresh-custom-timing"
                    permit where <miskatonic.grimoireCount[{fresh: true, initialTimeOutMs: 10000, retries: 10}]> > 0;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("fresh with custom timing", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().containsEntry(TraceFields.FRESH, Value.TRUE)
                    .containsEntry(TraceFields.INITIAL_TIMEOUT, Value.of(10000))
                    .containsEntry(TraceFields.RETRIES, Value.of(10)).as("Default poll interval should still apply")
                    .containsEntry(TraceFields.POLL_INTERVAL, Value.of(30000)).as("Default backoff should still apply")
                    .containsEntry(TraceFields.BACKOFF, Value.of(1000));
        }

        @Test
        @DisplayName("invocation includes configuration ID")
        void whenAttributeAccessed_thenConfigurationIdIsTraced() {
            val policy = """
                    policy "security-id-check"
                    permit where <miskatonic.starsAligned> == true;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("configuration ID", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().as("Configuration ID from evaluation context should be traced")
                    .containsEntry(TraceFields.CONFIGURATION_ID, Value.of("test-security"));
        }

        @Test
        @DisplayName("invocation with arguments and fresh flag together")
        void whenAttributeWithArgumentsAndFresh_thenBothAreTraced() {
            val policy  = """
                    policy "args-and-fresh"
                    permit where subject.<miskatonic.cultistRank("Deep Ones")[{fresh: true, initialTimeOutMs: 7500}]> != "unknown";
                    """;
            val subject = Value.of("Zadok Allen");

            val traced = evaluatePolicyWithPip(policy, Map.of("subject", subject));

            printDecision("arguments with fresh flag", traced);

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            val invocation     = (ObjectValue) firstAttribute.get(TraceFields.INVOCATION);
            assertThat(invocation).isNotNull().containsEntry(TraceFields.ENTITY, Value.of("Zadok Allen"))
                    .containsEntry(TraceFields.FRESH, Value.TRUE)
                    .containsEntry(TraceFields.INITIAL_TIMEOUT, Value.of(7500));

            val arguments = (ArrayValue) invocation.get(TraceFields.ARGUMENTS);
            assertThat(arguments).hasSize(1).containsExactly(Value.of("Deep Ones"));
        }

        private ObjectValue findAttributeByName(ArrayValue attributes, String name) {
            return attributes.stream().map(ObjectValue.class::cast).filter(attr -> {
                val attrName = extractAttributeName(attr);
                return attrName instanceof TextValue textValue && name.equals(textValue.value());
            }).findFirst().orElseThrow(() -> new AssertionError("Attribute not found: " + name));
        }

        private Value extractAttributeName(ObjectValue attr) {
            assertThat(attr).as("attribute record").isNotNull();
            val invocation = attr.get(TraceFields.INVOCATION);
            assertThat(invocation).as("invocation field").isNotNull().isInstanceOf(ObjectValue.class);
            val attrName = ((ObjectValue) invocation).get(TraceFields.ATTRIBUTE_NAME);
            assertThat(attrName).as("attribute name").isNotNull();
            return attrName;
        }

        @Test
        @DisplayName("streaming attribute emits multiple traced decisions")
        void whenStreamingAttribute_thenMultipleDecisionsEmitted() {
            val policy = """
                    policy "monitor-portal"
                    permit where <miskatonic.portalStatus> == "stable";
                    """;

            val compiled = compilePolicyWithPip(policy);
            val evalCtx  = createEvaluationContextWithPip(Map.of());

            assertThat(compiled.decisionExpression()).isInstanceOf(StreamExpression.class);

            val streamExpr = (StreamExpression) compiled.decisionExpression();

            StepVerifier.create(streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)))
                    .assertNext(traced -> {
                        printDecision("streaming attribute (1st)", traced);
                        assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
                        assertThat(getAttributes(traced)).isNotEmpty();
                    }).assertNext(traced -> {
                        printDecision("streaming attribute (2nd)", traced);
                        assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
                    }).assertNext(traced -> {
                        printDecision("streaming attribute (3rd)", traced);
                        assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
                    }).thenCancel().verify();
        }

        @Test
        @DisplayName("attribute value is captured in trace")
        void whenAttributeAccessed_thenValueIsCaptured() {
            val policy = """
                    policy "check-grimoire-count"
                    permit where <miskatonic.grimoireCount> > 5;
                    """;

            val traced = evaluatePolicyWithPip(policy, Map.of());

            printDecision("attribute value captured", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAttributes(traced)).isNotEmpty();

            val firstAttribute = (ObjectValue) getAttributes(traced).getFirst();
            assertThat(firstAttribute).containsEntry(TraceFields.VALUE, Value.of(13));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("attributePreservedCases")
        @DisplayName("attributes are preserved in various scenarios")
        void whenAttributeInVariousScenarios_thenAttributePreserved(String description, String policy,
                Map<String, Value> variables, Decision expectedDecision, boolean expectErrors) {
            val traced = evaluatePolicyWithPip(policy, variables);

            printDecision(description, traced);

            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            if (expectErrors) {
                assertThat(getErrors(traced)).isNotEmpty();
            }
            assertThat(getAttributes(traced)).as("Attributes should be preserved").isNotEmpty();
        }

        static Stream<Arguments> attributePreservedCases() {
            return Stream.of(arguments("body attr with constraint error", """
                    policy "body-attr-constraint-error"
                    permit where <miskatonic.starsAligned> == true;
                    obligation 1 / subject.zero
                    """, Map.of("subject", json("{\"zero\": 0}")), Decision.INDETERMINATE, true),

                    arguments("body attr evaluates to false (pure)", """
                            policy "body-attr-false"
                            permit where <miskatonic.starsAligned> == false;
                            """, Map.of(), Decision.NOT_APPLICABLE, false),

                    arguments("body attr false with constant constraints", """
                            policy "body-attr-false-constant"
                            permit where <miskatonic.starsAligned> == false;
                            obligation "constant-obligation"
                            """, Map.of(), Decision.NOT_APPLICABLE, false),

                    arguments("constraint attr with other constraint error", """
                            policy "constraint-attr-with-error"
                            permit
                            obligation { "grimoires": <miskatonic.grimoireCount> }
                            advice 1 / environment.zero
                            """, Map.of("environment", json("{\"zero\": 0}")), Decision.INDETERMINATE, true));
        }

        private CompiledPolicy compilePolicyWithPip(String policyText) {
            val sapl = PARSER.parse(policyText);
            return SaplCompiler.compileDocument(sapl, pipContext);
        }

        private Value evaluatePolicyWithPip(String policyText, Map<String, Value> variables) {
            val compiled = compilePolicyWithPip(policyText);
            val evalCtx  = createEvaluationContextWithPip(variables);
            return evaluateExpressionWithPip(compiled.decisionExpression(), evalCtx);
        }

        private Value evaluateExpressionWithPip(CompiledExpression expression, EvaluationContext evalCtx) {
            return switch (expression) {
            case Value value             -> value;
            case PureExpression pure     -> pure.evaluate(evalCtx);
            case StreamExpression stream ->
                stream.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)).blockFirst();
            };
        }

        private EvaluationContext createEvaluationContextWithPip(Map<String, Value> variables) {
            return EvaluationContext.of("test-pdp", "test-security", "test-sub", null, variables,
                    pipContext.getFunctionBroker(), pipContext.getAttributeBroker());
        }
    }

    @Nested
    @DisplayName("Coverage Trace Level")
    class CoverageTraceLevelTests {

        @Test
        @DisplayName("when coverage enabled, condition hit is recorded for true condition")
        void whenCoverageEnabled_thenTrueConditionHitRecorded() {
            val policy  = """
                    policy "elder-sign-ritual"
                    permit where subject.initiateLevel >= 3;
                    """;
            val subject = json("{\"initiateLevel\": 5}");

            val traced = evaluatePolicyWithCoverage(policy, Map.of("subject", subject));

            printDecision("coverage true condition", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(hasConditions(traced)).isTrue();
            assertThat(getConditions(traced)).hasSize(1);

            val condition = (ObjectValue) getConditions(traced).getFirst();
            assertThat(condition.get(TraceFields.STATEMENT_ID)).isEqualTo(Value.of(0));
            assertThat(condition.get(TraceFields.RESULT)).isEqualTo(Value.TRUE);
            // Verify full position data is captured for coverage highlighting
            assertThat(condition.get(TraceFields.START_LINE)).isInstanceOf(NumberValue.class);
            assertThat(condition.get(TraceFields.END_LINE)).isInstanceOf(NumberValue.class);
            assertThat(condition.get(TraceFields.START_CHAR)).isInstanceOf(NumberValue.class);
            assertThat(condition.get(TraceFields.END_CHAR)).isInstanceOf(NumberValue.class);
        }

        @Test
        @DisplayName("when coverage enabled, condition hit is recorded for false condition")
        void whenCoverageEnabled_thenFalseConditionHitRecorded() {
            val policy  = """
                    policy "forbidden-archive-access"
                    permit where subject.clearanceLevel >= 10;
                    """;
            val subject = json("{\"clearanceLevel\": 5}");

            val traced = evaluatePolicyWithCoverage(policy, Map.of("subject", subject));

            printDecision("coverage false condition", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(hasConditions(traced)).isTrue();
            assertThat(getConditions(traced)).hasSize(1);

            val condition = (ObjectValue) getConditions(traced).getFirst();
            assertThat(condition.get(TraceFields.STATEMENT_ID)).isEqualTo(Value.of(0));
            assertThat(condition.get(TraceFields.RESULT)).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("when coverage enabled, multiple condition hits are recorded")
        void whenCoverageEnabled_thenMultipleConditionHitsRecorded() {
            val policy  = """
                    policy "ritual-chamber-access"
                    permit
                    where
                        subject.role == "cultist";
                        subject.sanityLevel > 30;
                    """;
            val subject = json("{\"role\": \"cultist\", \"sanityLevel\": 50}");

            val traced = evaluatePolicyWithCoverage(policy, Map.of("subject", subject));

            printDecision("coverage multiple conditions", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getConditions(traced)).hasSize(2);

            val firstCondition = (ObjectValue) getConditions(traced).get(0);
            assertThat(firstCondition.get(TraceFields.STATEMENT_ID)).isEqualTo(Value.of(0));
            assertThat(firstCondition.get(TraceFields.RESULT)).isEqualTo(Value.TRUE);

            val secondCondition = (ObjectValue) getConditions(traced).get(1);
            assertThat(secondCondition.get(TraceFields.STATEMENT_ID)).isEqualTo(Value.of(1));
            assertThat(secondCondition.get(TraceFields.RESULT)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when coverage enabled, short-circuit stops at false condition")
        void whenCoverageEnabled_thenShortCircuitPreservesSemantics() {
            val policy  = """
                    policy "deep-one-temple"
                    permit
                    where
                        subject.isHybrid == true;
                        subject.gills == true;
                    """;
            val subject = json("{\"isHybrid\": false, \"gills\": true}");

            val traced = evaluatePolicyWithCoverage(policy, Map.of("subject", subject));

            printDecision("coverage short-circuit", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            // Only first condition evaluated due to short-circuit
            assertThat(getConditions(traced)).hasSize(1);

            val condition = (ObjectValue) getConditions(traced).getFirst();
            assertThat(condition.get(TraceFields.STATEMENT_ID)).isEqualTo(Value.of(0));
            assertThat(condition.get(TraceFields.RESULT)).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("when coverage enabled, target result is recorded for matching policy")
        void whenCoverageEnabled_thenTargetResultRecorded() {
            val policy  = """
                    policy "necronomicon-access"
                    permit where subject.clearance == "omega";
                    """;
            val subject = json("{\"clearance\": \"omega\"}");

            val traced = evaluatePolicyWithCoverage(policy, Map.of("subject", subject));

            printDecision("coverage target result", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(hasTargetResult(traced)).isTrue();
            assertThat(getTargetResult(traced)).isTrue();
        }

        @Test
        @DisplayName("when coverage disabled, no condition hits in trace")
        void whenCoverageDisabled_thenNoConditionHitsInTrace() {
            val policy  = """
                    policy "standard-evaluation"
                    permit where subject.role == "investigator";
                    """;
            val subject = json("{\"role\": \"investigator\"}");

            // Standard evaluation without coverage
            val traced = evaluatePolicyWithVariables(policy, Map.of("subject", subject));

            printDecision("standard (no coverage)", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(hasConditions(traced)).isFalse();
            assertThat(hasTargetResult(traced)).isFalse();
        }

        @Test
        @DisplayName("when coverage enabled with constant true condition, hit is recorded")
        void whenCoverageEnabled_thenConstantTrueConditionRecorded() {
            val policy = """
                    policy "always-permit-ritual"
                    permit where true;
                    """;

            val traced = evaluatePolicyWithCoverage(policy, Map.of());

            printDecision("coverage constant true", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getConditions(traced)).hasSize(1);

            val condition = (ObjectValue) getConditions(traced).getFirst();
            assertThat(condition.get(TraceFields.RESULT)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when coverage enabled with constant false condition, hit is recorded")
        void whenCoverageEnabled_thenConstantFalseConditionRecorded() {
            val policy = """
                    policy "never-permit-ritual"
                    permit where false;
                    """;

            val traced = evaluatePolicyWithCoverage(policy, Map.of());

            printDecision("coverage constant false", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(getConditions(traced)).hasSize(1);

            val condition = (ObjectValue) getConditions(traced).getFirst();
            assertThat(condition.get(TraceFields.RESULT)).isEqualTo(Value.FALSE);
        }

        private Value evaluatePolicyWithCoverage(String policyText, Map<String, Value> variables) {
            // Coverage is enabled at compile-time, not evaluation-time
            val coverageContext = new CompilationContext(context.getFunctionBroker(), context.getAttributeBroker(),
                    io.sapl.api.pdp.TraceLevel.COVERAGE);
            val sapl            = PARSER.parse(policyText);
            val compiled        = SaplCompiler.compileDocument(sapl, coverageContext);
            val evalCtx         = createEvaluationContext(variables);
            return evaluateExpression(compiled.decisionExpression(), evalCtx);
        }

        private EvaluationContext createEvaluationContext(Map<String, Value> variables) {
            return EvaluationContext.of("test-pdp", "test-security", "test-sub", null, variables,
                    context.getFunctionBroker(), context.getAttributeBroker(),
                    () -> java.time.Instant.now().toString());
        }
    }

    /**
     * Test Policy Information Point with Lovecraftian/Miskatonic theme.
     */
    @PolicyInformationPoint(name = "miskatonic")
    public static class MiskatonicPip {

        @EnvironmentAttribute
        public Flux<Value> starsAligned() {
            return Flux.just(Value.TRUE);
        }

        @EnvironmentAttribute
        public Flux<Value> grimoireCount() {
            return Flux.just(Value.of(13));
        }

        @EnvironmentAttribute
        public Flux<Value> portalStatus() {
            return Flux.interval(Duration.ofMillis(10)).take(3).map(i -> Value.of(i == 1 ? "stable" : "unstable"));
        }

        @Attribute
        public Flux<Value> sanityLevel(Value entity) {
            if (entity instanceof TextValue text) {
                return switch (text.value()) {
                case "Herbert West"    -> Flux.just(Value.of(75));
                case "Randolph Carter" -> Flux.just(Value.of(85));
                case "Wilbur Whateley" -> Flux.just(Value.of(15));
                default                -> Flux.just(Value.of(50));
                };
            }
            return Flux.just(Value.of(50));
        }

        @Attribute
        public Flux<Value> forbiddenKnowledge(Value entity) {
            return Flux.just(Value.of("Necronomicon excerpts"));
        }

        @EnvironmentAttribute
        public Flux<Value> ritualPower(Value baseStrength, Value modifier) {
            var base = baseStrength instanceof NumberValue n ? n.value().intValue() : 10;
            var mod  = modifier instanceof NumberValue n ? n.value().intValue() : 0;
            return Flux.just(Value.of(base + mod));
        }

        @Attribute
        public Flux<Value> cultistRank(Value entity, Value organization) {
            var entityName = entity instanceof TextValue t ? t.value() : "unknown";
            var orgName    = organization instanceof TextValue t ? t.value() : "unknown cult";
            return Flux.just(Value.of(entityName + " of " + orgName));
        }
    }

    private CompiledPolicy compilePolicy(String policyText) {
        val sapl = PARSER.parse(policyText);
        return SaplCompiler.compileDocument(sapl, context);
    }

    private Value evaluatePolicy(String policyText) {
        val compiled = compilePolicy(policyText);
        val evalCtx  = createEvaluationContext(Map.of());
        return evaluateExpression(compiled.decisionExpression(), evalCtx);
    }

    private Value evaluatePolicyWithVariables(String policyText, Map<String, Value> variables) {
        val compiled = compilePolicy(policyText);
        val evalCtx  = createEvaluationContext(variables);
        return evaluateExpression(compiled.decisionExpression(), evalCtx);
    }

    private Value evaluatePolicySet(String policySetText, EvaluationContext evalCtx) {
        val compiled = compilePolicy(policySetText);
        return evaluateExpression(compiled.decisionExpression(), evalCtx);
    }

    private Value evaluateExpression(CompiledExpression expression, EvaluationContext evalCtx) {
        return switch (expression) {
        case Value value             -> value;
        case PureExpression pure     -> pure.evaluate(evalCtx);
        case StreamExpression stream ->
            stream.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)).blockFirst();
        };
    }

    private EvaluationContext createEvaluationContext(Map<String, Value> variables) {
        return EvaluationContext.of("test-pdp", "test-security", "test-sub", null, variables,
                context.getFunctionBroker(), context.getAttributeBroker());
    }

    private static void printDecision(String testName, Value traced) {
        if (!DEBUG) {
            return;
        }
        System.err.println("=== " + testName + " ===");
        System.err.println(toPrettyString(traced));
        System.err.println();
    }
}
