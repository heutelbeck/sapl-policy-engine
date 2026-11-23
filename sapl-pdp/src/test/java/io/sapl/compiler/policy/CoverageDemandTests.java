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

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;
import static io.sapl.compiler.document.DocumentCompiler.compileDocument;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.PdpCompiler;

import lombok.val;

@DisplayName("Coverage of reached policy conditions")
class CoverageDemandTests {
    @ParameterizedTest(name = "lowLatencyMode={0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("a failed first gate completes without opening the later finder")
    void whenClearanceIsDeniedThenOnlyTheClearanceFinderIsRequested(boolean lowLatency) {
        val compiler = compilationContext();
        compiler.setCompilerOptions(ObjectValue.builder().put("lowLatencyMode", Value.of(lowLatency)).build());
        val document = compileDocument("""
                policy "entry" permit
                    <building.clearance>;
                    <building.duty>;
                """, compiler);
        val context  = evaluationContext();
        val initial  = document.applicabilityAndVote().evaluate(context);
        assertThat(document.coverageVoter().evaluate(context).voteResult()).isEqualTo(initial);
        val clearance = initial.dependencies().keySet().iterator().next();
        val refused   = context.withSnapshot(Map.of(clearance, new AttributeSnapshot(Value.FALSE, Instant.EPOCH)));
        val covered   = document.coverageVoter().evaluate(refused);
        assertThat(covered.voteResult()).isEqualTo(document.applicabilityAndVote().evaluate(refused));
        val coverage = (Coverage.PolicyCoverage) covered.coverage();
        assertThat(coverage.bodyCoverage().hits()).extracting(h -> h.statementId()).containsExactly(0L);
    }

    @ParameterizedTest(name = "index={0}")
    @ValueSource(strings = { "NAIVE", "CANONICAL", "SMTDD" })
    @DisplayName("coverage asks for the same first unresolved policy as the authorization vote")
    void whenTwoPoliciesRequireFindersThenTheCoverageRoundHasTheSameRequests(String index) {
        val compiler = compilationContext();
        compiler.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(index)).build());
        val document = compileDocument("""
                set "entry" priority permit or deny
                policy "visitor" permit <building.visiting>;
                policy "employee" permit <building.onDuty>;
                """, compiler);
        val context  = evaluationContext();
        val ordinary = document.applicabilityAndVote().evaluate(context);
        assertThat(ordinary.vote()).isNull();
        assertThat(document.coverageVoter().evaluate(context).voteResult()).isEqualTo(ordinary);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource({ "string,PERMIT", "boolean,NOT_APPLICABLE", })
    @DisplayName("a set header validates once and gates both member bodies")
    void whenSchemaIsTheOnlyTargetThenItValidatesOnceForBothMembers(String type, Decision expected) {
        val document = compileDocument("""
                subject enforced schema {"type":"%s"}
                set "building" priority permit or abstain errors propagate
                policy "visitor" permit subject == "testSubject"; obligation "visitor"
                policy "employee" permit action == "testAction"; obligation "employee"
                """.formatted(type), compilationContext());
        val calls    = new AtomicInteger();
        val context  = evaluationContext().withObserver((parent, operand, value) -> {
                         if (parent == operand
                                 && operand instanceof SchemaValidatorCompiler.PrecompiledSchemaValidator) {
                             calls.incrementAndGet();
                         }
                     });
        val ordinary = document.applicabilityAndVote().evaluate(context);
        assertThat(calls.getAndSet(0)).isOne();
        val result = document.coverageVoter().evaluate(context);
        assertThat(calls.get()).isOne();
        assertThat(result.voteResult()).isEqualTo(ordinary);
        assertThat(ordinary.vote().authorizationDecision().decision()).isEqualTo(expected);
        val set    = (Coverage.PolicySetCoverage) result.coverage();
        val target = (Coverage.TargetResult) set.targetHit();
        assertThat(target.match()).isEqualTo(Value.of(expected == Decision.PERMIT));
        assertThat(set.schemaHit().result()).isEqualTo(target.match());
        assertThat(set.schemaHit().location()).isEqualTo(target.location());
        if (expected == Decision.PERMIT) {
            assertThat(set.policyCoverages()).hasSize(2).allSatisfy(child -> {
                val body = ((Coverage.PolicyCoverage) child).bodyCoverage();
                assertThat(body.numberOfConditions()).isOne();
                assertThat(body.hits()).extracting(Coverage.ConditionHit::statementId).containsExactly(0L);
            });
        } else {
            assertThat(set.policyCoverages()).isEmpty();
        }
    }

