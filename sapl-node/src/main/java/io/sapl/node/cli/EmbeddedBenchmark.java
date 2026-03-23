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

import java.nio.file.Path;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * JMH benchmark for the embedded Policy Decision Point. Measures pure policy
 * evaluation performance without network overhead.
 * <p>
 * This class must be {@code public} because JMH's code generator requires
 * public access to {@code @State} classes and {@code @Benchmark} methods.
 */
@State(Scope.Benchmark)
public class EmbeddedBenchmark {

    @Param({})
    public String contextJson;

    private PolicyDecisionPoint       pdp;
    private AuthorizationSubscription subscription;
    private PDPComponents             components;

    @Setup(Level.Trial)
    public void setup() {
        val ctx    = BenchmarkContext.fromJson(contextJson);
        val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        subscription = mapper.readValue(ctx.subscriptionJson(), AuthorizationSubscription.class);
        components   = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of(ctx.policiesPath()))
                .build();
        pdp          = components.pdp();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (components != null) {
            components.dispose();
        }
    }

    @Benchmark
    public AuthorizationDecision decideOnceBlocking() {
        return pdp.decideOnceBlocking(subscription);
    }

}
