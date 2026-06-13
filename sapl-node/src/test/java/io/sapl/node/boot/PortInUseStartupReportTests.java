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
package io.sapl.node.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.core.io.support.SpringFactoriesLoader;

import lombok.val;

/**
 * Specifications for the operator-facing startup report when the HTTP server
 * port is already in use.
 * <p>
 * Spring Boot raises {@code PortInUseException} from the embedded Jetty
 * container when {@code server.port} is occupied, and ships a registered
 * {@code FailureAnalyzer} that turns it into a short Description plus Action
 * instead of a servlet-container stack trace. This test pins that the node's
 * dependency wiring still produces that clean report so the HTTP path stays
 * consistent with the RSocket port-in-use message. Loading the analyzers via
 * {@code SpringFactoriesLoader} mirrors exactly how Spring Boot resolves them
 * at boot, including under AOT and native image.
 */
@DisplayName("HTTP port-in-use startup report")
class PortInUseStartupReportTests {

    private static final int OCCUPIED_PORT = 8080;

    @Test
    @DisplayName("an in-use HTTP server port is reported as a clean analysis naming the port and a remedy, not a stack trace")
    void whenHttpPortInUseThenRegisteredAnalyzerProducesCleanReport() {
        val analysis = analyzeWithRegisteredAnalyzers(new PortInUseException(OCCUPIED_PORT));

        assertThat(analysis).isNotNull().satisfies(
                a -> assertThat(a.getDescription()).contains(String.valueOf(OCCUPIED_PORT)).contains("already in use"))
                .satisfies(a -> assertThat(a.getAction()).contains(String.valueOf(OCCUPIED_PORT))
                        .containsIgnoringCase("another port"));
    }

    private static FailureAnalysis analyzeWithRegisteredAnalyzers(Throwable failure) {
        // Some registered analyzers need a live ApplicationContext to construct;
        // they are irrelevant here and skipped via a no-op failure handler.
        val analyzers = SpringFactoriesLoader.forDefaultResourceLocation().load(FailureAnalyzer.class,
                (factoryType, factoryImplementationName, failureCause) -> {});
        return analyzers.stream().map(analyzer -> analyzer.analyze(failure)).filter(Objects::nonNull).findFirst()
                .orElse(null);
    }

}
