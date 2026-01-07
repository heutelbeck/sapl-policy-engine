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
import static io.sapl.benchmark.jmh.Helper.getClientRegistrationRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import javax.net.ssl.SSLException;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import io.netty.channel.ChannelOption;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.benchmark.BenchmarkExecutionContext;
import io.sapl.pdp.remote.RemoteHttpPolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Slf4j
@State(Scope.Benchmark)
public class HttpBenchmark {
    @Param({ "{}" })
    String contextJsonString;

    private PolicyDecisionPoint       noauthPdp;
    private PolicyDecisionPoint       basicAuthPdp;
    private PolicyDecisionPoint       apiKeyPdp;
    private PolicyDecisionPoint       oauth2Pdp;
    private BenchmarkExecutionContext context;

    private RemoteHttpPolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder getBaseBuilder() throws SSLException {
        return RemotePolicyDecisionPoint.builder().http().baseUrl(context.getHttpBaseUrl())
                .withHttpClient(HttpClient.create(ConnectionProvider.builder("custom")
                        // size connection pool depending on threads
                        .maxConnections((int) (Collections.max(context.getThreadList()) * 1.5)).build())
                        .responseTimeout(Duration.ofSeconds(10)))
                .withUnsecureSSL()
                // set SO_LINGER to 0 so that the http sockets are closed immediately ->
                // TIME_WAIT
                .option(ChannelOption.SO_LINGER, 0);
    }

    @Setup(Level.Trial)
    public void setup() throws IOException {
        context = BenchmarkExecutionContext.fromString(contextJsonString);
        log.info("initializing PDP and starting Benchmark ...");
        if (context.isUseNoAuth()) {
            noauthPdp = getBaseBuilder().build();
        }

        if (context.isUseBasicAuth()) {
            basicAuthPdp = getBaseBuilder().basicAuth(context.getBasicClientKey(), context.getBasicClientSecret())
                    .build();
        }

        if (context.isUseAuthApiKey()) {
            apiKeyPdp = getBaseBuilder().apiKey(context.getApiKey()).build();
        }

        if (context.isUseOauth2()) {
            oauth2Pdp = getBaseBuilder().oauth2(getClientRegistrationRepository(context), "saplPdp").build();
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

    @Benchmark
    public void basicAuthDecideSubscribe() {
        decide(basicAuthPdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void basicAuthDecideOnce() {
        decideOnce(basicAuthPdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void apiKeyDecideSubscribe() {
        decide(apiKeyPdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void apiKeyDecideOnce() {
        decideOnce(apiKeyPdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void oAuth2DecideSubscribe() {
        decide(oauth2Pdp, context.getAuthorizationSubscription());
    }

    @Benchmark
    public void oAuth2DecideOnce() {
        decideOnce(oauth2Pdp, context.getAuthorizationSubscription());
    }
}
