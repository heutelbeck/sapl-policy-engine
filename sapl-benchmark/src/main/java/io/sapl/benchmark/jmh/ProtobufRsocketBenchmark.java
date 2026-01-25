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

import java.io.IOException;

import javax.net.ssl.SSLException;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.benchmark.BenchmarkExecutionContext;
import io.sapl.pdp.remote.ProtobufRemotePolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * JMH benchmark for the protobuf-based RSocket PDP client. This benchmark
 * measures the performance of the high-performance protobuf serialization path.
 */
@Slf4j
@State(Scope.Benchmark)
public class ProtobufRsocketBenchmark {

    @Param({ "{}" })
    String contextJsonString;

    private PolicyDecisionPoint       noauthPdp;
    private BenchmarkExecutionContext context;

    private ProtobufRemotePolicyDecisionPoint.Builder getBaseBuilder() throws SSLException {
        var builder = ProtobufRemotePolicyDecisionPoint.builder().host(context.getRsocketHost())
                .port(context.getProtobufRsocketPort());
        if (context.isUseSsl()) {
            builder = builder.withUnsecureSSL();
        }
        return builder;
    }

    @Setup(Level.Trial)
    public void setup() throws IOException {
        context = BenchmarkExecutionContext.fromString(contextJsonString);
        log.info("Initializing Protobuf RSocket PDP and starting Benchmark ...");
        // Protobuf endpoint currently only supports no-auth
        // Auth is handled at connection setup, not yet implemented
        if (context.isUseNoAuth()) {
            noauthPdp = getBaseBuilder().build();
        }
    }

    @Benchmark
    public void noAuthDecideSubscribe() {
        decide(noauthPdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void noAuthDecideOnce() {
        decideOnce(noauthPdp, context.getAuthorizationSubscription());
    }
}
