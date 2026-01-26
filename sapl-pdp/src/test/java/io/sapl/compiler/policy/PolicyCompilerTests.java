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
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
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

import static io.sapl.util.SaplTesting.ATTRIBUTE_BROKER;
import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compilePolicy;
import static io.sapl.util.SaplTesting.evaluatePolicy;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parsePolicy;
import static io.sapl.util.SaplTesting.parseSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PolicyCompiler")
class PolicyCompilerTests {

    private static Voter compileToVoter(String policySource) {
        return compileToVoter(policySource, compilationContext(ATTRIBUTE_BROKER));
    }

    private static Voter compileToVoter(String policySource, CompilationContext ctx) {
        val policy         = parsePolicy(policySource);
        val compiledPolicy = PolicyCompiler.compilePolicy(policy, ctx);
        return compiledPolicy.applicabilityAndVote();
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
        void whenNoTargetThenStaticPermit() {
            val policy = """
                    policy "Unseen University Open Door"
                    permit
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val pdpVote = (Vote) voter;
            assertThat(pdpVote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("False body yields static NOT_APPLICABLE")
        void whenBodyIsFalseThenStaticNotApplicable() {
            val policy = """
                    policy "Patrician Palace Sealed"
                    permit
                    false;
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val pdpVote = (Vote) voter;
            assertThat(pdpVote.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("True body yields static PERMIT")
        void whenBodyIsTrueThenMendedDrumOpen() {
            val policy = """
                    policy "Mended Drum Open Hours"
                    permit
                    true;
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val pdpVote = (Vote) voter;
            assertThat(pdpVote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Pure body yields PureVoter")
        void whenPureBodyThenPureVoter() {
            val policy   = """
                    policy "Wizard Guild Verification"
                    permit
                    subject.guild == "wizard";
                          subject.level > 5;
                    """;
            val compiled = compileToVoter(policy);
            assertThat(compiled).isInstanceOf(PureVoter.class);
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
        void whenEmptyBodyPermitThenStaticPermit() {
            val policy = """
                    policy "City Watch Patrol Authorization"
                    permit
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val vote = (Vote) voter;
            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("Empty body deny yields static DENY")
        void whenEmptyBodyDenyThenAssassinsContractDenied() {
            val policy = """
                    policy "Assassins Guild Contract Rejection"
                    deny
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val vote = (Vote) voter;
            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("False body yields static NOT_APPLICABLE")
        void whenBodyFalseThenStaticNotApplicable() {
            val policy = """
                    policy "Thieves Guild Membership Verification"
                    permit
                    false;
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val vote = (Vote) voter;
            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Pure body without constraints yields PureVoter")
        void whenPureConditionThenPureVoter() {
            val policy   = """
                    policy "Librarian Species Verification"
                    permit
                    subject.species == "orangutan";
                    """;
            val compiled = compileToVoter(policy);
            assertThat(compiled).isInstanceOf(PureVoter.class);
        }

        @Test
        @DisplayName("Stream attribute in body yields StreamPolicyBodyPolicy")
        void whenAttributeInBodyThenWeatherMonitoringProducesStream() {
            val policy   = """
                    policy "Ankh-Morpork Weather Advisory"
                    permit
                    subject.<weather.current> == "foggy";
                    """;
            val ctx      = compilationContext(attributeBroker("weather.current", Value.of("foggy")));
            val compiled = compileToVoter(policy, ctx);
            assertThat(compiled).isInstanceOf(StreamVoter.class);
        }

        @Test
        @DisplayName("Static errors in body yields static INDETERMINATE")
        void whenStaticErrorInBodyThenStaticIndeterminate() {
            val policy = """
                    policy "Hex Arithmetic Failure"
                    permit
                    1/0 == 0;
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val vote = (Vote) voter;
            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Document Type Selection Based on Strata")
    class DocumentTypeSelectionTests {

        static Stream<Arguments> allStrataPermutations() {
            return Stream.of(
                    // Value stratum only
                    arguments("Value body with value constraints yields AuthorizationDecision",
                            "permit true; obligation \"follow_owner\" advice \"dont_eat_anyone\" transform \"sapient_pearwood\"",
                            Vote.class, null),

                    // Pure body, no constraints -> PureVoter
                    // (ApplicabilityCheckingPureVoter)
                    arguments("Pure body without constraints yields PureVoter", "permit subject.hasHeadology == true;",
                            PureVoter.class, null),

                    // Value body, Pure obligation -> PureVoter
                    arguments("Value body with pure obligation yields PureVoter",
                            "permit true; obligation subject.kitchen", PureVoter.class, null),

                    // Value body, Pure advice -> PureVoter
                    arguments("Value body with pure advice yields PureVoter", "permit true; advice subject.herbLore",
                            PureVoter.class, null),

                    // Value body, Pure transform -> PureVoter
                    arguments("Value body with pure transform yields PureVoter",
                            "permit true; transform subject.census", PureVoter.class, null),

                    // Pure body, Value constraints -> PureVoter
                    arguments("Pure body with value obligation yields PureVoter",
                            "permit subject.age > 80; obligation \"record_saga\"", PureVoter.class, null),

                    // Pure body, Pure obligation -> PureVoter
                    arguments("Pure body with pure obligation yields PureVoter",
                            "permit subject.title == \"Archchancellor\"; obligation subject.staffLog", PureVoter.class,
                            null),

                    // Pure body, Stream obligation -> PureStreamPolicyBody
                    arguments("Pure body with stream obligation yields PureStreamPolicyBody",
                            "permit subject.department == \"HEM\"; obligation <audit.hex>", StreamVoter.class,
                            "audit.hex"),

                    // Stream body, no constraints -> StreamPolicyBodyPolicy
                    arguments("Stream body without constraints yields StreamPolicyBodyPolicy",
                            "permit subject.<death.available> == true;", StreamVoter.class, "death.available"),

                    // Stream body, Value constraints -> StreamValuePolicyBodyPolicy
                    arguments("Stream body with value constraints yields StreamValuePolicyBodyPolicy",
                            "permit subject.<hogfather.status> == \"delivering\"; obligation \"wrap_gift\" advice \"check_list\"",
                            StreamVoter.class, "hogfather.status"),

                    // Stream body, Pure constraints -> StreamPurePolicyBody
                    arguments("Stream body with pure obligation yields StreamPurePolicyBody",
                            "permit subject.<location.current> == \"running\"; obligation subject.equipment",
                            StreamVoter.class, "location.current"),

                    // Stream body, Stream constraints -> StreamStreamPolicyBodyPolicy
                    arguments("Stream body with stream obligation yields StreamStreamPolicyBodyPolicy",
                            "permit subject.<guild.war> == \"active\"; obligation <audit.combat>", StreamVoter.class,
                            "guild.war,audit.combat"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("allStrataPermutations")
        @DisplayName("Stratum combinations produce correct document types")
        void stratumCombinationsProduceCorrectTypes(String scenario, String policyBody,
                Class<? extends Voter> expectedType, String attrNames) {
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
            val compiled = compileToVoter(policy, ctx);
            assertThat(compiled).isInstanceOf(expectedType);
        }

        static Stream<Arguments> strataLiftingWithMixedConstraints() {
            return Stream.of(
                    arguments("Pure obligation with value advice yields PureVoter",
                            "permit true; obligation subject.organ advice \"wash_hands\"", PureVoter.class),
                    arguments("Value obligation with pure advice yields PureVoter",
                            "permit true; obligation \"show_certificate\" advice subject.species", PureVoter.class),
                    arguments("Pure body with pure obligation and transform yields PureVoter",
                            "permit subject.clearance > 9; obligation subject.log transform subject.redacted",
                            PureVoter.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("strataLiftingWithMixedConstraints")
        @DisplayName("Mixed constraint strata lift correctly")
        void mixedConstraintStrataLiftCorrectly(String scenario, String policyBody,
                Class<? extends Voter> expectedType) {
            val policy   = "policy \"Strata Test\" " + policyBody;
            val compiled = compileToVoter(policy);
            assertThat(compiled).isInstanceOf(expectedType);
        }
    }

    @Nested
    @DisplayName("Pure Document Evaluation")
    class PureDocumentEvaluationTests {

        @Test
        @DisplayName("PureVoter evaluates to PERMIT when body condition matches")
        void whenBodyConditionMatchesThenPermit() {
            val policy = """
                    policy "Rincewind Emergency Escape Protocol"
                    permit
                    subject == "Rincewind";
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(PureVoter.class);

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("SimplePureVoter evaluates to NOT_APPLICABLE when body condition fails")
        void whenBodyConditionFailsThenNotApplicable() {
            val policy                = """
                    policy "Twoflower Tourist Visa Check"
                    permit
                    subject == "Twoflower";
                    """;
            val voter                 = compileToVoter(policy);
            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();
            assertThat(vote).isInstanceOf(Vote.class);
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field access in body evaluates to NOT_APPLICABLE")
        void whenUndefinedFieldAccessThenNotApplicable() {
            val policy                = """
                    policy "Luggage Inventory Check"
                    permit
                    subject.nonexistent.compartment == true;
                    """;
            val voter                 = compileToVoter(policy);
            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("PureVoter evaluates obligation, advice, and transform correctly")
        void whenPureConstraintsThenAllEvaluated() {
            val policy = """
                    policy "Lord Vetinari Executive Order"
                    permit
                    subject == "Rincewind";
                    obligation "report_to_patrician"
                    advice "avoid_politics"
                    transform "classified"
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(PureVoter.class);

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();

            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotNull().isNotEmpty();
            assertThat(authorizationDecision.advice()).isNotNull().isNotEmpty();
            assertThat(authorizationDecision.resource()).isEqualTo(Value.of("classified"));
        }

        @Test
        @DisplayName("Deny policy evaluates to DENY when body condition matches")
        void whenDenyPolicyBodyMatchesThenDeny() {
            val policy                = """
                    policy "Forbidden Octavo Section"
                    deny
                    subject == "Rincewind";
                    """;
            val voter                 = compileToVoter(policy);
            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("Single obligation wrapped in array")
        void whenSingleStringObligationThenWrappedInArray() {
            val policy                = """
                    policy "Clacks Tower Duty Roster"
                    permit
                    subject == "Rincewind";
                    obligation "send_overhead"
                    """;
            val voter                 = compileToVoter(policy);
            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(rincewindContext());
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Stream Document Evaluation")
    class StreamDocumentEvaluationTests {

        @Test
        @DisplayName("StreamPolicyBodyPolicy emits decisions on attribute state changes")
        void whenStreamAttributeThenEmitsOnChanges() {
            val policy     = """
                    policy "Luggage Proximity Alert"
                    permit
                    subject.<luggage.nearby> == true;
                    """;
            val attrBroker = attributeBroker("luggage.nearby", Value.TRUE, Value.FALSE, Value.TRUE);
            val ctx        = compilationContext(attrBroker);
            val voter      = compileToVoter(policy, ctx);
            assertThat(voter).isInstanceOf(StreamVoter.class);

            val subscription = parseSubscription("""
                    {"subject": "Rincewind", "action": "flee", "resource": "Luggage"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);
            val streamVoter  = (StreamVoter) voter;
            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("StreamValuePolicyBodyPolicy includes static constraints in emissions")
        void whenCarrotPatrolThenStaticConstraintsIncluded() {
            val policy     = """
                    policy "Captain Carrot Patrol Protocol"
                    permit
                    subject.<patrol.active> == true;
                    obligation "log_patrol"
                    advice "stay_polite"
                    """;
            val attrBroker = attributeBroker("patrol.active", Value.TRUE);
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamVoter.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Carrot", "action": "patrol", "resource": "Ankh-Morpork"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(vote -> {
                val authzDecision = vote.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
                assertThat(authzDecision.advice()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPurePolicyBody evaluates pure constraints per emission")
        void whenStreamBodyPureConstraintsThenConstraintsEvaluatedPerEmission() {
            val policy     = """
                    policy "Commander Vimes Duty Protocol"
                    permit
                    subject.<duty.status> == "on_duty";
                    obligation subject
                    """;
            val attrBroker = attributeBroker("duty.status", Value.of("on_duty"));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamVoter.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Vimes", "action": "command", "resource": "City Watch"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(vote -> {
                val authzDecision = vote.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamStreamPolicyBodyPolicy combines body and constraint streams")
        void whenStreamBodyAndStreamConstraintsThenAllStreamsCombined() {
            val policy     = """
                    policy "Guild Membership Continuous Verification"
                    permit
                    subject.<guild.active> == true;
                    obligation <audit.guild>
                    """;
            val attrBroker = attributeBroker(Map.of("guild.active", new Value[] { Value.TRUE }, "audit.guild",
                    new Value[] { Value.of("logged") }));
            assertThat(compilePolicy(policy, attrBroker)).isInstanceOf(StreamVoter.class);

            val decisions = evaluatePolicy("""
                    {"subject": "Moist", "action": "join", "resource": "Guild"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(vote -> {
                val authzDecision = vote.authorizationDecision();
                assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                assertThat(authzDecision.obligations()).isNotNull().isNotEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("StreamPolicyBodyPolicy emits NOT_APPLICABLE when body becomes false")
        void whenBodyBecomesFalseThenAccessRevoked() {
            val policy     = """
                    policy "Dynamic Guild Access Revocation"
                    permit
                    subject.<access.valid> == true;
                    """;
            val attrBroker = attributeBroker("access.valid", Value.TRUE, Value.FALSE);

            val decisions = evaluatePolicy("""
                    {"subject": "Visitor", "action": "access", "resource": "Guild Hall"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions)
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Attribute errors in body produces INDETERMINATE")
        void whenAttributeErrorThenIndeterminateDecision() {
            val policy     = """
                    policy "Clacks Network Error Handling"
                    permit
                    subject.<clacks.signal> == true;
                    """;
            val attrBroker = attributeBroker("clacks.signal", Value.error("GNU Terry Pratchett"));

            val decisions = evaluatePolicy("""
                    {"subject": "operator", "action": "transmit", "resource": "Clacks Tower"}
                    """, policy, attrBroker);
            StepVerifier.create(decisions).assertNext(
                    vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Constraint Error Handling")
    class ConstraintErrorHandlingTests {

        static Stream<Arguments> constraintErrorCases() {
            return Stream.of(arguments("Static errors in transform throws SaplCompilerException", """
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
            assertThatThrownBy(() -> compileToVoter(policySource)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining(expectedType).hasMessageContaining(expectedMessage);
        }
    }

    @Nested
    @DisplayName("PureVoter Runtime Evaluation")
    class PureVoteMakerRuntimeEvaluationTests {

        @Test
        @DisplayName("multiple body conditions with obligation and advice yields PERMIT with constraints")
        void whenMultipleBodyConditionsMatchThenPermitWithAttributesObligationAndAdvice() {
            val policy = """
                    policy "Unseen University Library Access Control"
                    permit
                        subject.title == "Librarian";
                        subject.species == "orangutan";
                        subject.hasLibraryCard == true;
                    obligation "stamp_return_date"
                    advice "ook"
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(PureVoter.class);

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

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(ctx);
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotEmpty();
            assertThat(authorizationDecision.advice()).isNotEmpty();
        }

        @Test
        @DisplayName("deny entitlement with matching body conditions yields DENY with advice")
        void whenDenyPolicyBodyMatchesThenDenyWithAttributesAdvice() {
            val policy = """
                    policy "City Watch Commander Patrol Restriction"
                    deny
                        subject.rank == "Commander";
                        subject.onDuty == true;
                        subject.patrolArea == "Shades";
                    advice "bring_entire_watch"
                    """;
            val voter  = compileToVoter(policy);

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

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(ctx);
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.DENY);
        }

        @Test
        @DisplayName("stream attribute in body with stream obligation compiles to StreamVoter")
        void whenStreamBodyAndStreamObligationThenStreamVoter() {
            val policy   = """
                    policy "Assassins Guild Active Contract Protocol"
                    permit
                        subject.guild == "Assassins";
                        subject.<contract.status> == "active";
                    obligation <audit.inhumation>
                    transform "CLASSIFIED"
                    """;
            val ctx      = compilationContext(attributeBroker(Map.of("contract.status",
                    new Value[] { Value.of("active") }, "audit.inhumation", new Value[] { Value.of("recorded") })));
            val compiled = compileToVoter(policy, ctx);
            assertThat(compiled).isInstanceOf(StreamVoter.class);
        }

        @Test
        @DisplayName("body condition with undefined comparison evaluates correctly")
        void whenBodyChecksNotUndefinedThenEvaluatesCorrectly() {
            val policy = """
                    policy "DEATH Duty Verification Protocol"
                    permit
                        subject.name == "DEATH";
                        subject.currentTask != undefined;
                    obligation "update_lifetimers"
                    """;
            val voter  = compileToVoter(policy);

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

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(ctx);
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("object literal transform yields non-undefined resource in vote")
        void whenObjectLiteralTransformThenResourceIsObject() {
            val policy = """
                    policy "Patrician Surveillance Network Access"
                    permit
                        subject == "Lord Vetinari";
                        action == "observe";
                    transform {
                        "accessLevel": "omniscient",
                        "clearance": "absolute"
                    }
                    """;
            val voter  = compileToVoter(policy);

            val subscription = parseSubscription("""
                    {
                        "subject": "Lord Vetinari",
                        "action": "observe",
                        "resource": "Ankh-Morpork"
                    }
                    """);
            val ctx          = evaluationContext(subscription);

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(ctx);
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditionsTests {

        @Test
        @DisplayName("Multiple obligations combined into array")
        void whenMultipleObligationsThenCombinedInArray() {
            val policy = """
                    policy "Igor Medical Procedure Checklist"
                    permit
                    obligation "sterilize_tools"
                    obligation "check_lightning_rod"
                    obligation "prepare_operating_table"
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);

            val vote                  = (Vote) voter;
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.obligations()).hasSize(3);
        }

        @Test
        @DisplayName("Multiple advice combined into array")
        void whenMultipleAdviceThenWitchesCombineCorrectly() {
            val policy = """
                    policy "Witch Coven Guidance Protocol"
                    permit
                    advice "trust_headology"
                    advice "avoid_cackling"
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);

            val vote                  = (Vote) voter;
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.advice()).hasSize(2);
        }

        @Test
        @DisplayName("Empty body with constraints yields static applicableDecision with obligations")
        void whenEmptyBodyWithConstraintsThenClacksObligationsEvaluated() {
            val policy = """
                    policy "Clacks Tower Always-On Protocol"
                    permit
                    obligation "gnu_terry_pratchett"
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);

            val vote                  = (Vote) voter;
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
            assertThat(authorizationDecision.obligations()).isNotEmpty();
        }

        @Test
        @DisplayName("Complex nested object transform preserved in applicableDecision")
        void whenComplexTransformationThenCensusDataPreserved() {
            val policy = """
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
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);

            val vote                  = (Vote) voter;
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.resource()).isNotNull().isNotInstanceOf(UndefinedValue.class);
        }

        @Test
        @DisplayName("Boolean short-circuit prevents evaluation errors in body")
        void whenShortCircuitThenErrorPrevented() {
            val policy = """
                    policy "Hex Safety Short-Circuit"
                    permit
                        false && (1/0 == 0);
                    """;
            val voter  = compileToVoter(policy);
            assertThat(voter).isInstanceOf(Vote.class);
            val vote = (Vote) voter;
            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        @Test
        @DisplayName("Undefined field comparison with undefined evaluates to PERMIT")
        void whenUndefinedFieldThenHandledGracefully() {
            val policy = """
                    policy "Morporkian Citizen Records"
                    permit
                    subject.taxRecord == undefined;
                    """;
            val voter  = compileToVoter(policy);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Nobby"}, "action": "verify", "resource": "citizenship"}
                    """);
            val ctx          = evaluationContext(subscription);

            val pureVoter             = (PureVoter) voter;
            val vote                  = pureVoter.vote(ctx);
            val authorizationDecision = vote.authorizationDecision();
            assertThat(authorizationDecision.decision()).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("PureStreamPolicyBody Specific Behaviors")
    class PureStreamVoterSpecificBehaviorsTests {

        @Test
        @DisplayName("PureStreamPolicyBody evaluates pure body with stream constraints")
        void whenHistoryMonksStreamingThenPureBodyEvaluated() {
            val policy     = """
                    policy "History Monks Time Stream Monitoring"
                    permit
                    subject.name == "Lu-Tze";
                    obligation <temporal.audit>
                    """;
            val attrBroker = attributeBroker("temporal.audit", Value.of("time_recorded"));
            val ctx        = compilationContext(attrBroker);
            val voter      = compileToVoter(policy, ctx);
            assertThat(voter).isInstanceOf(StreamVoter.class);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Lu-Tze"}, "action": "sweep", "resource": "time stream"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamVoter = (StreamVoter) voter;
            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns NOT_APPLICABLE when pure body is false")
        void whenPureBodyFalseThenSusanDenied() {
            val policy     = """
                    policy "Susan Sto Helit Death Duty Check"
                    permit
                    subject.name == "DEATH";
                    obligation <death.audit>
                    """;
            val attrBroker = attributeBroker("death.audit", Value.of("logged"));
            val ctx        = compilationContext(attrBroker);
            val voter      = compileToVoter(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"name": "Susan"}, "action": "substitute", "resource": "Death Duty"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamVoter = (StreamVoter) voter;
            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision())
                            .isEqualTo(Decision.NOT_APPLICABLE))
                    .verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody includes pure advice in stream emissions")
        void whenPureAdviceWithAttributesStreamObligationThenLiftedViaPathOfPureToStream() {
            val policy     = """
                    policy "Granny Weatherwax Mixed Constraint Protocol"
                    permit
                    obligation <coven.audit>
                    advice subject.headologyLevel
                    """;
            val attrBroker = attributeBroker("coven.audit", Value.of("recorded"));
            val ctx        = compilationContext(attrBroker);
            val voter      = compileToVoter(policy, ctx);
            assertThat(voter).isInstanceOf(StreamVoter.class);

            val subscription = parseSubscription("""
                    {"subject": {"headologyLevel": 99}, "action": "use", "resource": "headology"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamVoter = (StreamVoter) voter;
            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(vote -> {
                        val authzDecision = vote.authorizationDecision();
                        assertThat(authzDecision.decision()).isEqualTo(Decision.PERMIT);
                        assertThat(authzDecision.advice()).contains(Value.of(99));
                    }).verifyComplete();
        }

        @Test
        @DisplayName("PureStreamPolicyBody returns INDETERMINATE on pure body evaluation errors")
        void whenPureBodyErrorThenIndeterminate() {
            val policy     = """
                    policy "Rincewind Spell Attempt"
                    permit
                    1/subject.magicPower == 1;
                    obligation <spell.audit>
                    """;
            val attrBroker = attributeBroker("spell.audit", Value.of("recorded"));
            val ctx        = compilationContext(attrBroker);
            val voter      = compileToVoter(policy, ctx);

            val subscription = parseSubscription("""
                    {"subject": {"magicPower": 0}, "action": "cast", "resource": "spell"}
                    """);
            val evalContext  = evaluationContext(subscription, attrBroker);

            val streamVoter = (StreamVoter) voter;
            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, evalContext)))
                    .assertNext(vote -> assertThat(vote.authorizationDecision().decision())
                            .isEqualTo(Decision.INDETERMINATE))
                    .verifyComplete();
        }
    }
}
