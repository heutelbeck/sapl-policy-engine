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
package io.sapl.benchmark.jmh;

import static io.sapl.benchmark.jmh.Helper.decide;
import static io.sapl.benchmark.jmh.Helper.decideOnce;

import io.sapl.benchmark.util.EchoPIP;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.State;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.benchmark.BenchmarkExecutionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@State(Scope.Benchmark)
public class EmbeddedBenchmark {

    @Param({ "{}" })
    String                                           contextJsonString;
    private PolicyDecisionPoint                      pdp;
    private BenchmarkExecutionContext                context;
    private PolicyDecisionPointBuilder.PDPComponents components;

    @Setup(Level.Trial)
    public void setup() {
        context = BenchmarkExecutionContext.fromString(contextJsonString);
        log.info("Initializing PDP and starting benchmark ...");
        components = PolicyDecisionPointBuilder.withDefaults().withResourcesSource()
                .withPolicyInformationPoint(new EchoPIP()).build();
        pdp        = components.pdp();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        log.info("Shutdown benchmark ...");
        components.dispose();
    }

    @Benchmark
    public void noAuthDecideSubscribe() {
        decide(pdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void noAuthDecideOnce() {
        decideOnce(pdp, context.getAuthorizationSubscription());
    }
}
