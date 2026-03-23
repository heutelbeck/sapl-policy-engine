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
package io.sapl.node.cli;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import javax.net.ssl.SSLException;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * JMH benchmark for a remote Policy Decision Point. Measures end-to-end
 * latency including HTTP transport, serialization, and server-side evaluation.
 * <p>
 * This class must be {@code public} because JMH's code generator requires
 * public access to {@code @State} classes and {@code @Benchmark} methods.
 */
@State(Scope.Benchmark)
public class RemoteBenchmark {

    @Param({})
    public String contextJson;

    private PolicyDecisionPoint       pdp;
    private AuthorizationSubscription subscription;

    @Setup(Level.Trial)
    public void setup() throws SSLException {
        val ctx    = BenchmarkContext.fromJson(contextJson);
        val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        subscription = mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class);
        pdp          = RemotePdpFactory.create(ctx);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pdp = null;
    }

    @Benchmark
    public AuthorizationDecision decideOnceBlocking() {
        return pdp.decideOnceBlocking(subscription);
    }

    @Benchmark
    public AuthorizationDecision decideOnceReactive() {
        return pdp.decideOnce(subscription).block();
    }

    @Benchmark
    public AuthorizationDecision decideStreamFirst() {
        return pdp.decide(subscription).blockFirst();
    }

}
