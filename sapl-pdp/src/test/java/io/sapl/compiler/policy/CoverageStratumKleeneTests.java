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

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compilePolicyFull;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseSubscription;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.broker.api.TestAttributeBroker;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.model.Coverage;
import io.sapl.util.CoverageEvaluator;
import lombok.val;

/**
 * The coverage voter walks the body conditions by stratum (constants, then pure
 * operators, then streams) under Kleene strong three-valued AND, so its
 * decision
 * follows production and a FALSE dominates regardless of where it sits. These
 * cases pin the behaviour the stratification adds beyond the single pure-and-
 * stream cell: a FALSE that dominates an earlier error inside the stream
 * stratum,
 * a constant FALSE that blocks an earlier pure condition from being evaluated,
 * and a pure FALSE that decides the body without awaiting a stream that never
 * arrives. Each runs under both lowLatencyMode settings to cover the eager and
 * lazy walks.
 */
@DisplayName("Coverage walks body strata under Kleene AND")
class CoverageStratumKleeneTests {

    private static final String SUBSCRIPTION = """
            { "subject": "alice", "action": "read", "resource": "data" }
            """;

    @ParameterizedTest(name = "lowLatencyMode={0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("a streaming FALSE dominates an earlier streaming error")
    void whenStreamErrorBeforeStreamFalseThenNotApplicable(boolean lowLatencyMode) throws InterruptedException {
        val result = coverage("policy \"p\" permit <test.a>; <test.b>;", lowLatencyMode, broker -> {
            broker.register("test.a", Value.error("stream boom"));
            broker.register("test.b", Value.FALSE);
        });
        assertThat(result.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        assertThat(hitStatementIds(result)).containsExactly(0L, 1L);
    }

    @ParameterizedTest(name = "lowLatencyMode={0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("a constant FALSE blocks an earlier pure condition from being evaluated")
    void whenConstantFalseAfterPureThenPureNotHit(boolean lowLatencyMode) throws InterruptedException {
        val result = coverage("policy \"p\" permit subject == \"alice\"; false;", lowLatencyMode, broker -> {});
        assertThat(result.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        // The constant FALSE is statement 1. The pure equality is statement 0 and must
        // not be hit.
        assertThat(hitStatementIds(result)).containsExactly(1L);
    }

    @ParameterizedTest(name = "lowLatencyMode={0}")
    @ValueSource(booleans = { true, false })
    @Timeout(10)
    @DisplayName("a pure FALSE decides the body without awaiting a stream that never arrives")
    void whenPureFalseBesideNeverArrivingStreamThenNotApplicableWithoutHanging(boolean lowLatencyMode)
            throws InterruptedException {
        val result = coverage("policy \"p\" permit <never.attr>; subject == \"bob\";", lowLatencyMode,
                broker -> broker.register("never.attr"));
        assertThat(result.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        // Only the pure equality (statement 1) is reached. The never-arriving stream
        // (statement 0) is not.
        assertThat(hitStatementIds(result)).containsExactly(1L);
    }

    private static VoteResultWithCoverage coverage(String policySource, boolean lowLatencyMode,
            Consumer<TestAttributeBroker> register) throws InterruptedException {
        val ctx = compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("lowLatencyMode", Value.of(lowLatencyMode)).build());
        val compiled = compilePolicyFull(policySource, ctx);
        val baseCtx  = evaluationContext(parseSubscription(SUBSCRIPTION));
        try (val broker = new TestAttributeBroker()) {
            register.accept(broker);
            try (val coverage = CoverageEvaluator.evaluate(compiled.coverageVoter(), baseCtx, broker)) {
                return coverage.awaitNext();
            }
        }
    }

    private static List<Long> hitStatementIds(VoteResultWithCoverage result) {
        return ((Coverage.PolicyCoverage) result.coverage()).bodyCoverage().hits().stream()
                .map(Coverage.ConditionHit::statementId).toList();
    }
}
