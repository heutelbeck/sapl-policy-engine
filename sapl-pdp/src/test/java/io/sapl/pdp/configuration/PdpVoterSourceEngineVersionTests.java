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

import static io.sapl.pdp.SaplBuildInfo.version;
import static io.sapl.pdp.configuration.PdpVoterSource.engineVersionError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;

import lombok.val;

@Timeout(10)
@ExtendWith(MockitoExtension.class)
@DisplayName("Engine requirements govern configuration admission and preserve running subscriptions")
class PdpVoterSourceEngineVersionTests {

    private static final String PDP_ID        = "engine-test";
    private static final String PERMIT_POLICY = """
            policy "access" permit""";
    private static final String DENY_POLICY   = """
            policy "access" deny""";

    @Mock
    ExtensionsProcessor extensions;
    @Mock
    AttributeBroker     attributeBroker;

    @NullSource
    @ValueSource(strings = { "exact", "minimum" })
    @ParameterizedTest(name = "Requirement {0} accepts the current engine")
    @DisplayName("A missing or matching requirement permits configuration loading")
    void whenRequirementMatchesOrIsMissingThenConfigurationIsLoaded(String kind) {
        String requirement = null;
        if ("exact".equals(kind)) {
            requirement = version();
        } else if ("minimum".equals(kind)) {
            requirement = ">=" + version();
        }
        try (val source = source()) {
            source.loadConfiguration(configuration("accepted", requirement, PERMIT_POLICY));

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state).returns("accepted",
                    PdpStatus::configurationId);
        }
    }

    @Test
    @DisplayName("An incompatible initial configuration fails closed and reports both versions")
    void whenInitialRequirementDoesNotMatchThenNoConfigurationIsServed() {
        val requirement = ">" + version();
        try (val source = source()) {
            source.loadConfiguration(configuration("rejected", requirement, PERMIT_POLICY));

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state)
                    .satisfies(status -> assertThat(status.lastError()).contains("requiredEngineVersion", version(),
                            requirement, PDP_ID, "Configuration 'rejected'"));
        }
    }

    @Test
    @DisplayName("A compatible configuration recovers from a rejected initial requirement")
    void whenCompatibleConfigurationFollowsRejectionThenPdpRecovers() {
        try (val source = source()) {
            source.loadConfiguration(configuration("rejected", ">" + version(), PERMIT_POLICY));
            source.loadConfiguration(configuration("accepted", version(), PERMIT_POLICY));

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state)
                    .returns("accepted", PdpStatus::configurationId).returns(null, PdpStatus::lastError);
        }
    }

    @Test
    @DisplayName("An incompatible update preserves the last good voter and marks it stale")
    void whenUpdateIsIncompatibleThenLastGoodConfigurationKeepsServing() {
        try (val source = source()) {
            source.loadConfiguration(configuration("accepted", null, PERMIT_POLICY));
            val original = source.getCurrentConfiguration(PDP_ID).orElseThrow();

            source.loadConfiguration(configuration("rejected", ">" + version(), DENY_POLICY));

            assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(original);
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.STALE, PdpStatus::state).returns("accepted",
                    PdpStatus::configurationId);
        }
    }

    @Test
    @DisplayName("An incompatible requirement is rejected before extension processing")
    void whenRequirementIsIncompatibleThenExtensionsAreNotPreparedOrCommitted() {
        try (val source = new PdpVoterSource(new StaticPluginsSource(plugins()), Clock.systemUTC(), extensions)) {
            source.loadConfiguration(configuration("rejected", ">" + version(), PERMIT_POLICY));

            verifyNoInteractions(extensions);
        }
    }

    @Test
    @DisplayName("A rejected configuration cannot become active when plugins arrive later")
    void whenRejectedBeforePluginsThenLaterPluginsCannotActivateConfiguration() {
        val plugins = new MutablePluginsSource();
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            source.loadConfiguration(configuration("rejected", ">" + version(), PERMIT_POLICY));
            plugins.publish(plugins());

            assertThat(source.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.ERROR, PdpStatus::state);
        }
    }

    @Test
    @DisplayName("Rejecting an update leaves a compatible pending configuration available for plugin arrival")
    void whenPendingConfigurationIsFollowedByRejectedUpdateThenPendingConfigurationSurvives() {
        val plugins = new MutablePluginsSource();
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            source.loadConfiguration(configuration("accepted", null, PERMIT_POLICY));
            source.loadConfiguration(configuration("rejected", ">" + version(), DENY_POLICY));
            plugins.publish(plugins());

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state).returns("accepted",
                    PdpStatus::configurationId);
        }
    }

    @Test
    @DisplayName("Plugin replacement recompiles the accepted configuration rather than the rejected update")
    void whenPluginsChangeAfterRejectedUpdateThenAcceptedConfigurationIsRecompiled() {
        val plugins = new MutablePluginsSource(plugins());
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            source.loadConfiguration(configuration("accepted", null, PERMIT_POLICY));
            source.loadConfiguration(configuration("rejected", ">" + version(), DENY_POLICY));
            plugins.publish(plugins());

            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state).returns("accepted",
                    PdpStatus::configurationId);
        }
    }

    @Test
    @DisplayName("An existing decision subscription survives rejection and adopts a later compatible update")
    void whenUpdateIsRejectedThenExistingSubscriptionContinuesAndCanChangeLater() throws InterruptedException {
        try (val source = source()) {
            source.loadConfiguration(configuration("accepted", null, PERMIT_POLICY));
            val pdp          = new BlockingPolicyDecisionPoint(source, attributeBroker,
                    new ThreadLocalRandomIdFactory());
            val subscription = AuthorizationSubscription.of("alice", "read", "archive");
            try (val decisions = pdp.decide(subscription, PDP_ID)) {
                assertThat(decisions.awaitNext().decision()).isEqualTo(Decision.PERMIT);

                source.loadConfiguration(configuration("rejected", ">" + version(), DENY_POLICY));

                assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.STALE, PdpStatus::state);
                assertThat(pdp.decideOnce(subscription, PDP_ID).decision()).isEqualTo(Decision.PERMIT);

                source.loadConfiguration(configuration("replacement", version(), DENY_POLICY));

                assertThat(decisions.awaitNext().decision()).isEqualTo(Decision.DENY);
            }
        }
    }

    @Test
    @DisplayName("Rejected updates leave listeners registered for later compatible configurations")
    void whenUpdateIsRejectedThenListenersReceiveOnlyAcceptedConfigurations() {
        try (val source = source()) {
            source.loadConfiguration(configuration("accepted", null, PERMIT_POLICY));
            val updates = new ArrayList<PdpUpdateEvent>();
            source.subscribeToUpdates(PDP_ID, updates::add);

            source.loadConfiguration(configuration("rejected", ">" + version(), DENY_POLICY));
            source.loadConfiguration(configuration("replacement", version(), DENY_POLICY));

            assertThat(updates).hasSize(2)
                    .allSatisfy(event -> assertThat(event).isInstanceOf(PdpUpdateEvent.Voter.class));
        }
    }

    @MethodSource("versionRequirements")
    @ParameterizedTest(name = "Engine {0} with requirement {1} is compatible: {2}")
    @DisplayName("Version matching preserves prerelease semantics and rejects unknown builds when required")
    void whenCheckingEngineVersionThenDeclaredRangeDeterminesCompatibility(String actual, String requirement,
            boolean compatible) {
        assertThat(engineVersionError(actual, requirement) == null).isEqualTo(compatible);
    }

    @ValueSource(strings = { "unknown", "", "${project.version}", "4.2.01" })
    @DisplayName("An unavailable or malformed engine version reports the metadata problem")
    @ParameterizedTest(name = "Invalid engine build version {0} explains how to repair the metadata")
    void whenEngineVersionIsInvalidThenDiagnosticIdentifiesBuildMetadata(String actual) {
        assertThat(engineVersionError(actual, ">=4.2.0")).contains(actual, ">=4.2.0", "requiredEngineVersion",
                "not a valid semantic version", "build metadata");
    }

    @Test
    @DisplayName("An incompatible valid version reports the running version and the required range")
    void whenEngineVersionIsOutsideRangeThenDiagnosticExplainsCompatibilityFailure() {
        assertThat(engineVersionError("4.1.0", ">=4.2.0"))
                .contains("4.1.0", ">=4.2.0", "requiredEngineVersion", "does not satisfy")
                .doesNotContain("not a valid semantic version");
    }

    static Stream<Arguments> versionRequirements() {
        return Stream.of(arguments("4.2.0", null, true), arguments("unknown", null, true), arguments("", null, true),
                arguments("4.2.0", "^4.0.0", true), arguments("4.2.0", ">=4.2.0 <5.0.0", true),
                arguments("4.2.0", ">4.2.0", false), arguments("4.2.0", "<4.2.0", false),
                arguments("4.2.0", ">=4.3.0 || ^5.0.0", false), arguments("4.2.0", "^3.0.0 || ^4.0.0", true),
                arguments("4.2.0-SNAPSHOT", ">=4.2.0", false), arguments("4.2.0-SNAPSHOT", ">=4.1.0", false),
                arguments("4.2.0-SNAPSHOT", ">=4.2.0-0 <5.0.0", true),
                arguments("4.2.0-SNAPSHOT", "4.2.0-SNAPSHOT", true), arguments("4.2.0-RC1", ">=4.2.0-0", true),
                arguments("4.2.0", "*", true), arguments("4.2.0-SNAPSHOT", "*", false),
                arguments("4.2.0+build.42", "4.2.0", true), arguments("unknown", "*", false), arguments("", "*", false),
                arguments("${project.version}", "*", false), arguments("4.2.01", "*", false),
                arguments("4.2", "*", false), arguments("invalid", ">=4.2.0", false));
    }

    private static PdpVoterSource source() {
        return new PdpVoterSource(new StaticPluginsSource(plugins()), Clock.systemUTC());
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(SaplTesting.FUNCTION_BROKER);
    }

    private static PDPConfiguration configuration(String id, String requirement, String policy) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT), Map.of(), Map.of(), Set.of(),
                UnknownProvenance.INSTANCE, requirement);
    }
}
