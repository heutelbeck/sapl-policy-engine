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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.Voter;
import lombok.val;

import static io.sapl.attributes.broker.BrokerEvalLoops.openWithHead;
import static io.sapl.compiler.policy.PolicyCompiler.compilePolicy;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parsePolicy;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(20)
@DisplayName("Lazy policy evaluation across atomic repository changes")
class LazyRepositoryEvaluationTests {

    @Test
    @DisplayName("an atomic swap after discovering B reevaluates A and cannot grant access from old A and new B")
    void whenAttributesSwapAfterLazyDiscoveryThenThePolicyReevaluatesAAndDropsB() throws Exception {
        // The policy needs A and B, but discovers B lazily.
        // Initially, A=true and B=false, so the correct result is false.
        // After evaluating A and discovering B, but before evaluating B,
        // an atomic update publishes A=false and B=true.
        // If only B were reevaluated while retaining the old A=true,
        // the result would incorrectly be true instead of false.
        // The eval loop must therefore reevaluate A as well. With A=false,
        // the result is false, and B is no longer needed.
        val policy       = lazyPolicy();
        val a            = policy.a();
        val b            = policy.b();
        val aKey         = RepositoryKey.fromInvocation(a.invocation());
        val bKey         = RepositoryKey.fromInvocation(b.invocation());
        val rounds       = new CopyOnWriteArrayList<EvaluationRound>();
        val decisions    = new CopyOnWriteArrayList<Decision>();
        val terminations = new CopyOnWriteArrayList<RuntimeException>();
        val bDiscovered  = new CompletableFuture<Void>();
        val resume       = new CompletableFuture<Void>();
        val pauseOnce    = new AtomicBoolean(true);

        assertThat(policy.evaluate(Value.TRUE, Value.FALSE).vote().authorizationDecision().decision())
                .isEqualTo(Decision.NOT_APPLICABLE);

        try (val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository);
                val worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
            repository
                    .publish(List.of(new BoundValue(aKey, Value.TRUE, null), new BoundValue(bKey, Value.FALSE, null)));
            val opening = worker.submit(() -> openWithHead(broker, "lazy-attribute-swap", Set.of(a),
                    attributes -> policy.voter().evaluate(policy.context().withSnapshot(attributes)),
                    (result, attributes) -> {
                        rounds.add(new EvaluationRound(attributes, result));
                        val vote = result.vote();
                        if (vote != null) {
                            decisions.add(vote.authorizationDecision().decision());
                        }
                        if (vote == null && result.dependencies().containsKey(b) && pauseOnce.getAndSet(false)) {
                            bDiscovered.complete(null);
                            resume.orTimeout(5, TimeUnit.SECONDS).join();
                        }
                    }, result -> result.dependencies().keySet(), terminations::add));
            try {
                bDiscovered.get(5, TimeUnit.SECONDS);
                assertThat(rounds).singleElement().satisfies(round -> {
                    assertThat(round.attributes()).containsOnlyKeys(a);
                    assertThat(round.attributes().get(a).value()).isEqualTo(Value.TRUE);
                    assertThat(round.result().vote()).isNull();
                    assertThat(round.result().dependencies()).containsOnlyKeys(a, b);
                });
                repository.publish(
                        List.of(new BoundValue(aKey, Value.FALSE, null), new BoundValue(bKey, Value.TRUE, null)));
            } finally {
                resume.complete(null);
            }

            try (val subscription = opening.get(5, TimeUnit.SECONDS)) {
                assertThat(decisions).isNotEmpty().containsOnly(Decision.NOT_APPLICABLE);
                assertThat(rounds).anySatisfy(round -> {
                    assertThat(round.attributes()).containsOnlyKeys(a, b);
                    assertThat(round.attributes().get(a).value()).isEqualTo(Value.FALSE);
                    assertThat(round.attributes().get(b).value()).isEqualTo(Value.TRUE);
                    assertThat(round.result().dependencies()).containsOnlyKeys(a);
                });
                repository.publish(
                        List.of(new BoundValue(aKey, Value.TRUE, null), new BoundValue(bKey, Value.TRUE, null)));
                assertThat(decisions).contains(Decision.PERMIT).containsOnly(Decision.NOT_APPLICABLE, Decision.PERMIT)
                        .last().isEqualTo(Decision.PERMIT);
                assertThat(terminations).isEmpty();
            }
        }
    }

    private static LazyPolicy lazyPolicy() {
        val source  = parsePolicy("""
                policy "Access requires both approvals"
                permit
                    <access.a> && <access.b>;
                """);
        val voter   = compilePolicy(source, compilationContext()).applicabilityAndVote();
        val context = evaluationContext();
        val initial = voter.evaluate(context);
        assertThat(initial.dependencies()).hasSize(1);
        val a = initial.dependencies().keySet().iterator().next();
        assertThat(a.invocation().attributeName()).isEqualTo("access.a");
        val next = voter.evaluate(context.withSnapshot(Map.of(a, new AttributeSnapshot(Value.TRUE, Instant.EPOCH))));
        assertThat(next.dependencies()).hasSize(2).containsKey(a);
        val b = next.dependencies().keySet().stream().filter(key -> !key.equals(a)).findFirst().orElseThrow();
        assertThat(b.invocation().attributeName()).isEqualTo("access.b");
        return new LazyPolicy(voter, context, a, b);
    }

    private record LazyPolicy(Voter voter, EvaluationContext context, SubscriptionKey a, SubscriptionKey b) {
        VoteResult evaluate(Value first, Value second) {
            return voter.evaluate(context.withSnapshot(Map.of(a, new AttributeSnapshot(first, Instant.EPOCH), b,
                    new AttributeSnapshot(second, Instant.EPOCH))));
        }
    }

    private record EvaluationRound(Map<SubscriptionKey, AttributeSnapshot> attributes, VoteResult result) {}
}
