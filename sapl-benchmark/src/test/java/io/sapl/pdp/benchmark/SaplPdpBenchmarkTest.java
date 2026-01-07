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
package io.sapl.pdp.benchmark;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;

import io.sapl.benchmark.BenchmarkConfiguration;
import io.sapl.benchmark.BenchmarkExecutionContext;
import io.sapl.benchmark.jmh.EmbeddedBenchmark;
import io.sapl.benchmark.jmh.Helper;
import io.sapl.benchmark.jmh.HttpBenchmark;
import io.sapl.benchmark.jmh.RsocketBenchmark;
import io.sapl.benchmark.util.BenchmarkException;

class SaplPdpBenchmarkTest {
    private static final String TMP_REPORT_PATH = "target/tmp_benchmark_test";

    @BeforeAll
    static void createEmptyBenchmarkFolder() throws IOException {
        final var tmpReportPathFile = new File(TMP_REPORT_PATH);
        FileUtils.deleteDirectory(tmpReportPathFile);
        Assertions.assertTrue(tmpReportPathFile.mkdir());
    }

    @Test
    void whenExecutingEmbeddedBenchmark_withValidSubscription_thenDecisionIsAccepted() throws IOException {
        final var benchmarkConfig = BenchmarkConfiguration
                .fromFile("src/test/resources/unittest_benchmark_config.yaml");
        benchmarkConfig.setRunHttpBenchmarks(false);
        benchmarkConfig.setRunRsocketBenchmarks(false);
        benchmarkConfig
                .setSubscription("{\"subject\": \"Willi\", \"action\": \"requests\", \"resource\": \"information\"}");
        final var embeddedBenchmark = new EmbeddedBenchmark();
        final var benchmarkContext  = BenchmarkExecutionContext.fromBenchmarkConfiguration(benchmarkConfig);
        try (MockedStatic<BenchmarkExecutionContext> utilities = Mockito.mockStatic(BenchmarkExecutionContext.class)) {
            utilities.when(() -> BenchmarkExecutionContext.fromString(any())).thenReturn(benchmarkContext);
            embeddedBenchmark.setup();
            assertDoesNotThrow(() -> {
                embeddedBenchmark.noAuthDecideOnce();
                embeddedBenchmark.noAuthDecideSubscribe();
            });
        }
    }

    @Test
    void whenExecutingEmbeddedBenchmark_withInvalidSubscription_thenExceptionIsThrown() throws IOException {
        final var benchmarkConfig = BenchmarkConfiguration
                .fromFile("src/test/resources/unittest_benchmark_config.yaml");
        benchmarkConfig.setRunHttpBenchmarks(false);
        benchmarkConfig.setRunRsocketBenchmarks(false);
        benchmarkConfig.setSubscription(
                "{\"subject\": \"Willi\", \"action\": \"invalid action\", \"resource\": \"information\"}");
        final var embeddedBenchmark = new EmbeddedBenchmark();
        final var benchmarkContext  = BenchmarkExecutionContext.fromBenchmarkConfiguration(benchmarkConfig);
        try (MockedStatic<BenchmarkExecutionContext> utilities = Mockito.mockStatic(BenchmarkExecutionContext.class)) {
            utilities.when(() -> BenchmarkExecutionContext.fromString(any())).thenReturn(benchmarkContext);
            embeddedBenchmark.setup();
            assertThrows(BenchmarkException.class, embeddedBenchmark::noAuthDecideOnce);
            assertThrows(BenchmarkException.class, embeddedBenchmark::noAuthDecideSubscribe);
        }
    }

    @Test
    void whenLoadingContaxtFromString_withInvalidJson_thenExcpetionIsThrown() {
        assertThrows(Exception.class, () -> BenchmarkExecutionContext.fromString("{invalidjson]"));
    }

