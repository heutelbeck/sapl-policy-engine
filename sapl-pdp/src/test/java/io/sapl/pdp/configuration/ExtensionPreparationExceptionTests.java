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
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.PreparationResult.Accepted;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unexpected extension preparation failures explain the operation and original exception")
class ExtensionPreparationExceptionTests {

    private static final String PDP_ID = "orders";
    private static final String POLICY = """
            policy "access" permit""";

    @Mock
    ExtensionsProcessor host;

    @AfterEach
    @BeforeEach
    void clearRejectionLog() {
        getTestLogger(PdpVoterSource.class).clear();
    }

    @MethodSource("preparationFailures")
    @ParameterizedTest(name = "Previous configuration: {0}, exception: {1}")
    @DisplayName("The original failure is explained in the status and retained in the log")
    void whenHostThrowsDuringPreparationThenStatusAndLogIdentifyTheCause(boolean previous, RuntimeException failure,
            String expectedMessage) {
        val rejected = configuration("rejected");
        when(host.prepare(PDP_ID, rejected)).thenThrow(failure);
        val plugins = new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER));
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC(), host)) {
            if (previous) {
                val active = configuration("active");
                when(host.prepare(PDP_ID, active)).thenReturn(new Accepted());
                source.loadConfiguration(active);
            }
            val original = source.getCurrentConfiguration(PDP_ID);

            source.loadConfiguration(rejected);

            assertThat(source.getPdpStatus(PDP_ID)).get()
                    .returns(previous ? PdpState.STALE : PdpState.ERROR, PdpStatus::state)
                    .returns(previous ? "active" : null, PdpStatus::configurationId)
                    .satisfies(status -> assertThat(status.lastError()).contains("Configuration 'rejected'", PDP_ID,
                            "Extension preparation failed", failure.getClass().getSimpleName(), expectedMessage));
            assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents())
                    .filteredOn(event -> event.getThrowable().isPresent()).singleElement().satisfies(event -> {
                        assertThat(event.getThrowable()).containsSame(failure);
                        assertThat(event.getFormattedMessage()).contains("preparation", "rejected", PDP_ID,
                                failure.getClass().getSimpleName(), expectedMessage);
                    });
            if (previous) {
                assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(original.orElseThrow());
            } else {
                assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            }
        }
    }

    static Stream<Arguments> preparationFailures() {
        return Stream.of(false, true)
                .flatMap(previous -> Stream.of(
                        arguments(previous, new IllegalStateException("Missing endpoint."), "Missing endpoint."),
                        arguments(previous, new IllegalArgumentException("Invalid transport."), "Invalid transport."),
                        arguments(previous, new IllegalStateException(), "No exception message was provided."),
                        arguments(previous, new IllegalStateException(""), "No exception message was provided."),
                        arguments(previous, new IllegalStateException(" "), "No exception message was provided.")));
    }

    private static PDPConfiguration configuration(String id) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(POLICY),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}