    @Test
    @DisplayName("a schema-only set preserves a validation error as its target result")
    void whenSchemaValidationErrorsThenTheTargetCarriesTheError() {
        val document = compileDocument("""
                subject enforced schema {"type":"string"}
                set "building" first or abstain errors propagate
                policy "entry" permit
                """, compilationContext());
        val failure  = Value.error("subject unavailable");
        val context  = evaluationContext(AuthorizationSubscription.of(failure, "enter", "building"));
        val covered  = document.coverageVoter().evaluate(context);
        assertThat(covered.voteResult()).isEqualTo(document.applicabilityAndVote().evaluate(context));
        val set = (Coverage.PolicySetCoverage) covered.coverage();
        assertThat(((Coverage.TargetResult) set.targetHit()).match()).isEqualTo(failure);
        assertThat(set.schemaHit().result()).isEqualTo(failure);
        assertThat(set.policyCoverages()).isEmpty();
    }

    @ParameterizedTest(name = "{0}, {1}")
    @CsvSource({ "string,true,PERMIT", "boolean,action.missing > 0,NOT_APPLICABLE",
            "string,action.missing > 0,INDETERMINATE" })
    @DisplayName("the target result reports the conjunction of validation and FOR")
    void whenForAndValidationAreCombinedThenTheReportedTargetGatesTheMember(String type, String operand,
            Decision expected) {
        val document = compileDocument("""
                subject schema {"type":"boolean"}
                set "building" priority permit or abstain
                policy "visitor" permit true;
                policy "employee" permit true;
                """, compilationContext());
        val set      = (Coverage.PolicySetCoverage) document.coverageVoter().evaluate(evaluationContext()).coverage();
        assertThat(set.targetHit()).isEqualTo(Coverage.BLANK_TARGET_HIT);
        assertThat(set.schemaHit()).isNull();
        assertThat(set.policyCoverages()).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "first", "priority permit", "unique", "unanimous", "unanimous strict" })
    @DisplayName("a partial round exposes only completed members")
    void whenASetWaitsThenOnlyCompletedSiblingsAppear(String algorithm) {
        val first    = "first".equals(algorithm) ? "false" : "true";
        val document = compileDocument("""
                set "building" %s or abstain
                policy "finished" permit %s;
                policy "waiting" permit true; <building.clearance>;
                """.formatted(algorithm, first), compilationContext());
        val result   = document.coverageVoter().evaluate(evaluationContext());
        assertThat(result.voteResult().vote()).isNull();
        val set = (Coverage.PolicySetCoverage) result.coverage();
        assertThat(set.policyCoverages()).extracting(member -> member.voter().name()).containsExactly("finished");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "NAIVE", "CANONICAL", "SMTDD" })
    @DisplayName("an exhausted incremental index reports a failed policy guard")
    void whenNoPolicyMatchesUniqueThenTheRejectedConditionIsCovered(String index) {
        val compiler = compilationContext();
        compiler.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(index)).build());
        val config  = new PDPConfiguration("facility", "gates",
                new CombiningAlgorithm(CombiningAlgorithm.VotingMode.UNIQUE, ABSTAIN, PROPAGATE),
                List.of("policy \"visitor\" permit subject == \"visitor\";"),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        val pdp     = PdpCompiler.compilePDPConfiguration(config, compiler);
        val covered = pdp.coverageVoter().evaluate(evaluationContext());
        assertThat(covered.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        val members = ((Coverage.PdpCoverage) covered.coverage()).documentCoverages();
        assertThat(members).singleElement()
                .satisfies(member -> assertThat(((Coverage.PolicyCoverage) member).bodyCoverage().hits())
                        .extracting(Coverage.ConditionHit::result).containsExactly(Value.FALSE));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "NAIVE", "CANONICAL", "SMTDD" })
    @DisplayName("a shared predicate proves each reached source occurrence")
    void whenIndexedPoliciesShareAConditionThenEveryMatchingSourceSiteIsCovered(String indexing) {
        val compiler = compilationContext();
        compiler.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of(indexing)).build());
        val config  = new PDPConfiguration("facility", "gates",
                new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE),
                List.of("policy \"visitor\" permit var admitted = subject == \"testSubject\"; admitted; admitted; obligation \"visit\"",
                        "policy \"employee\" permit subject == \"testSubject\"; obligation \"work\""),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        val pdp     = PdpCompiler.compilePDPConfiguration(config, compiler);
        val context = evaluationContext();
        val covered = pdp.coverageVoter().evaluate(context);
        assertThat(covered.voteResult()).isEqualTo(pdp.voter().evaluate(context));
        val members = ((Coverage.PdpCoverage) covered.coverage()).documentCoverages();
        assertThat(members).hasSize(2);
        assertThat(((Coverage.PolicyCoverage) members.getFirst()).bodyCoverage().hits())
                .extracting(Coverage.ConditionHit::statementId).containsExactly(0L, 1L);
        assertThat(((Coverage.PolicyCoverage) members.getLast()).bodyCoverage().hits())
                .extracting(Coverage.ConditionHit::statementId).containsExactly(0L);
    }
}
