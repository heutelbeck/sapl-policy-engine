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
package io.sapl.attributes.broker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.compiler.document.VoteResult;
import lombok.val;

import static io.sapl.attributes.broker.BrokerEvalLoops.openWithHead;
import static io.sapl.compiler.policy.PolicyCompiler.compilePolicy;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parsePolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Timeout(20)
@DisplayName("Lazy policy reevaluation while an external attribute is pending")
class PendingAttributeReevaluationTests {

    private static final String PENDING_B = "<access.b[{initialTimeOutMs: 60000, retries: 0}]>";

    @Test
    @DisplayName("a frozen head remains the first observation while a different attribute is pending")
    void whenALiveSourceChangesThenItsCachedHeadDoesNotBecomeANewLiveInput() throws Exception {
        val voter   = compilePolicy(parsePolicy("""
                policy "A frozen approval and a live approval"
                permit
                    |<access.a> && %s;
                """.formatted(PENDING_B)), compilationContext()).applicabilityAndVote();
        val context = evaluationContext();
        val initial = voter.evaluate(context);
        val a       = initial.dependencies().keySet().iterator().next();
        assertThat(a.head()).isTrue();
        val aKey         = RepositoryKey.fromInvocation(a.invocation());
        val rounds       = new CopyOnWriteArrayList<EvaluationRound>();
        val result       = new CompletableFuture<EvaluationRound>();
        val terminations = new CopyOnWriteArrayList<RuntimeException>();
        try (val pip = new PendingPip();
                val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository)) {
            broker.load(pip);
            repository.publish(List.of(new BoundValue(aKey, Value.TRUE, null)));
            try (val subscription = openWithHead(broker, "head-with-pending-b", Set.of(a),
                    inputs -> voter.evaluate(context.withSnapshot(inputs)), (vote, inputs) -> {
                        val round = new EvaluationRound(inputs, vote);
                        rounds.add(round);
                        if (vote.vote() != null) {
                            result.complete(round);
                        }
                    }, vote -> vote.dependencies().keySet(), terminations::add)) {
                assertThat(pip.opened).hasValue(1);
                repository.publish(List.of(new BoundValue(aKey, Value.FALSE, null)));
                assertThat(rounds).singleElement().satisfies(round -> {
                    assertThat(round.inputs().get(a).value()).isEqualTo(Value.TRUE);
                    assertThat(round.result().vote()).isNull();
                });
                pip.emit(Value.FALSE);
                val completed = result.get(5, TimeUnit.SECONDS);
                assertThat(completed.inputs().get(a).value()).isEqualTo(Value.TRUE);
                assertThat(completed.result().vote().authorizationDecision().decision())
                        .isEqualTo(Decision.NOT_APPLICABLE);
                assertThat(terminations).isEmpty();
            }
        }
    }

    @MethodSource("lazyPolicies")
    @ParameterizedTest(name = "{0}")
    @DisplayName("a changed A makes pending B unnecessary without waiting for B to deliver")
    void whenAChangesWhileBIsPendingThenThePolicyCanDropB(String description, String condition, Value initialA,
            Value changedA, Value releasedB, boolean establishPermit, Decision expected) throws Exception {
        val source  = parsePolicy("""
                policy "Access while an external approval is pending"
                permit
                    %s;
                """.formatted(condition));
        val voter   = compilePolicy(source, compilationContext()).applicabilityAndVote();
        val context = evaluationContext();
        val initial = voter.evaluate(context);
        assertThat(initial.dependencies()).hasSize(1);
        val a = initial.dependencies().keySet().iterator().next();
        assertThat(a.invocation().attributeName()).isEqualTo("access.a");
        val aKey         = RepositoryKey.fromInvocation(a.invocation());
        val cKey         = new RepositoryKey(null, "access.c", List.of(), context.pdpId());
        val rounds       = new CopyOnWriteArrayList<EvaluationRound>();
        val decisions    = new CopyOnWriteArrayList<Decision>();
        val terminations = new CopyOnWriteArrayList<RuntimeException>();
        val beforeB      = new CompletableFuture<EvaluationRound>();
        val completed    = new CompletableFuture<EvaluationRound>();

        // The compiled policy itself can finish from the changed A without any B value.
        val independent = voter
                .evaluate(context.withSnapshot(Map.of(a, new AttributeSnapshot(changedA, Instant.EPOCH))));
        assertThat(independent.dependencies()).containsOnlyKeys(a);
        assertThat(independent.vote().authorizationDecision().decision()).isEqualTo(expected);

        try (val pip = new PendingPip();
                val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository)) {
            broker.load(pip);
            repository.publish(List.of(new BoundValue(aKey, initialA, null), new BoundValue(cKey, Value.TRUE, null)));
            try (val subscription = openWithHead(broker, "pending-b", Set.of(a),
                    inputs -> voter.evaluate(context.withSnapshot(inputs)), (result, inputs) -> {
                        val round = new EvaluationRound(inputs, result);
                        rounds.add(round);
                        if (result.vote() != null) {
                            decisions.add(result.vote().authorizationDecision().decision());
                            if (result.dependencies().keySet().equals(Set.of(a))
                                    && changedA.equals(inputs.get(a).value())) {
                                completed.complete(round);
                                if (pip.delivered.get() == 0) {
                                    beforeB.complete(round);
                                }
                            }
                        }
                    }, result -> result.dependencies().keySet(), terminations::add)) {
                if (establishPermit) {
                    assertThat(decisions).containsExactly(Decision.PERMIT);
                    repository.publish(List.of(new BoundValue(cKey, Value.FALSE, null)));
                }
                assertThat(pip.opened).hasValue(1);
                assertThat(pip.delivered).hasValue(0);
                assertThat(rounds).last().satisfies(round -> {
                    assertThat(round.result().vote()).isNull();
                    assertThat(round.result().dependencies().keySet())
                            .extracting(key -> key.invocation().attributeName()).contains("access.a", "access.b");
                    assertThat(round.inputs().keySet()).extracting(key -> key.invocation().attributeName())
                            .doesNotContain("access.b");
                });

                repository.publish(List.of(new BoundValue(aKey, changedA, null)));
                val withoutB         = beforeB.completeOnTimeout(null, 1, TimeUnit.SECONDS).get(2, TimeUnit.SECONDS);
                val decisionsBeforeB = List.copyOf(decisions);
                if (withoutB == null) {
                    // Deliver B only after observing the gap, to prove the same policy still progresses.
                    pip.emit(releasedB);
                }
                val finalRound = completed.get(5, TimeUnit.SECONDS);
                assertThat(finalRound.result().dependencies()).containsOnlyKeys(a);
                assertThat(finalRound.result().vote().authorizationDecision().decision()).isEqualTo(expected);
                assertThat(terminations).isEmpty();
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(pip.closed).hasValue(1));
                assertThat(withoutB)
                        .as("A must make pending B unnecessary. Decisions before B: %s. After releasing B: %s.",
                                decisionsBeforeB, finalRound.result().vote().authorizationDecision().decision())
                        .isNotNull().satisfies(round -> {
                            assertThat(round.inputs().get(a).value()).isEqualTo(changedA);
                            assertThat(round.inputs().keySet()).extracting(key -> key.invocation().attributeName())
                                    .doesNotContain("access.b");
                        });
            }
        }
    }

    private static List<Arguments> lazyPolicies() {
        return List.of(
                arguments("AND completes false while B is pending", "<access.a> && " + PENDING_B, Value.TRUE,
                        Value.FALSE, Value.TRUE, false, Decision.NOT_APPLICABLE),
                arguments("OR completes true while B is pending", "<access.a> || " + PENDING_B, Value.FALSE, Value.TRUE,
                        Value.FALSE, false, Decision.PERMIT),
                arguments("a previous PERMIT is revised while B is pending",
                        "<access.a> && (<access.c> || " + PENDING_B + ")", Value.TRUE, Value.FALSE, Value.TRUE, true,
                        Decision.NOT_APPLICABLE));
    }

    private record EvaluationRound(Map<SubscriptionKey, AttributeSnapshot> inputs, VoteResult result) {}

    @PolicyInformationPoint(name = "access")
    public static final class PendingPip implements AutoCloseable {

        private final AtomicInteger           opened    = new AtomicInteger();
        private final AtomicInteger           delivered = new AtomicInteger();
        private final AtomicInteger           closed    = new AtomicInteger();
        private final LatestSlotStream<Value> pending   = new LatestSlotStream<>();

        PendingPip() {
            pending.onClose(closed::incrementAndGet);
        }

        @EnvironmentAttribute
        public Stream<Value> b() {
            opened.incrementAndGet();
            return pending;
        }

        @Override
        public void close() {
            pending.close();
        }

        private void emit(Value value) {
            delivered.incrementAndGet();
            pending.put(value);
        }
    }
}