    @Test
    void whenExecutingHttpBenchmark_thenDecisionIsAccepted() throws IOException {
        final var mockedContainer = Mockito.mock(GenericContainer.class);
        final var benchmarkConfig = BenchmarkConfiguration
                .fromFile("src/test/resources/unittest_benchmark_config.yaml");
        benchmarkConfig.setRunEmbeddedBenchmarks(true);
        benchmarkConfig.setRunHttpBenchmarks(true);
        benchmarkConfig.setRunHttpBenchmarks(true);
        benchmarkConfig.setRunRsocketBenchmarks(false);
        benchmarkConfig.setUseBasicAuth(true);
        benchmarkConfig.setBasicClientKey("123");
        benchmarkConfig.setBasicClientSecret("123");
        benchmarkConfig.setUseAuthApiKey(true);
        benchmarkConfig.setApiKeySecret("123");
        benchmarkConfig.setUseOauth2(true);
        final var benchmark        = new HttpBenchmark();
        final var benchmarkContext = BenchmarkExecutionContext.fromBenchmarkConfiguration(benchmarkConfig,
                mockedContainer, mockedContainer);
        try (MockedStatic<BenchmarkExecutionContext> utilities = Mockito.mockStatic(BenchmarkExecutionContext.class)) {
            utilities.when(() -> BenchmarkExecutionContext.fromString(any())).thenReturn(benchmarkContext);
            benchmark.setup();
            try (MockedStatic<Helper> mockedHelper = Mockito.mockStatic(Helper.class)) {
                mockedHelper.when(() -> Helper.decide(any(), any())).then(x -> null);
                mockedHelper.when(() -> Helper.decideOnce(any(), any())).then(x -> null);
                // NoAuth
                assertDoesNotThrow(benchmark::noAuthDecideOnce);
                assertDoesNotThrow(benchmark::noAuthDecideSubscribe);
                // BasicAuth
                assertDoesNotThrow(benchmark::basicAuthDecideOnce);
                assertDoesNotThrow(benchmark::basicAuthDecideSubscribe);
                // ApiKey
                assertDoesNotThrow(benchmark::apiKeyDecideOnce);
                assertDoesNotThrow(benchmark::apiKeyDecideSubscribe);
                // Oauth2
                assertDoesNotThrow(benchmark::oAuth2DecideOnce);
                assertDoesNotThrow(benchmark::oAuth2DecideSubscribe);
            }
        }
    }

    @Test
    void whenExecutingRsocketBenchmark_thenDecisionIsAccepted() throws IOException {
        final var mockedContainer = Mockito.mock(GenericContainer.class);
        Mockito.when(mockedContainer.getHost()).thenReturn("localhost");
        final var benchmarkConfig = BenchmarkConfiguration
                .fromFile("src/test/resources/unittest_benchmark_config.yaml");
        benchmarkConfig.setRunEmbeddedBenchmarks(false);
        benchmarkConfig.setRunHttpBenchmarks(false);
        benchmarkConfig.setRunRsocketBenchmarks(true);
        benchmarkConfig.setUseBasicAuth(true);
        benchmarkConfig.setBasicClientKey("123");
        benchmarkConfig.setBasicClientSecret("123");
        benchmarkConfig.setUseAuthApiKey(true);
        benchmarkConfig.setApiKeySecret("123");
        final var benchmark        = new RsocketBenchmark();
        final var benchmarkContext = BenchmarkExecutionContext.fromBenchmarkConfiguration(benchmarkConfig,
                mockedContainer, mockedContainer);
        try (MockedStatic<BenchmarkExecutionContext> utilities = Mockito.mockStatic(BenchmarkExecutionContext.class)) {
            utilities.when(() -> BenchmarkExecutionContext.fromString(any())).thenReturn(benchmarkContext);
            benchmark.setup();
            try (MockedStatic<Helper> mockedHelper = Mockito.mockStatic(Helper.class)) {
                mockedHelper.when(() -> Helper.decide(any(), any())).then(x -> null);
                mockedHelper.when(() -> Helper.decideOnce(any(), any())).then(x -> null);
                // NoAuth
                assertDoesNotThrow(benchmark::noAuthDecideOnce);
                assertDoesNotThrow(benchmark::noAuthDecideSubscribe);
                // BasicAuth
                assertDoesNotThrow(benchmark::basicAuthDecideOnce);
                assertDoesNotThrow(benchmark::basicAuthDecideSubscribe);
                // ApiKey
                assertDoesNotThrow(benchmark::apiKeyDecideOnce);
                assertDoesNotThrow(benchmark::apiKeyDecideSubscribe);
                // Oauth2
                assertDoesNotThrow(benchmark::oAuth2DecideOnce);
                assertDoesNotThrow(benchmark::oAuth2DecideSubscribe);
            }
        }
    }
}
