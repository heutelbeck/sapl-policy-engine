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
package io.sapl.benchmark.sapl4;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IndexingStrategy;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmark for the embedded Policy Decision Point. Each trial builds a
 * fresh PDP from the selected scenario. Subscriptions are cycled round-robin
 * across iterations to avoid JIT optimizing for a single constant input.
 * <p>
 * This class is public because JMH's code generator requires public access to
 * {@code @State} classes and {@code @Benchmark} methods.
 */
@State(Scope.Benchmark)
public class EmbeddedPdpBenchmark {

    @Param({})
    public String scenarioName;

    @Param({ "AUTO" })
    public String indexingStrategy;

    @Param({ "42" })
    public String seed;

    private PolicyDecisionPoint         pdp;
    private AuthorizationSubscription[] subscriptions;
    private PDPComponents               components;
    private final AtomicInteger         subscriptionIndex = new AtomicInteger(0);

    @Setup(Level.Trial)
    public void setup() {
        var scenario = ScenarioFactory.create(scenarioName, Long.parseLong(seed));
        var strategy = IndexingStrategy.valueOf(indexingStrategy.toUpperCase());
        components    = scenario.buildPdp(strategy);
        pdp           = components.pdp();
        subscriptions = scenario.subscriptions().toArray(AuthorizationSubscription[]::new);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (components != null) {
            components.dispose();
        }
    }

    /**
     * Baseline: returns a constant decision with no PDP evaluation. Measures
     * the JMH harness overhead ceiling.
     *
     * @return a constant PERMIT decision
     */
    @Benchmark
    public AuthorizationDecision noOp() {
        return AuthorizationDecision.PERMIT;
    }

    /**
     * Evaluates a single authorization decision using the blocking API.
     * Cycles through subscriptions round-robin.
     *
     * @return the authorization decision
     */
    @Benchmark
    public AuthorizationDecision decideOnceBlocking() {
        return pdp.decideOnceBlocking(nextSubscription());
    }

    /**
     * Evaluates a single authorization decision by subscribing to the reactive
     * stream and taking the first element.
     *
     * @return the authorization decision
     */
    @Benchmark
    public AuthorizationDecision decideStreamFirst() {
        return pdp.decide(nextSubscription()).blockFirst();
    }

    private AuthorizationSubscription nextSubscription() {
        var index = subscriptionIndex.getAndIncrement();
        return subscriptions[Integer.remainderUnsigned(index, subscriptions.length)];
    }

}
