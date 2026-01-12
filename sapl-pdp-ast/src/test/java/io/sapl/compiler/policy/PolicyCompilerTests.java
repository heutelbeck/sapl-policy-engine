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
package io.sapl.compiler.policy;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Policy;
import io.sapl.compiler.ast.SAPLCompiler;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive tests for PolicyCompiler covering all document type
 * permutations and evaluation behaviors. Test scenarios are themed around Terry
 * Pratchett's Discworld universe featuring the Ankh-Morpork guilds, Unseen
 * University, the City Watch, and various denizens of the Disc.
 */
@DisplayName("PolicyCompiler")
class PolicyCompilerTests {

    private static final AstTransformer TRANSFORMER = new AstTransformer();

    private static Policy parsePolicy(String policySource) {
        val document = SAPLCompiler.parse(policySource);
        val element  = document.policyElement();
        if (element instanceof PolicyOnlyElementContext policyOnly) {
            return (Policy) TRANSFORMER.visit(policyOnly.policy());
        }
        throw new IllegalArgumentException("Expected a single policy, not a policy set");
    }

    private static PolicyBody compileToDecisionMaker(String policySource) {
        return compileToDecisionMaker(policySource, compilationContext(ATTRIBUTE_BROKER));
    }

    private static PolicyBody compileToDecisionMaker(String policySource, CompilationContext ctx) {
        val policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, null, ctx).policyBody();
    }

    private static EvaluationContext rincewindContext() {
        return evaluationContext(parseSubscription("""
                {
                    "subject": "Rincewind",
                    "action": "flee",
                    "resource": "Luggage"
                }
                """));
    }

    @Nested
    @DisplayName("Target Expression Handling")
    class TargetExpressionHandlingTests {

        @Test
        @DisplayName("No target yields static PERMIT")
        void whenNoTarget_thenUnseenUniversityPermitsAll() {
            val policy   = """
                    policy "Unseen University Open Door"
                    permit
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("False target yields static NOT_APPLICABLE")
        void whenTargetIsFalse_thenPatricianPalaceGatesClosed() {
            val policy   = """
                    policy "Patrician Palace Sealed"
                    permit false
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("True target yields static PERMIT")
        void whenTargetIsTrue_thenMendedDrumOpen() {
            val policy   = """
                    policy "Mended Drum Open Hours"
                    permit true
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Pure target with pure body yields PurePolicyBody")
        void whenPureTargetWithBody_thenWizardGuildCheckProducesPureDocument() {
            val policy   = """
                    policy "Wizard Guild Verification"
                    permit subject.guild == "wizard"
                    where subject.level > 5;
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PurePolicyBody.class);
        }

        static Stream<Arguments> targetValidationErrorCases() {
            return Stream.of(arguments("Attribute in target throws SaplCompilerException", """
                    policy "History Monks Temporal Gate"
                    permit <time.now> != undefined
                    """, "time.now", "Attribute access is forbidden in target expressions"),
                    arguments("Relative accessor in target throws SaplCompilerException", """
                            policy "Auditor Invalid Query"
                            permit @.name == "Death"
                            """, null, "relative value accessor"),
                    arguments("Static error in target throws SaplCompilerException", """
                            policy "Hex Malfunction"
                            permit 1/0 == 0
                            """, null, "statically evaluates to an error"),
                    arguments("Non-boolean target throws SaplCompilerException", """
                            policy "Luggage Confused State"
                            permit "not a boolean"
                            """, null, "must evaluate to Boolean"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("targetValidationErrorCases")
        @DisplayName("Target validation errors")
        void targetValidationErrors(String scenario, String policySource, String attrName, String expectedMessage) {
            val ctx = attrName != null ? compilationContext(attributeBroker(attrName, Value.of("value")))
                    : compilationContext(ATTRIBUTE_BROKER);
            assertThatThrownBy(() -> compileToDecisionMaker(policySource, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining(expectedMessage);
        }
    }

    @Nested
    @DisplayName("Body Expression Handling")
    class BodyExpressionHandlingTests {

        @Test
        @DisplayName("Empty body permit yields static PERMIT")
        void whenEmptyBodyPermit_thenCityWatchPatrolApproved() {
            val policy   = """
                    policy "City Watch Patrol Authorization"
                    permit
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Empty body deny yields static DENY")
        void whenEmptyBodyDeny_thenAssassinsContractDenied() {
            val policy   = """
                    policy "Assassins Guild Contract Rejection"
                    deny
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("False body yields static NOT_APPLICABLE")
        void whenBodyFalse_thenThievesGuildMembershipFailed() {
            val policy   = """
                    policy "Thieves Guild Membership Verification"
                    permit
                    where false;
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Pure body without constraints yields SimplePurePolicyBody")
        void whenPureCondition_thenLibrarianCheckProducesSimplePurePolicy() {
            val policy   = """
                    policy "Librarian Species Verification"
                    permit
                    where subject.species == "orangutan";
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyCompiler.SimplePurePolicyBody.class);
        }

        @Test
        @DisplayName("Stream attribute in body yields StreamPolicyBody")
        void whenAttributeInBody_thenWeatherMonitoringProducesStream() {
            val policy   = """
                    policy "Ankh-Morpork Weather Advisory"
                    permit
                    where subject.<weather.current> == "foggy";
                    """;
            val ctx      = compilationContext(attributeBroker("weather.current", Value.of("foggy")));
            val compiled = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(StreamPolicyBody.class);
        }

        static Stream<Arguments> bodyValidationErrorCases() {
            return Stream.of(arguments("Relative accessor in body throws SaplCompilerException", """
                    policy "Auditor Invalid Inspection"
                    permit
                    where @.valid == true;
                    """, "relative value accessor"), arguments("Static error in body throws SaplCompilerException", """
                    policy "Hex Arithmetic Failure"
                    permit
                    where 1/0 == 0;
                    """, "statically evaluates to an error"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bodyValidationErrorCases")
        @DisplayName("Body validation errors")
        void bodyValidationErrors(String scenario, String policySource, String expectedMessage) {
            assertThatThrownBy(() -> compileToDecisionMaker(policySource)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining(expectedMessage);
        }
    }

    @Nested
    @DisplayName("Document Type Selection Based on Strata")
    class DocumentTypeSelectionTests {

        static Stream<Arguments> allStrataPermutations() {
            return Stream.of(
                    // Value stratum only
                    arguments("Value body with value constraints yields AuthorizationDecision",
                            "permit true obligation \"follow_owner\" advice \"dont_eat_anyone\" transform \"sapient_pearwood\"",
                            PolicyDecision.class, null),

                    // Pure body, no constraints -> SimplePurePolicyBody
                    arguments("Pure body without constraints yields SimplePurePolicyBody",
                            "permit where subject.hasHeadology == true;", PolicyCompiler.SimplePurePolicyBody.class,
                            null),

                    // Value body, Pure obligation -> PurePolicyBody
                    arguments("Value body with pure obligation yields PurePolicyBody",
                            "permit where true; obligation subject.kitchen", PolicyCompiler.PurePolicyBody.class, null),

                    // Value body, Pure advice -> PurePolicyBody
                    arguments("Value body with pure advice yields PurePolicyBody",
                            "permit where true; advice subject.herbLore", PolicyCompiler.PurePolicyBody.class, null),

                    // Value body, Pure transform -> PurePolicyBody
                    arguments("Value body with pure transform yields PurePolicyBody",
                            "permit where true; transform subject.census", PolicyCompiler.PurePolicyBody.class, null),

                    // Pure body, Value constraints -> PurePolicyBody
                    arguments("Pure body with value obligation yields PurePolicyBody",
                            "permit where subject.age > 80; obligation \"record_saga\"",
                            PolicyCompiler.PurePolicyBody.class, null),

                    // Pure body, Pure obligation -> PurePolicyBody
                    arguments("Pure body with pure obligation yields PurePolicyBody",
                            "permit where subject.title == \"Archchancellor\"; obligation subject.staffLog",
                            PolicyCompiler.PurePolicyBody.class, null),

                    // Pure body, Stream obligation -> PureStreamPolicyBody
                    arguments("Pure body with stream obligation yields PureStreamPolicyBody",
                            "permit where subject.department == \"HEM\"; obligation <audit.hex>",
                            PolicyCompiler.PureStreamPolicyBody.class, "audit.hex"),

                    // Stream body, no constraints -> StreamPolicyBody
                    arguments("Stream body without constraints yields StreamPolicyBody",
                            "permit where subject.<death.available> == true;", PolicyCompiler.StreamPolicyBody.class,
                            "death.available"),

                    // Stream body, Value constraints -> StreamValuePolicyBody
                    arguments("Stream body with value constraints yields StreamValuePolicyBody",
                            "permit where subject.<hogfather.status> == \"delivering\"; obligation \"wrap_gift\" advice \"check_list\"",
                            PolicyCompiler.StreamValuePolicyBody.class, "hogfather.status"),

                    // Stream body, Pure constraints -> StreamPurePolicyBody
                    arguments("Stream body with pure obligation yields StreamPurePolicyBody",
                            "permit where subject.<location.current> == \"running\"; obligation subject.equipment",
                            PolicyCompiler.StreamPurePolicyBody.class, "location.current"),

                    // Stream body, Stream constraints -> StreamStreamPolicyBody
                    arguments("Stream body with stream obligation yields StreamStreamPolicyBody",
                            "permit where subject.<guild.war> == \"active\"; obligation <audit.combat>",
                            PolicyCompiler.StreamStreamPolicyBody.class, "guild.war,audit.combat"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("allStrataPermutations")
        @DisplayName("Stratum combinations produce correct document types")
        void stratumCombinationsProduceCorrectTypes(String scenario, String policyBody,
                Class<? extends PolicyBody> expectedType, String attrNames) {
            val                policy = "policy \"" + scenario + "\" " + policyBody;
            CompilationContext ctx;
            if (attrNames != null) {
                val attrs = attrNames.split(",");
                if (attrs.length == 1) {
                    ctx = compilationContext(attributeBroker(attrs[0], Value.TRUE));
                } else {
                    ctx = compilationContext(attributeBroker(Map.of(attrs[0], new Value[] { Value.TRUE }, attrs[1],
                            new Value[] { Value.of("logged") })));
                }
            } else {
                ctx = compilationContext(ATTRIBUTE_BROKER);
            }
            val compiled = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(expectedType);
        }

        static Stream<Arguments> strataLiftingWithMixedConstraints() {
            return Stream.of(
                    arguments("Pure obligation with value advice yields PurePolicyBody",
                            "permit where true; obligation subject.organ advice \"wash_hands\"",
                            PolicyCompiler.PurePolicyBody.class),
                    arguments("Value obligation with pure advice yields PurePolicyBody",
                            "permit where true; obligation \"show_certificate\" advice subject.species",
                            PolicyCompiler.PurePolicyBody.class),
                    arguments("Pure body with pure obligation and transform yields PurePolicyBody",
                            "permit where subject.clearance > 9; obligation subject.log transform subject.redacted",
                            PolicyCompiler.PurePolicyBody.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("strataLiftingWithMixedConstraints")
        @DisplayName("Mixed constraint strata lift correctly")
        void mixedConstraintStrataLiftCorrectly(String scenario, String policyBody,
                Class<? extends PolicyBody> expectedType) {
            val policy   = "policy \"Strata Test\" " + policyBody;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(expectedType);
        }
    }

    @Nested
    @DisplayName("Pure Document Evaluation")
    class PureDocumentEvaluationTests {

        @Test
        @DisplayName("SimplePurePolicyBody evaluates to PERMIT when body condition matches")
        void whenRincewindFleeing_thenFlightPermitted() {
            val policy   = """
                    policy "Rincewind Emergency Escape Protocol"
                    permit
                    where subject == "Rincewind";
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyCompiler.SimplePurePolicyBody.class);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("SimplePurePolicyBody evaluates to NOT_APPLICABLE when body condition fails")
        void whenNotTwoflower_thenTouristVisaDenied() {
            val policy   = """
                    policy "Twoflower Tourist Visa Check"
                    permit
                    where subject == "Twoflower";
                    """;
            val compiled = compileToDecisionMaker(policy);
            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());
            assertThat(decision).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) decision).decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field access in body evaluates to NOT_APPLICABLE")
        void whenUndefinedFieldAccess_thenLuggageInventoryNotApplicable() {
            val policy   = """
                    policy "Luggage Inventory Check"
                    permit
                    where subject.nonexistent.compartment == true;
                    """;
            val compiled = compileToDecisionMaker(policy);
            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());
            assertThat(decision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("PurePolicyBody evaluates obligation, advice, and transform correctly")
        void whenVetinariWithConstraints_thenAllEvaluated() {
            val policy   = """
                    policy "Lord Vetinari Executive Order"
                    permit
                    where subject == "Rincewind";
                    obligation "report_to_patrician"
                    advice "avoid_politics"
                    transform "classified"
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyCompiler.PurePolicyBody.class);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());

            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).isNotNull().isNotEmpty();
            assertThat(decision.advice()).isNotNull().isNotEmpty();
            assertThat(decision.resource()).isEqualTo(Value.of("classified"));
        }

        @Test
        @DisplayName("Deny policy evaluates to DENY when body condition matches")
        void whenForbiddenSection_thenRincewindDenied() {
            val policy   = """
                    policy "Forbidden Octavo Section"
                    deny
                    where subject == "Rincewind";
                    """;
            val compiled = compileToDecisionMaker(policy);
            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());
            assertThat(decision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("Single obligation wrapped in array")
        void whenSingleStringObligation_thenWrappedInArray() {
            val policy   = """
                    policy "Clacks Tower Duty Roster"
                    permit
                    where subject == "Rincewind";
                    obligation "send_overhead"
                    """;
            val compiled = compileToDecisionMaker(policy);
            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(rincewindContext());
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Stream Document Evaluation")
    class StreamDocumentEvaluationTests {

        @Test
        @DisplayName("StreamPolicyBody emits decisions on attribute state changes")
        void whenLuggageProximity_thenEmitsOnChanges() {
            val policy     = """
                    policy "Luggage Proximity Alert"
                    permit
                    where subject.<luggage.nearby> == true;
                    """;
            val attrBroker = attributeBroker("luggage.nearby", Value.TRUE, Value.FALSE, Value.TRUE);
            val ctx        = compilationContext(attrBroker);
            val compiled   = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(PolicyCompiler.StreamPolicyBody.class);

            val subscription = parseSubscription("""
                    {"subject": "Rincewind", "action": "flee", "resource": "Luggage"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);
            val streamDoc    = (StreamPolicyBody) compiled;
            StepVerifier.create(streamDoc.stream().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.PERMIT))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.NOT_APPLICABLE))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
        }

        @Test
        @DisplayName("StreamValuePolicyBody includes static constraints in emissions")
        void whenCarrotPatrol_thenStaticConstraintsIncluded() {
            val policy     = """
                    policy "Captain Carrot Patrol Protocol"
                    permit
                    where subject.<patrol.active> == true;
                    obligation "log_patrol"
                    advice "stay_polite"
                    """;
            val attrBroker = attributeBroker("patrol.active", Value.TRUE);
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(PolicyCompiler.StreamValuePolicyBody.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Carrot", "action": "patrol", "resource": "Ankh-Morpork"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(tad -> {
                assertThat(tad.decision()).isEqualTo(Decision.PERMIT);
                assertThat(tad.obligations()).isNotNull().isNotEmpty();
                assertThat(tad.advice()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPurePolicyBody evaluates pure constraints per emission")
        void whenVimesDutyStatus_thenPureConstraintsEvaluatedPerEmission() {
            val policy     = """
                    policy "Commander Vimes Duty Protocol"
                    permit
                    where subject.<duty.status> == "on_duty";
                    obligation subject
                    """;
            val attrBroker = attributeBroker("duty.status", Value.of("on_duty"));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(PolicyCompiler.StreamPurePolicyBody.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Vimes", "action": "command", "resource": "City Watch"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(tad -> {
                assertThat(tad.decision()).isEqualTo(Decision.PERMIT);
                assertThat(tad.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamStreamPolicyBody combines body and constraint streams")
        void whenGuildMembership_thenAllStreamsCombined() {
            val policy     = """
                    policy "Guild Membership Continuous Verification"
                    permit
                    where subject.<guild.active> == true;
                    obligation <audit.guild>
                    """;
            val attrBroker = attributeBroker(Map.of("guild.active", new Value[] { Value.TRUE }, "audit.guild",
                    new Value[] { Value.of("logged") }));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(PolicyCompiler.StreamStreamPolicyBody.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Moist", "action": "join", "resource": "Guild"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(tad -> {
                assertThat(tad.decision()).isEqualTo(Decision.PERMIT);
                assertThat(tad.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPolicyBody emits NOT_APPLICABLE when body becomes false")
        void whenBodyBecomesFalse_thenAccessRevoked() {
            val policy     = """
                    policy "Dynamic Guild Access Revocation"
                    permit
                    where subject.<access.valid> == true;
                    """;
            val attrBroker = attributeBroker("access.valid", Value.TRUE, Value.FALSE);

            val decisions = evaluatePolicy("""
                    {"subject": "Visitor", "action": "access", "resource": "Guild Hall"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.PERMIT))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.NOT_APPLICABLE)).verifyComplete();
        }

        @Test
        @DisplayName("Attribute error in body produces INDETERMINATE")
        void whenAttributeError_thenIndeterminateDecision() {
            val policy     = """
                    policy "Clacks Network Error Handling"
                    permit
                    where subject.<clacks.signal> == true;
                    """;
            val attrBroker = attributeBroker("clacks.signal", Value.error("GNU Terry Pratchett"));

            val decisions = evaluatePolicy("""
                    {"subject": "operator", "action": "transmit", "resource": "Clacks Tower"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions)
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.INDETERMINATE)).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Constraint Error Handling")
    class ConstraintErrorHandlingTests {

        static Stream<Arguments> constraintErrorCases() {
            return Stream.of(arguments("Static error in transform throws SaplCompilerException", """
                    policy "Hex Overflow Error"
                    permit
                    transform 1/0
                    """, "Transformation", "statically evaluates to an error"),
                    arguments("Relative accessor in obligation throws SaplCompilerException", """
                            policy "Igor Organ Tracking Error"
                            permit
                            obligation @.organ
                            """, "Obligation", "@ or #"),
                    arguments("Relative accessor in transform throws SaplCompilerException", """
                            policy "Vetinari Report Redaction Error"
                            permit
                            transform @.filtered
                            """, "Transformation", "@ or #"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("constraintErrorCases")
        @DisplayName("Constraint validation errors")
        void constraintValidationErrors(String scenario, String policySource, String expectedType,
                String expectedMessage) {
            assertThatThrownBy(() -> compileToDecisionMaker(policySource)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining(expectedType).hasMessageContaining(expectedMessage);
        }
    }

    @Nested
    @DisplayName("Target Expression Evaluation in Documents")
    class TargetExpressionEvaluationTests {

        @Test
        @DisplayName("PurePolicyBody exposes target expression")
        void whenPureTarget_thenAccessibleFromDocument() {
            val policy   = """
                    policy "Thieves Guild Membership Check"
                    permit subject.guild == "thieves"
                    where subject.level > 1;
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PurePolicyBody.class);

            val pureDoc = (PurePolicyBody) compiled;
            assertThat(pureDoc.targetExpression()).isNotNull();
        }

        @Test
        @DisplayName("Constant true target stored as Value.TRUE in document")
        void whenConstantTrueTarget_thenStoredAsValueTrue() {
            val policy   = """
                    policy "Universal Disc Access"
                    permit true
                    where subject == "Rincewind";
                    """;
            val compiled = compileToDecisionMaker(policy);
            val pureDoc  = (PurePolicyBody) compiled;
            assertThat(pureDoc.targetExpression()).isEqualTo(Value.TRUE);
        }
    }

    @Nested
    @DisplayName("Discworld Lore-Accurate Scenarios")
    class DiscworldLoreAccurateScenariosTests {

        @Test
        @DisplayName("PurePolicyBody with target, body, and constraints evaluates correctly")
        void librarianGrantsBookAccess() {
            val policy   = """
                    policy "Unseen University Library Access Control"
                    permit subject.title == "Librarian"
                    where
                        subject.species == "orangutan";
                        subject.hasLibraryCard == true;
                    obligation "stamp_return_date"
                    advice "ook"
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyCompiler.PurePolicyBody.class);

            val subscription = parseSubscription("""
                    {
                        "subject": {
                            "title": "Librarian",
                            "species": "orangutan",
                            "hasLibraryCard": true
                        },
                        "action": "borrow",
                        "resource": "Octavo Section"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(ctx);
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).isNotEmpty();
            assertThat(decision.advice()).isNotEmpty();
        }

        @Test
        @DisplayName("Deny policy with target and body evaluates to DENY")
        void vimesDeniesShadePatrol() {
            val policy   = """
                    policy "City Watch Commander Patrol Restriction"
                    deny subject.rank == "Commander"
                    where
                        subject.onDuty == true;
                        subject.patrolArea == "Shades";
                    advice "bring_entire_watch"
                    """;
            val compiled = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {
                        "subject": {
                            "rank": "Commander",
                            "onDuty": true,
                            "patrolArea": "Shades"
                        },
                        "action": "patrol",
                        "resource": "The Shades"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(ctx);
            assertThat(decision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("StreamStreamPolicyBody with stream body and stream constraints compiles correctly")
        void assassinsGuildContractMonitoring() {
            val policy   = """
                    policy "Assassins Guild Active Contract Protocol"
                    permit
                    where
                        subject.guild == "Assassins";
                        subject.<contract.status> == "active";
                    obligation <audit.inhumation>
                    transform "CLASSIFIED"
                    """;
            val ctx      = compilationContext(attributeBroker(Map.of("contract.status",
                    new Value[] { Value.of("active") }, "audit.inhumation", new Value[] { Value.of("recorded") })));
            val compiled = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(PolicyCompiler.StreamStreamPolicyBody.class);
        }

        @Test
        @DisplayName("PurePolicyBody with undefined check in body evaluates correctly")
        void deathVerifiesDutyAssignment() {
            val policy   = """
                    policy "DEATH Duty Verification Protocol"
                    permit subject.name == "DEATH"
                    where
                        subject.currentTask != undefined;
                    obligation "update_lifetimers"
                    """;
            val compiled = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {
                        "subject": {
                            "name": "DEATH",
                            "currentTask": "collect_soul"
                        },
                        "action": "reap",
                        "resource": "mortal soul"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(ctx);
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("PurePolicyBody with object transform in applicableDecision")
        void vetinariSurveillanceAccess() {
            val policy   = """
                    policy "Patrician Surveillance Network Access"
                    permit
                    where
                        subject == "Lord Vetinari";
                        action == "observe";
                    transform {
                        "accessLevel": "omniscient",
                        "clearance": "absolute"
                    }
                    """;
            val compiled = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {
                        "subject": "Lord Vetinari",
                        "action": "observe",
                        "resource": "Ankh-Morpork"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(ctx);
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditionsTests {

        @Test
        @DisplayName("Multiple obligations combined into array")
        void whenMultipleObligations_thenIgorCombinesInArray() {
            val policy   = """
                    policy "Igor Medical Procedure Checklist"
                    permit
                    obligation "sterilize_tools"
                    obligation "check_lightning_rod"
                    obligation "prepare_operating_table"
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);

            val decision = (PolicyDecision) compiled;
            assertThat(decision.obligations()).hasSize(3);
        }

        @Test
        @DisplayName("Multiple advice combined into array")
        void whenMultipleAdvice_thenWitchesCombineCorrectly() {
            val policy   = """
                    policy "Witch Coven Guidance Protocol"
                    permit
                    advice "trust_headology"
                    advice "avoid_cackling"
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);

            val decision = (PolicyDecision) compiled;
            assertThat(decision.advice()).hasSize(2);
        }

        @Test
        @DisplayName("Empty body with constraints yields static applicableDecision with obligations")
        void whenEmptyBodyWithConstraints_thenClacksObligationsEvaluated() {
            val policy   = """
                    policy "Clacks Tower Always-On Protocol"
                    permit
                    obligation "gnu_terry_pratchett"
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);

            val decision = (PolicyDecision) compiled;
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(decision.obligations()).isNotEmpty();
        }

        @Test
        @DisplayName("Complex nested object transform preserved in applicableDecision")
        void whenComplexTransformation_thenCensusDataPreserved() {
            val policy   = """
                    policy "Ankh-Morpork Census Record"
                    permit
                    transform {
                        "population": {
                            "humans": 100000,
                            "dwarfs": 50000,
                            "trolls": 20000,
                            "undead": "unknown"
                        }
                    }
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);

            val decision = (PolicyDecision) compiled;
            assertThat(decision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }

        @Test
        @DisplayName("Boolean short-circuit prevents evaluation error in body")
        void whenShortCircuit_thenHexOverflowPrevented() {
            val policy   = """
                    policy "Hex Safety Short-Circuit"
                    permit
                    where
                        false && (1/0 == 0);
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PolicyDecision.class);
            assertThat(((PolicyDecision) compiled).decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field comparison with undefined evaluates to PERMIT")
        void whenUndefinedField_thenHandledGracefully() {
            val policy   = """
                    policy "Morporkian Citizen Records"
                    permit
                    where subject.taxRecord == undefined;
                    """;
            val compiled = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Nobby"}, "action": "verify", "resource": "citizenship"}
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDoc  = (PurePolicyBody) compiled;
            val decision = pureDoc.evaluateBody(ctx);
            assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("PureStreamPolicyBody Specific Behaviors")
    class PureStreamPolicyBodyBodySpecificBehaviorsTests {

        @Test
        @DisplayName("PureStreamPolicyBody evaluates pure body with stream constraints")
        void whenHistoryMonksStreaming_thenPureBodyEvaluated() {
            val policy     = """
                    policy "History Monks Time Stream Monitoring"
                    permit
                    where subject.name == "Lu-Tze";
                    obligation <temporal.audit>
                    """;
            val attrBroker = attributeBroker("temporal.audit", Value.of("time_recorded"));
            val ctx        = compilationContext(attrBroker);
            val compiled   = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(PolicyCompiler.PureStreamPolicyBody.class);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Lu-Tze"}, "action": "sweep", "resource": "time stream"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDoc = (StreamPolicyBody) compiled;
            StepVerifier.create(streamDoc.stream().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns NOT_APPLICABLE when pure body is false")
        void whenPureBodyFalse_thenSusanDenied() {
            val policy     = """
                    policy "Susan Sto Helit Death Duty Check"
                    permit
                    where subject.name == "DEATH";
                    obligation <death.audit>
                    """;
            val attrBroker = attributeBroker("death.audit", Value.of("logged"));
            val ctx        = compilationContext(attrBroker);
            val compiled   = compileToDecisionMaker(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Susan"}, "action": "substitute", "resource": "Death Duty"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDoc = (StreamPolicyBody) compiled;
            StepVerifier.create(streamDoc.stream().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.NOT_APPLICABLE)).verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody includes pure advice in stream emissions")
        void whenPureAdviceWithStreamObligation_thenLiftedViaPathOfPureToStream() {
            val policy     = """
                    policy "Granny Weatherwax Mixed Constraint Protocol"
                    permit
                    obligation <coven.audit>
                    advice subject.headologyLevel
                    """;
            val attrBroker = attributeBroker("coven.audit", Value.of("recorded"));
            val ctx        = compilationContext(attrBroker);
            val compiled   = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(PolicyCompiler.PureStreamPolicyBody.class);

            val subscription = parseSubscription("""
                    {"subject": {"headologyLevel": 99}, "action": "use", "resource": "headology"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDoc = (StreamPolicyBody) compiled;
            StepVerifier.create(streamDoc.stream().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(tad -> {
                        assertThat(tad.decision()).isEqualTo(Decision.PERMIT);
                        assertThat(tad.advice()).contains(Value.of(99));
                    }).verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns INDETERMINATE on pure body evaluation error")
        void whenPureBodyError_thenRincewindMagicFails() {
            val policy     = """
                    policy "Rincewind Spell Attempt"
                    permit
                    where 1/subject.magicPower == 1;
                    obligation <spell.audit>
                    """;
            val attrBroker = attributeBroker("spell.audit", Value.of("recorded"));
            val ctx        = compilationContext(attrBroker);
            val compiled   = compileToDecisionMaker(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"magicPower": 0}, "action": "cast", "resource": "spell"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDoc = (StreamPolicyBody) compiled;
            StepVerifier.create(streamDoc.stream().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(tad -> assertThat(tad.decision()).isEqualTo(Decision.INDETERMINATE)).verifyComplete();
        }
    }
}
