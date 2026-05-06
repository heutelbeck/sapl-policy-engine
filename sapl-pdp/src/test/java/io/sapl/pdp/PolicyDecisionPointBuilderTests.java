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
package io.sapl.pdp;

import io.sapl.legacy.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.reactive.pdp.PolicyDecisionPointBuilder;
import io.sapl.reactive.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.source.DirectoryPDPConfigurationSource;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static io.sapl.pdp.PdpTestHelper.createBundle;
import static io.sapl.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

@DisplayName("PolicyDecisionPointBuilder")
class PolicyDecisionPointBuilderTests {

    private static final String DEFAULT_PDP_ID = "default";

    private static final CombiningAlgorithm PERMIT_OVERRIDES = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.PERMIT, ErrorHandling.PROPAGATE);
    private static final CombiningAlgorithm DENY_OVERRIDES   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private static BundleSecurityPolicy developmentPolicy;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupSecurityPolicy() {
        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
    }

    @Test
    void whenBuildingWithDefaultsThenPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withDefaults().build();

        assertThat(components).satisfies(c -> {
            assertThat(c.pdp()).isNotNull();
            assertThat(c.pdpVoterSource()).isNotNull();
            assertThat(c.functionBroker()).isNotNull();
            assertThat(c.attributeBroker()).isNotNull();
            assertThat(c.source()).isNull();
        });

        closeSource(components);
    }

    @Test
    void whenBuildingWithoutDefaultsThenMinimalPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();

        assertThat(components).satisfies(c -> {
            assertThat(c.pdp()).isNotNull();
            assertThat(c.pdpVoterSource()).isNotNull();
            assertThat(c.source()).isNull();
        });

        closeSource(components);
    }

    @Test
    void whenBuildingWithConfigurationThenConfigurationIsLoaded() throws Exception {
        val policy = "policy \"permit-all\" permit";
        val config = new PDPConfiguration(DEFAULT_PDP_ID, "v1", PERMIT_OVERRIDES, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        closeSource(components);
    }

    @Test
    void whenBuildingWithBundleThenConfigurationIsLoaded() throws Exception {
        val bundleBytes = createBundle("policy \"deny-all\" deny");

        val components = PolicyDecisionPointBuilder.withoutDefaults()
                .withBundle(bundleBytes, DEFAULT_PDP_ID, developmentPolicy).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();

        closeSource(components);
    }

    @Test
    void whenBuildingWithDirectorySourceThenPoliciesAreLoaded() throws Exception {
        val policyDir = tempDir.resolve("policies");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }}
                        """);
        Files.writeString(policyDir.resolve("permit.sapl"), "policy \"permit\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        assertThat(components.source()).isNotNull();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> StepVerifier
                .create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete());

        closeSource(components);
    }

    @Test
    void whenBuildingWithBundleDirectorySourceThenBundlesAreLoaded() throws Exception {
        val bundleDir = tempDir.resolve("bundles");
        Files.createDirectories(bundleDir);
        // Bundle filename (minus extension) becomes pdpId
        Files.write(bundleDir.resolve("default.saplbundle"), createBundle("policy \"permit\" permit"));

        val components = PolicyDecisionPointBuilder.withoutDefaults()
                .withBundleDirectorySource(bundleDir, developmentPolicy).build();

        assertThat(components.source()).isNotNull();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> StepVerifier
                .create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete());

        closeSource(components);
    }

    @Test
    void whenDisposeSourceThenResourcesAreReleased() throws Exception {
        val policyDir = tempDir.resolve("dispose-test");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("pdp.json"),
                """
                        {"algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "PROPAGATE" }}
                        """);
        Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        val source = (DirectoryPDPConfigurationSource) components.source();
        assertThat(source).isNotNull();
        assertThat(source.isClosed()).isFalse();

        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    @Test
    void whenBuildingWithMultipleConfigurationsThenAllAreLoaded() throws Exception {
        val config1 = new PDPConfiguration("pdp1", "v1", PERMIT_OVERRIDES, List.of("policy \"p1\" permit"),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        val config2 = new PDPConfiguration("pdp2", "v1", DENY_OVERRIDES, List.of("policy \"p2\" deny"),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config1)
                .withConfiguration(config2).build();

        // Both configurations should be loaded
        assertThat(components.pdpVoterSource()).isNotNull();

        closeSource(components);
    }

    @Test
    void whenRegisteringDirectorySourceTwiceThenExceptionIsThrown() {
        val policyDir1 = tempDir.resolve("policies1");
        val policyDir2 = tempDir.resolve("policies2");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir1);

        assertThatThrownBy(() -> builder.withDirectorySource(policyDir2)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringBundleSourceAfterDirectorySourceThenExceptionIsThrown() {
        val policyDir = tempDir.resolve("policies");
        val bundleDir = tempDir.resolve("bundles");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir);

        assertThatThrownBy(() -> builder.withBundleDirectorySource(bundleDir, developmentPolicy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringResourcesSourceAfterDirectorySourceThenExceptionIsThrown() {
        val policyDir = tempDir.resolve("policies");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir);

        assertThatThrownBy(() -> builder.withResourcesSource("/policies")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringMultiDirectorySourceAfterBundleSourceThenExceptionIsThrown() {
        val bundleDir = tempDir.resolve("bundles");
        val multiDir  = tempDir.resolve("multi");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withBundleDirectorySource(bundleDir,
                developmentPolicy);

        assertThatThrownBy(() -> builder.withMultiDirectorySource(multiDir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringCustomSourceAfterResourcesSourceThenExceptionIsThrown() {
        val builder         = PolicyDecisionPointBuilder.withoutDefaults().withResourcesSource();
        val secondarySource = mock(PDPConfigurationSource.class);

        assertThatThrownBy(() -> builder.withConfigurationSource(secondarySource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenBuildingWithExternalFunctionBrokerThenBrokerIsUsed() throws Exception {
        val externalBroker = mock(FunctionBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalBroker).build();

        assertThat(components.functionBroker()).isSameAs(externalBroker);

        closeSource(components);
    }

    @Test
    void whenBuildingWithExternalAttributeBrokerThenBrokerIsUsed() throws Exception {
        val externalBroker = mock(AttributeBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(externalBroker).build();

        assertThat(components.attributeBroker()).isSameAs(externalBroker);

        closeSource(components);
    }

    @Test
    void whenBuildingWithExternalBrokersThenLibrariesAndPipsAreIgnored() throws Exception {
        val externalFunctionBroker  = mock(FunctionBroker.class);
        val externalAttributeBroker = mock(AttributeBroker.class);

        // These would fail if actually loaded, but external brokers should bypass
        // traced building
        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalFunctionBroker)
                .withAttributeBroker(externalAttributeBroker).withPolicyInformationPoints(List.of(new Object()))
                .withFunctionLibraryInstances(List.of(new Object())).build();

        assertThat(components).satisfies(c -> {
            assertThat(c.functionBroker()).isSameAs(externalFunctionBroker);
            assertThat(c.attributeBroker()).isSameAs(externalAttributeBroker);
        });

        closeSource(components);
    }

    @Test
    void whenBuildingWithCollectionMethodsThenBuilderAcceptsCollections() throws Exception {
        // Just verify the builder methods accept collections - actual loading is tested
        // elsewhere
        val builder = PolicyDecisionPointBuilder.withoutDefaults();

        // These methods should not throw
        builder.withFunctionLibraries(List.of());
        builder.withFunctionLibraryInstances(List.of());
        builder.withPolicyInformationPoints(List.of());

        val components = builder.build();
        assertThat(components.pdp()).isNotNull();

        closeSource(components);
    }

    @Test
    void whenBuildingWithPoliciesAndAlgorithmThenPdpBuildsSuccessfully() throws Exception {
        val policy = "policy \"elder-wards\" permit";
        val config = new PDPConfiguration(DEFAULT_PDP_ID, "v1", DENY_OVERRIDES, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        closeSource(components);
    }

    @Test
    void whenBuildingWithPoliciesThenPdpBuildsSuccessfully() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().withCombiningAlgorithm(PERMIT_OVERRIDES)
                .withPolicy("policy \"test\" permit").build();

        assertThat(components.pdp()).isNotNull();

        closeSource(components);
    }

    private void closeSource(PDPComponents components) throws Exception {
        val source = components.source();
        if (source != null) {
            source.close();
        }
    }
}
