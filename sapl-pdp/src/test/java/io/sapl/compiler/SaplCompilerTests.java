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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SaplCompilerTests {

    private static final SAPLParser   PARSER = new DefaultSAPLParser();
    private static CompilationContext context;
    private static EvaluationContext  evaluationContext;

    @BeforeAll
    static void setupContext() {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        context           = new CompilationContext(functionBroker, attributeBroker);
        evaluationContext = new EvaluationContext(null, null, null, null, functionBroker, attributeBroker);
    }

    // ==========================================================================
    // Basic Policy Compilation Tests
    // ==========================================================================

    @Test
    void whenSimplePermitPolicy_thenCompilesSuccessfully() {
        val source   = """
                policy "ancient rite of R'lyeh"
                permit
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.name()).isEqualTo("ancient rite of R'lyeh");
        assertThat(compiled.entitlement()).isEqualTo("PERMIT");
    }

    @Test
    void whenSimpleDenyPolicy_thenCompilesSuccessfully() {
        val source   = """
                policy "forbidden tome of Yog-Sothoth"
                deny
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.name()).isEqualTo("forbidden tome of Yog-Sothoth");
        assertThat(compiled.entitlement()).isEqualTo("DENY");
    }

    @Test
    void whenPolicyWithTarget_thenCompilesSuccessfully() {
        val source   = """
                policy "access to Miskatonic archives"
                permit subject == "librarian"
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.matchExpression()).isInstanceOf(PureExpression.class);
    }

    @Test
    void whenPolicyWithConstantTrueTarget_thenConstantFolds() {
        val source   = """
                policy "the stars are right"
                permit true
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled.matchExpression()).isEqualTo(Value.TRUE);
    }

    // ==========================================================================
    // Compilation Error Tests (parameterized)
    // ==========================================================================

    static Stream<Arguments> compilationErrorCases() {
        return Stream.of(arguments("target always false", "policy \"p\" permit false", "always evaluates to false"),
                arguments("target always error", "policy \"p\" permit 10/0 == 1", "error"),
                arguments("target always non-boolean", "policy \"p\" permit \"not a boolean\"", "non-Boolean"),
                arguments("duplicate variable in body", "policy \"p\" permit where var x = 1; var x = 2;",
                        "already defined"),
                arguments("body always non-boolean", "policy \"p\" permit where \"a string\";", "non-Boolean"),
                arguments("obligation always error", "policy \"p\" permit obligation 10/0",
                        "Constraint always evaluates to error"),
                arguments("duplicate global variable in set",
                        "set \"s\" first-applicable var x = 1; var x = 2; policy \"p\" permit", "already defined"),
                arguments("schema not object value", "subject enforced schema \"not an object\" policy \"p\" permit",
                        "must evaluate to ObjectValue"),
                arguments("policy set with constant false target",
                        "set \"s\" first-applicable for false policy \"p\" permit", "always evaluates to false"),
                arguments("policy set variable evaluates to error",
                        "set \"s\" first-applicable var x = 10/0; policy \"p\" permit", "error"),
                arguments("transformation always error", "policy \"p\" permit transform 10/0",
                        "Transformation always evaluates to error"),
                arguments("invalid syntax", "policy \"broken syntax permit", "parse"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("compilationErrorCases")
    void whenInvalidPolicy_thenThrowsCompilationError(String description, String source, String expectedSubstring) {
        assertThatThrownBy(() -> SaplCompiler.compile(source, context)).isInstanceOf(SaplCompilerException.class)
                .hasMessageContaining(expectedSubstring);
    }

    // ==========================================================================
    // Policy Body Tests (where clause)
    // ==========================================================================

    @Test
    void whenPolicyWithCondition_thenCompilesBody() {
        val source   = """
                policy "cultist verification"
                permit
                where
                    resource.clearanceLevel >= 5;
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isNotNull();
    }

    @Test
    void whenPolicyWithMultipleConditions_thenCombinesWithLazyAnd() {
        val source   = """
                policy "deep one ritual"
                permit
                where
                    subject.species == "deep_one";
                    action == "summon";
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    @Test
    void whenPolicyWithVariableDefinition_thenRegistersVariable() {
        val source   = """
                policy "sanity check"
                permit
                where
                    var sanityThreshold = 50;
                    resource.sanity >= sanityThreshold;
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    // ==========================================================================
    // Constraints Tests (obligations, advice, transformation)
    // ==========================================================================

    @Test
    void whenPolicyWithObligation_thenCompilesObligation() {
        val source   = """
                policy "containment protocol"
                permit
                obligation { "type": "log", "message": "Access granted to containment unit" }
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    @Test
    void whenPolicyWithAdvice_thenCompilesAdvice() {
        val source   = """
                policy "safety advisory"
                permit
                advice { "type": "warning", "message": "Do not gaze upon the artifact" }
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    @Test
    void whenPolicyWithTransformation_thenCompilesTransformation() {
        val source   = """
                policy "data redaction"
                permit
                transform resource |- { @.secret: filter.blacken }
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    // ==========================================================================
    // Policy Set Tests
    // ==========================================================================

    @ParameterizedTest(name = "policy set with {0} algorithm compiles")
    @MethodSource("combiningAlgorithms")
    void whenPolicySetWithCombiningAlgorithm_thenCompiles(String algorithmName, String algorithmSyntax) {
        val source   = """
                set "cult hierarchy"
                %s
                policy "inner circle"
                permit subject.rank == "high_priest"
                policy "outer circle"
                deny
                """.formatted(algorithmSyntax);
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.name()).isEqualTo("cult hierarchy");
        assertThat(compiled.entitlement()).isNull(); // Policy sets don't have entitlement
    }

    static Stream<Arguments> combiningAlgorithms() {
        return Stream.of(arguments("deny-overrides", "deny-overrides"),
                arguments("permit-overrides", "permit-overrides"), arguments("first-applicable", "first-applicable"),
                arguments("only-one-applicable", "only-one-applicable"),
                arguments("deny-unless-permit", "deny-unless-permit"),
                arguments("permit-unless-deny", "permit-unless-deny"));
    }

    @Test
    void whenPolicySetWithGlobalVariables_thenVariablesAvailableInPolicies() {
        val source   = """
                set "shoggoth handling procedures"
                first-applicable
                var hazardLevel = "EXTREME";
                policy "assess threat"
                permit hazardLevel == "EXTREME"
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    // ==========================================================================
    // Import Tests
    // ==========================================================================

    @Test
    void whenPolicyWithImport_thenResolvesFunction() {
        val source   = """
                import filter.blacken
                policy "data sanitization"
                permit
                transform resource |- { @.classified: blacken }
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    @Test
    void whenPolicyWithAliasedImport_thenResolvesAlias() {
        val source   = """
                import filter.blacken as redact
                policy "document processing"
                permit
                transform resource |- { @.topSecret: redact }
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
    }

    // ==========================================================================
    // Decision Generation Tests (parameterized)
    // ==========================================================================

    static Stream<Arguments> decisionGenerationCases() {
        return Stream.of(arguments("permit policy generates PERMIT", "policy \"p\" permit", "PERMIT"),
                arguments("deny policy generates DENY", "policy \"p\" deny", "DENY"),
                arguments("policy with false body generates NOT_APPLICABLE", "policy \"p\" permit where false;",
                        "NOT_APPLICABLE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decisionGenerationCases")
    void whenPolicyEvaluated_thenGeneratesExpectedDecision(String description, String source, String expectedDecision) {
        val compiled = SaplCompiler.compile(source, context);
        val decision = evaluateDecision(compiled);

        assertThat(decision).isInstanceOf(ObjectValue.class);
        val decisionObj = (ObjectValue) decision;
        assertThat(decisionObj.get("decision")).isEqualTo(Value.of(expectedDecision));
    }

    // ==========================================================================
    // Coverage Tracking Tests
    // ==========================================================================

    @Test
    void whenCoverageEnabled_thenRecordsConditionHits() {
        val coverageContext = new CompilationContext(context.getFunctionBroker(), context.getAttributeBroker(),
                TraceLevel.COVERAGE);
        val source          = """
                policy "traced access"
                permit
                where
                    true;
                """;
        val compiled        = SaplCompiler.compile(source, coverageContext);
        assertThat(compiled).isNotNull();

        // Evaluate and check that conditions are tracked
        val decision = evaluateDecision(compiled);
        assertThat(decision).isInstanceOf(ObjectValue.class);
        val decisionObj = (ObjectValue) decision;
        // The decision should contain trace information including condition hits
        assertThat(decisionObj.containsKey("conditions")).isTrue();
    }

    // ==========================================================================
    // Metadata Propagation Tests
    // ==========================================================================

    @Test
    void whenPolicyEvaluated_thenMetadataIsPropagated() {
        val source   = """
                policy "metadata test"
                permit
                obligation { "type": "notification" }
                """;
        val compiled = SaplCompiler.compile(source, context);
        val decision = evaluateDecision(compiled);

        assertThat(decision).isNotNull();
        assertThat(decision.metadata()).isNotNull();
    }

    // ==========================================================================
    // Schema Validation Tests
    // ==========================================================================

    @Test
    void whenPolicyWithEnforcedSchema_thenCompilesSchemaValidation() {
        val source   = """
                subject enforced schema { "type": "object" }
                policy "schema enforcement test"
                permit
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.matchExpression()).isInstanceOf(PureExpression.class);
    }

    @Test
    void whenPolicyWithNonEnforcedSchema_thenIgnoresSchema() {
        val source   = """
                subject schema { "type": "object" }
                policy "non-enforced schema test"
                permit
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.matchExpression()).isEqualTo(Value.TRUE);
    }

    // ==========================================================================
    // Policy Set with Target Expression Tests
    // ==========================================================================

    @Test
    void whenPolicySetWithForClause_thenCompilesTarget() {
        val source   = """
                set "cult operations center"
                first-applicable
                for action == "summon"
                policy "inner sanctum access"
                permit subject.rank == "high_priest"
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.name()).isEqualTo("cult operations center");
        assertThat(compiled.matchExpression()).isInstanceOf(PureExpression.class);
    }

    // ==========================================================================
    // Multiple Policies in Set Tests
    // ==========================================================================

    @Test
    void whenPolicySetWithMultiplePolicies_thenAllCompile() {
        val source   = """
                set "occult hierarchy"
                deny-overrides
                policy "outer circle"
                permit subject.clearance >= 1
                policy "inner circle"
                permit subject.clearance >= 5
                policy "innermost sanctum"
                deny subject.corrupted == true
                """;
        val compiled = SaplCompiler.compile(source, context);
        assertThat(compiled).isNotNull();
        assertThat(compiled.name()).isEqualTo("occult hierarchy");
    }

    // ==========================================================================
    // Entitlement Conversion Tests (parameterized)
    // ==========================================================================

    static Stream<Arguments> entitlementConversionCases() {
        return Stream.of(arguments("permit converts to PERMIT", "permit", Decision.PERMIT),
                arguments("deny converts to DENY", "deny", Decision.DENY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entitlementConversionCases")
    void whenEntitlement_thenConvertsToExpectedDecision(String description, String entitlement, Decision expected) {
        assertThat(SaplCompiler.decisionOf(parseEntitlement(entitlement))).isEqualTo(expected);
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private Value evaluateDecision(CompiledPolicy compiled) {
        val   matchExpr = compiled.matchExpression();
        Value matchResult;
        if (matchExpr instanceof Value val) {
            matchResult = val;
        } else if (matchExpr instanceof PureExpression pureExpr) {
            matchResult = pureExpr.evaluate(evaluationContext);
        } else {
            throw new IllegalStateException("Unexpected match expression type: " + matchExpr.getClass());
        }

        if (!Value.TRUE.equals(matchResult)) {
            return AuthorizationDecisionUtil.NOT_APPLICABLE;
        }

        val decisionExpr = compiled.decisionExpression();
        if (decisionExpr instanceof Value val) {
            return val;
        } else if (decisionExpr instanceof PureExpression pureExpr) {
            return pureExpr.evaluate(evaluationContext);
        } else if (decisionExpr instanceof StreamExpression streamExpr) {
            return streamExpr.stream().blockFirst(Duration.ofSeconds(5));
        }
        throw new IllegalStateException("Unexpected decision expression type: " + decisionExpr.getClass());
    }

    private io.sapl.grammar.antlr.SAPLParser.EntitlementContext parseEntitlement(String entitlement) {
        val source = """
                policy "test"
                %s
                """.formatted(entitlement);
        val sapl   = PARSER.parse(source);
        val policy = (io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext) sapl.policyElement();
        return policy.policy().entitlement();
    }

}
