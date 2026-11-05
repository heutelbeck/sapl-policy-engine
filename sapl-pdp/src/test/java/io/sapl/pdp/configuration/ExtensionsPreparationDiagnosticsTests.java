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
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.valfirst.slf4jtest.LoggingEvent;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;
import io.sapl.pdp.configuration.PreparationResult.Accepted;
import io.sapl.pdp.configuration.PreparationResult.Rejected;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Extension preparation reports why a configuration was rejected")
class ExtensionsPreparationDiagnosticsTests {

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

    @Test
    @DisplayName("A plain PDP accepts optional extensions without declaring support for them")
    void whenExtensionsAreNotRequiredThenPlainPdpAcceptsPreparation() {
        val configuration = configuration("accepted", Set.of());

        assertThat(new EmptyExtensionsProcessor().prepare(PDP_ID, configuration)).isInstanceOf(Accepted.class);
    }

    @MethodSource("requiredRequirements")
    @DisplayName("A plain PDP identifies every unsupported required extension")
    @ParameterizedTest(name = "Unsupported required extensions {0} are named in the rejection")
    void whenRequiredExtensionsAreUnsupportedThenPreparationExplainsRejection(Set<String> required) {
        val configuration = configuration("rejected", required);

        assertThat(new EmptyExtensionsProcessor().prepare(PDP_ID, configuration)).isInstanceOfSatisfying(Rejected.class,
                rejection -> assertThat(rejection.reason()).contains(required.toArray(String[]::new)));
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "A last good configuration exists: {0}")
    @DisplayName("Extension rejection reasons reach the status and log with the rejected publication identity")
    void whenPreparationIsRejectedThenReasonIdentifiesConfigurationAndMissingExtensions(boolean previous) {
        try (val source = new PdpVoterSource(new StaticPluginsSource(plugins()), Clock.systemUTC())) {
            if (previous) {
                source.loadConfiguration(configuration("active-config", Set.of()));
            }
            val original = source.getCurrentConfiguration(PDP_ID);

            source.loadConfiguration(configuration("rejected-config", Set.of("limits", "audit")));

            assertThat(source.getPdpStatus(PDP_ID)).get()
                    .returns(previous ? PdpState.STALE : PdpState.ERROR, PdpStatus::state)
                    .returns(previous ? "active-config" : null, PdpStatus::configurationId).satisfies(status -> {
                        assertThat(status.lastError()).contains("rejected-config", PDP_ID, "limits", "audit");
                        assertThat(getTestLogger(PdpVoterSource.class).getLoggingEvents())
                                .extracting(LoggingEvent::getFormattedMessage).singleElement().asString()
                                .contains(status.lastError());
                    });
            if (previous) {
                assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(original.orElseThrow());
            } else {
                assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("A rejected required extension cannot become active when plugins arrive later")
    void whenPreparationIsRejectedBeforePluginsThenConfigurationIsNotRetained() {
        val plugins = new MutablePluginsSource();
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            source.loadConfiguration(configuration("rejected", Set.of("limits")));
            plugins.publish(plugins());

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state)
                    .satisfies(status -> assertThat(status.lastError()).contains("limits"));
        }
    }

    @Test
    @DisplayName("A later accepted configuration clears the extension rejection")
    void whenCompatibleConfigurationFollowsRejectedPreparationThenPdpRecovers() {
        try (val source = new PdpVoterSource(new StaticPluginsSource(plugins()), Clock.systemUTC())) {
            source.loadConfiguration(configuration("rejected", Set.of("limits")));
            source.loadConfiguration(configuration("accepted", Set.of()));

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state)
                    .returns("accepted", PdpStatus::configurationId).returns(null, PdpStatus::lastError);
        }
    }

    @Test
    @DisplayName("A host returning no preparation result fails closed with a contract diagnostic")
    void whenHostReturnsNoPreparationResultThenConfigurationIsRejected() {
        try (val source = new PdpVoterSource(new StaticPluginsSource(plugins()), Clock.systemUTC(), host)) {
            source.loadConfiguration(configuration("rejected", Set.of()));

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state)
                    .satisfies(status -> assertThat(status.lastError()).contains("returned no preparation result",
                            "rejected", PDP_ID));
        }
    }

    static Stream<Arguments> requiredRequirements() {
        return Stream.of(arguments(Set.of("limits")), arguments(Set.of("limits", "audit")));
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(SaplTesting.FUNCTION_BROKER);
    }

    private static PDPConfiguration configuration(String id, Set<String> required) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(POLICY),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT),
                Map.of("limits", Value.EMPTY_OBJECT, "audit", Value.EMPTY_OBJECT), Map.of(), required,
                UnknownProvenance.INSTANCE);
    }
}
