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
package io.sapl.benchmark.sapl3;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;

/**
 * JMH benchmark for the SAPL 3.0 embedded PDP. Each trial builds a fresh PDP
 * from the selected scenario.
 * <p>
 * This class is public because JMH's code generator requires public access to
 * {@code @State} classes and {@code @Benchmark} methods.
 */
@State(Scope.Benchmark)
public class EmbeddedPdpBenchmark {

    @Param({})
    public String scenarioName;

    private PolicyDecisionPoint       pdp;
    private AuthorizationSubscription subscription;

    @Setup(Level.Trial)
    public void setup() throws IOException, io.sapl.interpreter.InitializationException {
        var scenario = Scenario.fromName(scenarioName);
        pdp          = scenario.buildPdp();
        subscription = scenario.subscription();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (pdp instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // cleanup only
            }
        }
    }

    /**
     * Evaluates a single authorization decision using the reactive stream and
     * taking the first element.
     *
     * @return the authorization decision
     */
    @Benchmark
    public AuthorizationDecision decideFirst() {
        return pdp.decide(subscription).blockFirst();
    }

    /**
     * Evaluates a single authorization decision using the blocking Mono API.
     *
     * @return the authorization decision
     */
    @Benchmark
    public AuthorizationDecision decideOnce() {
        return pdp.decideOnce(subscription).block();
    }

}
