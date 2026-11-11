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
package io.sapl.pdp.configuration;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.valfirst.slf4jtest.LoggingEvent;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;

import lombok.val;

@DisplayName("Compilation rejection diagnostics identify the rejected publication")
class PdpVoterSourceCompilationDiagnosticsTests {

    private static final String PDP_ID                = "orders";
    private static final String VALID_POLICY          = """
            policy "access" permit""";
    private static final String INVALID_REGEX_POLICY  = """
            policy "access"
            permit
                subject =~ "(";
            """;
    private static final String INVALID_SYNTAX_POLICY = "not a SAPL document";

    @AfterEach
    @BeforeEach
    void clearRejectionLog() {
        getTestLogger(PdpVoterSource.class).clear();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("compilationFailures")
    @DisplayName("The status and log name the rejected configuration and preserve compiler details")
    void whenCompilationFailsThenStatusAndLogIdentifyRejectedConfiguration(String description,
            boolean previousConfiguration, String policy, PdpState expectedState, List<String> compilerDetails) {
        val plugins = new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER));
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            if (previousConfiguration) {
                source.loadConfiguration(configuration("active-config", VALID_POLICY));
            }
            val original = source.getCurrentConfiguration(PDP_ID);

            source.loadConfiguration(configuration("rejected-config", policy));

            val status = source.getPdpStatus(PDP_ID).orElseThrow();
            assertThat(status).returns(expectedState, PdpStatus::state)
                    .returns(previousConfiguration ? "active-config" : null, PdpStatus::configurationId);
            assertThat(status.lastError()).contains(compilerDetails)
                    .startsWith("Configuration 'rejected-config' for pdpId 'orders' was rejected.")
                    .doesNotContain("Configuration 'active-config'");
            assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents())
                    .extracting(LoggingEvent::getFormattedMessage).containsExactly(status.lastError());
            if (previousConfiguration) {
                assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(original.orElseThrow());
            }
        }
    }

    static Stream<Arguments> compilationFailures() {
        val regexDetails  = List.of("SAPL Compilation Error", "Invalid regular expression", "Document: access",
                "Location: line 3", "subject =~", "^");
        val syntaxDetails = List.of("SAPL Compilation Error", "Error:", "line 1");
        return Stream.of(arguments("Initial regex failure", false, INVALID_REGEX_POLICY, PdpState.ERROR, regexDetails),
                arguments("Regex failure in an update", true, INVALID_REGEX_POLICY, PdpState.STALE, regexDetails),
                arguments("Initial syntax failure", false, INVALID_SYNTAX_POLICY, PdpState.ERROR, syntaxDetails),
                arguments("Syntax failure in an update", true, INVALID_SYNTAX_POLICY, PdpState.STALE, syntaxDetails));
    }

    private static PDPConfiguration configuration(String id, String policy) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}
