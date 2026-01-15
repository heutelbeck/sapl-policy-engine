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
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
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

    private static DecisionMaker compileToDecisionMaker(String policySource) {
        return compileToDecisionMaker(policySource, compilationContext(ATTRIBUTE_BROKER));
    }

    private static DecisionMaker compileToDecisionMaker(String policySource, CompilationContext ctx) {
        val policy         = parsePolicy(policySource);
        val compiledPolicy = PolicyCompiler.compilePolicy(policy, ctx);
        return compiledPolicy.applicabilityAndDecision();
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
        void whenNoTarget_thenStaticPermit() {
            val policy        = """
                    policy "Unseen University Open Door"
                    permit
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("False body yields static NOT_APPLICABLE")
        void whenBodyIsFalse_thenStaticNotApplicable() {
            val policy        = """
                    policy "Patrician Palace Sealed"
                    permit
                    where false;
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("True body yields static PERMIT")
        void whenBodyIsTrue_thenMendedDrumOpen() {
            val policy        = """
                    policy "Mended Drum Open Hours"
                    permit
                    where true;
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Pure body yields PureDecisionMaker")
        void whenPureBody_thenPureDecisionMaker() {
            val policy   = """
                    policy "Wizard Guild Verification"
                    permit
                    where subject.guild == "wizard";
                          subject.level > 5;
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PureDecisionMaker.class);
        }

        // Note: Target expressions removed from policies - conditions are in where
        // clause
        // Static errors in body become INDETERMINATE at compile time
    }

    @Nested
    @DisplayName("Body Expression Handling")
    class BodyExpressionHandlingTests {

        @Test
        @DisplayName("Empty body permit yields static PERMIT")
        void whenEmptyBodyPermit_thenStaticPermit() {
            val policy        = """
                    policy "City Watch Patrol Authorization"
                    permit
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Empty body deny yields static DENY")
        void whenEmptyBodyDeny_thenAssassinsContractDenied() {
            val policy        = """
                    policy "Assassins Guild Contract Rejection"
                    deny
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("False body yields static NOT_APPLICABLE")
        void whenBodyFalse_thenStaticNotApplicable() {
            val policy        = """
                    policy "Thieves Guild Membership Verification"
                    permit
                    where false;
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Pure body without constraints yields PureDecisionMaker")
        void whenPureCondition_thenPureDecisionMaker() {
            val policy   = """
                    policy "Librarian Species Verification"
                    permit
                    where subject.species == "orangutan";
                    """;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(PureDecisionMaker.class);
        }

        @Test
        @DisplayName("Stream attribute in body yields StreamPolicyBodyPolicy")
        void whenAttributeInBody_thenWeatherMonitoringProducesStream() {
            val policy   = """
                    policy "Ankh-Morpork Weather Advisory"
                    permit
                    where subject.<weather.current> == "foggy";
                    """;
            val ctx      = compilationContext(attributeBroker("weather.current", Value.of("foggy")));
            val compiled = compileToDecisionMaker(policy, ctx);
            assertThat(compiled).isInstanceOf(StreamDecisionMaker.class);
        }

        @Test
        @DisplayName("Static error in body yields static INDETERMINATE")
        void whenStaticErrorInBody_thenStaticIndeterminate() {
            val policy        = """
                    policy "Hex Arithmetic Failure"
                    permit
                    where 1/0 == 0;
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Document Type Selection Based on Strata")
    class DocumentTypeSelectionTests {

        static Stream<Arguments> allStrataPermutations() {
            return Stream.of(
                    // Value stratum only
                    arguments("Value body with value constraints yields AuthorizationDecision",
                            "permit where true; obligation \"follow_owner\" advice \"dont_eat_anyone\" transform \"sapient_pearwood\"",
                            PolicyDecision.class, null),

                    // Pure body, no constraints -> PureDecisionMaker
                    // (ApplicabilityCheckingPureDecisionMaker)
                    arguments("Pure body without constraints yields PureDecisionMaker",
                            "permit where subject.hasHeadology == true;", PureDecisionMaker.class, null),

                    // Value body, Pure obligation -> PurePolicyBodyDecisionMaker
                    arguments("Value body with pure obligation yields PurePolicyBodyDecisionMaker",
                            "permit where true; obligation subject.kitchen", PureDecisionMaker.class, null),

                    // Value body, Pure advice -> PurePolicyBodyDecisionMaker
                    arguments("Value body with pure advice yields PurePolicyBodyDecisionMaker",
                            "permit where true; advice subject.herbLore", PureDecisionMaker.class, null),

                    // Value body, Pure transform -> PurePolicyBodyDecisionMaker
                    arguments("Value body with pure transform yields PurePolicyBodyDecisionMaker",
                            "permit where true; transform subject.census", PureDecisionMaker.class, null),

                    // Pure body, Value constraints -> PurePolicyBodyDecisionMaker
                    arguments("Pure body with value obligation yields PurePolicyBodyDecisionMaker",
                            "permit where subject.age > 80; obligation \"record_saga\"", PureDecisionMaker.class, null),

                    // Pure body, Pure obligation -> PurePolicyBodyDecisionMaker
                    arguments("Pure body with pure obligation yields PurePolicyBodyDecisionMaker",
                            "permit where subject.title == \"Archchancellor\"; obligation subject.staffLog",
                            PureDecisionMaker.class, null),

                    // Pure body, Stream obligation -> PureStreamPolicyBody
                    arguments("Pure body with stream obligation yields PureStreamPolicyBody",
                            "permit where subject.department == \"HEM\"; obligation <audit.hex>",
                            StreamDecisionMaker.class, "audit.hex"),

                    // Stream body, no constraints -> StreamPolicyBodyPolicy
                    arguments("Stream body without constraints yields StreamPolicyBodyPolicy",
                            "permit where subject.<death.available> == true;", StreamDecisionMaker.class,
                            "death.available"),

                    // Stream body, Value constraints -> StreamValuePolicyBodyPolicy
                    arguments("Stream body with value constraints yields StreamValuePolicyBodyPolicy",
                            "permit where subject.<hogfather.status> == \"delivering\"; obligation \"wrap_gift\" advice \"check_list\"",
                            StreamDecisionMaker.class, "hogfather.status"),

                    // Stream body, Pure constraints -> StreamPurePolicyBody
                    arguments("Stream body with pure obligation yields StreamPurePolicyBody",
                            "permit where subject.<location.current> == \"running\"; obligation subject.equipment",
                            StreamDecisionMaker.class, "location.current"),

                    // Stream body, Stream constraints -> StreamStreamPolicyBodyPolicy
                    arguments("Stream body with stream obligation yields StreamStreamPolicyBodyPolicy",
                            "permit where subject.<guild.war> == \"active\"; obligation <audit.combat>",
                            StreamDecisionMaker.class, "guild.war,audit.combat"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("allStrataPermutations")
        @DisplayName("Stratum combinations produce correct document types")
        void stratumCombinationsProduceCorrectTypes(String scenario, String policyBody,
                Class<? extends DecisionMaker> expectedType, String attrNames) {
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
            return Stream.of(arguments("Pure obligation with value advice yields PurePolicyBodyDecisionMaker",
                    "permit where true; obligation subject.organ advice \"wash_hands\"", PureDecisionMaker.class),
                    arguments("Value obligation with pure advice yields PurePolicyBodyDecisionMaker",
                            "permit where true; obligation \"show_certificate\" advice subject.species",
                            PureDecisionMaker.class),
                    arguments("Pure body with pure obligation and transform yields PurePolicyBodyDecisionMaker",
                            "permit where subject.clearance > 9; obligation subject.log transform subject.redacted",
                            PureDecisionMaker.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("strataLiftingWithMixedConstraints")
        @DisplayName("Mixed constraint strata lift correctly")
        void mixedConstraintStrataLiftCorrectly(String scenario, String policyBody,
                Class<? extends DecisionMaker> expectedType) {
            val policy   = "policy \"Strata Test\" " + policyBody;
            val compiled = compileToDecisionMaker(policy);
            assertThat(compiled).isInstanceOf(expectedType);
        }
    }

    @Nested
    @DisplayName("Pure Document Evaluation")
    class PureDocumentEvaluationTests {

        @Test
        @DisplayName("PureDecisionMaker evaluates to PERMIT when body condition matches")
        void whenBodyConditionMatches_thenPermit() {
            val policy        = """
                    policy "Rincewind Emergency Escape Protocol"
                    permit
                    where subject == "Rincewind";
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PureDecisionMaker.class);

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("SimplePurePolicyBodyDecisionMaker evaluates to NOT_APPLICABLE when body condition fails")
        void whenBodyConditionFails_thenNotApplicable() {
            val policy                = """
                    policy "Twoflower Tourist Visa Check"
                    permit
                    where subject == "Twoflower";
                    """;
            val decisionMaker         = compileToDecisionMaker(policy);
            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(pdpDecision).isInstanceOf(PolicyDecision.class);
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field access in body evaluates to NOT_APPLICABLE")
        void whenUndefinedFieldAccess_thenNotApplicable() {
            val policy                = """
                    policy "Luggage Inventory Check"
                    permit
                    where subject.nonexistent.compartment == true;
                    """;
            val decisionMaker         = compileToDecisionMaker(policy);
            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("PurePolicyBodyDecisionMaker evaluates obligation, advice, and transform correctly")
        void whenPureConstraints_thenAllEvaluated() {
            val policy        = """
                    policy "Lord Vetinari Executive Order"
                    permit
                    where subject == "Rincewind";
                    obligation "report_to_patrician"
                    advice "avoid_politics"
                    transform "classified"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PureDecisionMaker.class);

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();

            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotNull().isNotEmpty();
            assertThat(authorizationDecision.advice()).isNotNull().isNotEmpty();
            assertThat(authorizationDecision.resource()).isEqualTo(Value.of("classified"));
        }

        @Test
        @DisplayName("Deny policy evaluates to DENY when body condition matches")
        void whenDenyPolicyBodyMatches_thenDeny() {
            val policy                = """
                    policy "Forbidden Octavo Section"
                    deny
                    where subject == "Rincewind";
                    """;
            val decisionMaker         = compileToDecisionMaker(policy);
            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("Single obligation wrapped in array")
        void whenSingleStringObligation_thenWrappedInArray() {
            val policy                = """
                    policy "Clacks Tower Duty Roster"
                    permit
                    where subject == "Rincewind";
                    obligation "send_overhead"
                    """;
            val decisionMaker         = compileToDecisionMaker(policy);
            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), rincewindContext());
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Stream Document Evaluation")
    class StreamDocumentEvaluationTests {

        @Test
        @DisplayName("StreamPolicyBodyPolicy emits decisions on attribute state changes")
        void whenStreamAttribute_thenEmitsOnChanges() {
            val policy        = """
                    policy "Luggage Proximity Alert"
                    permit
                    where subject.<luggage.nearby> == true;
                    """;
            val attrBroker    = attributeBroker("luggage.nearby", Value.TRUE, Value.FALSE, Value.TRUE);
            val ctx           = compilationContext(attrBroker);
            val decisionMaker = compileToDecisionMaker(policy, ctx);
            assertThat(decisionMaker).isInstanceOf(StreamDecisionMaker.class);

            val subscription        = parseSubscription("""
                    {"subject": "Rincewind", "action": "flee", "resource": "Luggage"}
                    """);
            val evalContext         = evaluationContext(subscription, attrBroker);
            val streamDecisionMaker = (StreamDecisionMaker) decisionMaker;
            StepVerifier
                    .create(streamDecisionMaker.decide(List.of())
                            .contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.PERMIT))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.PERMIT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("StreamValuePolicyBodyPolicy includes static constraints in emissions")
        void whenCarrotPatrol_thenStaticConstraintsIncluded() {
            val policy     = """
                    policy "Captain Carrot Patrol Protocol"
                    permit
                    where subject.<patrol.active> == true;
                    obligation "log_patrol"
                    advice "stay_polite"
                    """;
            val attrBroker = attributeBroker("patrol.active", Value.TRUE);
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamDecisionMaker.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Carrot", "action": "patrol", "resource": "Ankh-Morpork"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(pdpDecision -> {
                val authzDecision = pdpDecision.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
                assertThat(authzDecision.advice()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPurePolicyBody evaluates pure constraints per emission")
        void whenStreamBodyPureConstraints_thenConstraintsEvaluatedPerEmission() {
            val policy     = """
                    policy "Commander Vimes Duty Protocol"
                    permit
                    where subject.<duty.status> == "on_duty";
                    obligation subject
                    """;
            val attrBroker = attributeBroker("duty.status", Value.of("on_duty"));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamDecisionMaker.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Vimes", "action": "command", "resource": "City Watch"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(pdpDecision -> {
                val authzDecision = pdpDecision.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamStreamPolicyBodyPolicy combines body and constraint streams")
        void whenStreamBodyAndStreamConstraints_thenAllStreamsCombined() {
            val policy     = """
                    policy "Guild Membership Continuous Verification"
                    permit
                    where subject.<guild.active> == true;
                    obligation <audit.guild>
                    """;
            val attrBroker = attributeBroker(Map.of("guild.active", new Value[] { Value.TRUE }, "audit.guild",
                    new Value[] { Value.of("logged") }));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamDecisionMaker.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Moist", "action": "join", "resource": "Guild"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(pdpDecision -> {
                val authzDecision = pdpDecision.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPolicyBodyPolicy emits NOT_APPLICABLE when body becomes false")
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
            StepVerifier.create(decisions)
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.PERMIT))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .verifyComplete();
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
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.INDETERMINATE))
                    .verifyComplete();
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
    @DisplayName("PureDecisionMaker Runtime Evaluation")
    class PurePDPDecisionMakerRuntimeEvaluationTests {

        @Test
        @DisplayName("multiple body conditions with obligation and advice yields PERMIT with constraints")
        void whenMultipleBodyConditionsMatch_thenPermitWithObligationAndAdvice() {
            val policy        = """
                    policy "Unseen University Library Access Control"
                    permit
                    where
                        subject.title == "Librarian";
                        subject.species == "orangutan";
                        subject.hasLibraryCard == true;
                    obligation "stamp_return_date"
                    advice "ook"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PureDecisionMaker.class);

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

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), ctx);
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotEmpty();
            assertThat(authorizationDecision.advice()).isNotEmpty();
        }

        @Test
        @DisplayName("deny entitlement with matching body conditions yields DENY with advice")
        void whenDenyPolicyBodyMatches_thenDenyWithAdvice() {
            val policy        = """
                    policy "City Watch Commander Patrol Restriction"
                    deny
                    where
                        subject.rank == "Commander";
                        subject.onDuty == true;
                        subject.patrolArea == "Shades";
                    advice "bring_entire_watch"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);

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

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), ctx);
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("stream attribute in body with stream obligation compiles to StreamDecisionMaker")
        void whenStreamBodyAndStreamObligation_thenStreamDecisionMaker() {
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
            assertThat(compiled).isInstanceOf(StreamDecisionMaker.class);
        }

        @Test
        @DisplayName("body condition with undefined comparison evaluates correctly")
        void whenBodyChecksNotUndefined_thenEvaluatesCorrectly() {
            val policy        = """
                    policy "DEATH Duty Verification Protocol"
                    permit
                    where
                        subject.name == "DEATH";
                        subject.currentTask != undefined;
                    obligation "update_lifetimers"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);

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

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), ctx);
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("object literal transform yields non-undefined resource in decision")
        void whenObjectLiteralTransform_thenResourceIsObject() {
            val policy        = """
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
            val decisionMaker = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {
                        "subject": "Lord Vetinari",
                        "action": "observe",
                        "resource": "Ankh-Morpork"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), ctx);
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditionsTests {

        @Test
        @DisplayName("Multiple obligations combined into array")
        void whenMultipleObligations_thenCombinedInArray() {
            val policy        = """
                    policy "Igor Medical Procedure Checklist"
                    permit
                    obligation "sterilize_tools"
                    obligation "check_lightning_rod"
                    obligation "prepare_operating_table"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);

            val pdpDecision           = (PolicyDecision) decisionMaker;
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.obligations()).hasSize(3);
        }

        @Test
        @DisplayName("Multiple advice combined into array")
        void whenMultipleAdvice_thenWitchesCombineCorrectly() {
            val policy        = """
                    policy "Witch Coven Guidance Protocol"
                    permit
                    advice "trust_headology"
                    advice "avoid_cackling"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);

            val pdpDecision           = (PolicyDecision) decisionMaker;
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.advice()).hasSize(2);
        }

        @Test
        @DisplayName("Empty body with constraints yields static applicableDecision with obligations")
        void whenEmptyBodyWithConstraints_thenClacksObligationsEvaluated() {
            val policy        = """
                    policy "Clacks Tower Always-On Protocol"
                    permit
                    obligation "gnu_terry_pratchett"
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);

            val pdpDecision           = (PolicyDecision) decisionMaker;
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotEmpty();
        }

        @Test
        @DisplayName("Complex nested object transform preserved in applicableDecision")
        void whenComplexTransformation_thenCensusDataPreserved() {
            val policy        = """
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
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);

            val pdpDecision           = (PolicyDecision) decisionMaker;
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }

        @Test
        @DisplayName("Boolean short-circuit prevents evaluation error in body")
        void whenShortCircuit_thenErrorPrevented() {
            val policy        = """
                    policy "Hex Safety Short-Circuit"
                    permit
                    where
                        false && (1/0 == 0);
                    """;
            val decisionMaker = compileToDecisionMaker(policy);
            assertThat(decisionMaker).isInstanceOf(PolicyDecision.class);
            val pdpDecision = (PolicyDecision) decisionMaker;
            assertThat(pdpDecision.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field comparison with undefined evaluates to PERMIT")
        void whenUndefinedField_thenHandledGracefully() {
            val policy        = """
                    policy "Morporkian Citizen Records"
                    permit
                    where subject.taxRecord == undefined;
                    """;
            val decisionMaker = compileToDecisionMaker(policy);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Nobby"}, "action": "verify", "resource": "citizenship"}
                    """);
            val ctx          = evaluationContext(subscription);

            val pureDecisionMaker     = (PureDecisionMaker) decisionMaker;
            val pdpDecision           = pureDecisionMaker.decide(List.of(), ctx);
            val authorizationDecision = pdpDecision.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("PureStreamPolicyBody Specific Behaviors")
    class PureStreamDecisionMakerPDPDecisionMakerSpecificBehaviorsTests {

        @Test
        @DisplayName("PureStreamPolicyBody evaluates pure body with stream constraints")
        void whenHistoryMonksStreaming_thenPureBodyEvaluated() {
            val policy        = """
                    policy "History Monks Time Stream Monitoring"
                    permit
                    where subject.name == "Lu-Tze";
                    obligation <temporal.audit>
                    """;
            val attrBroker    = attributeBroker("temporal.audit", Value.of("time_recorded"));
            val ctx           = compilationContext(attrBroker);
            val decisionMaker = compileToDecisionMaker(policy, ctx);
            assertThat(decisionMaker).isInstanceOf(StreamDecisionMaker.class);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Lu-Tze"}, "action": "sweep", "resource": "time stream"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDecisionMaker = (StreamDecisionMaker) decisionMaker;
            StepVerifier
                    .create(streamDecisionMaker.decide(List.of())
                            .contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.PERMIT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns NOT_APPLICABLE when pure body is false")
        void whenPureBodyFalse_thenSusanDenied() {
            val policy        = """
                    policy "Susan Sto Helit Death Duty Check"
                    permit
                    where subject.name == "DEATH";
                    obligation <death.audit>
                    """;
            val attrBroker    = attributeBroker("death.audit", Value.of("logged"));
            val ctx           = compilationContext(attrBroker);
            val decisionMaker = compileToDecisionMaker(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Susan"}, "action": "substitute", "resource": "Death Duty"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDecisionMaker = (StreamDecisionMaker) decisionMaker;
            StepVerifier
                    .create(streamDecisionMaker.decide(List.of())
                            .contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody includes pure advice in stream emissions")
        void whenPureAdviceWithStreamObligation_thenLiftedViaPathOfPureToStream() {
            val policy        = """
                    policy "Granny Weatherwax Mixed Constraint Protocol"
                    permit
                    obligation <coven.audit>
                    advice subject.headologyLevel
                    """;
            val attrBroker    = attributeBroker("coven.audit", Value.of("recorded"));
            val ctx           = compilationContext(attrBroker);
            val decisionMaker = compileToDecisionMaker(policy, ctx);
            assertThat(decisionMaker).isInstanceOf(StreamDecisionMaker.class);

            val subscription = parseSubscription("""
                    {"subject": {"headologyLevel": 99}, "action": "use", "resource": "headology"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDecisionMaker = (StreamDecisionMaker) decisionMaker;
            StepVerifier.create(streamDecisionMaker.decide(List.of())
                    .contextWrite(c -> c.put(EvaluationContext.class, evalContext))).assertNext(pdpDecision -> {
                        val authzDecision = pdpDecision.authorizationDecision();
                        assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                        assertThat(authzDecision.advice()).contains(Value.of(99));
                    }).verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns INDETERMINATE on pure body evaluation error")
        void whenPureBodyError_thenIndeterminate() {
            val policy        = """
                    policy "Rincewind Spell Attempt"
                    permit
                    where 1/subject.magicPower == 1;
                    obligation <spell.audit>
                    """;
            val attrBroker    = attributeBroker("spell.audit", Value.of("recorded"));
            val ctx           = compilationContext(attrBroker);
            val decisionMaker = compileToDecisionMaker(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"magicPower": 0}, "action": "cast", "resource": "spell"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamDecisionMaker = (StreamDecisionMaker) decisionMaker;
            StepVerifier
                    .create(streamDecisionMaker.decide(List.of())
                            .contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(pdpDecision -> assertThat(pdpDecision.authorizationDecision().decision())
                            .isEqualTo(Decision.INDETERMINATE))
                    .verifyComplete();
        }
    }
}
