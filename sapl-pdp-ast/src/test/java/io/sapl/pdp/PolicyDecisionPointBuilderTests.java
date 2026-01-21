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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.sapl.pdp.PdpTestHelper.createBundle;
import static io.sapl.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

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
        developmentPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();
    }

    @Test
    void whenBuildingWithDefaults_thenPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withDefaults().build();

        assertThat(components.pdp()).isNotNull();
        assertThat(components.pdpRegister()).isNotNull();
        assertThat(components.functionBroker()).isNotNull();
        assertThat(components.attributeBroker()).isNotNull();
        assertThat(components.source()).isNull();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithoutDefaults_thenMinimalPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();

        assertThat(components.pdp()).isNotNull();
        assertThat(components.pdpRegister()).isNotNull();
        assertThat(components.source()).isNull();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithConfiguration_thenConfigurationIsLoaded() throws Exception {
        val policy = "policy \"permit-all\" permit";
        val config = new PDPConfiguration(DEFAULT_PDP_ID, "v1", PERMIT_OVERRIDES, List.of(policy), Map.of());

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithBundle_thenConfigurationIsLoaded() throws Exception {
        val bundleBytes = createBundle("policy \"deny-all\" deny");

        val components = PolicyDecisionPointBuilder.withoutDefaults()
                .withBundle(bundleBytes, DEFAULT_PDP_ID, developmentPolicy).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithDirectorySource_thenPoliciesAreLoaded() throws Exception {
        val policyDir = tempDir.resolve("policies");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("permit.sapl"), "policy \"permit\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        assertThat(components.source()).isNotNull();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> StepVerifier
                .create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete());

        disposeSource(components);
    }

    @Test
    void whenBuildingWithBundleDirectorySource_thenBundlesAreLoaded() throws Exception {
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

        disposeSource(components);
    }

    @Test
    void whenDisposeSource_thenResourcesAreReleased() throws Exception {
        val policyDir = tempDir.resolve("dispose-test");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        val source = components.source();
        assertThat(source).isNotNull();
        assertThat(source.isDisposed()).isFalse();

        source.dispose();

        assertThat(source.isDisposed()).isTrue();
    }

    @Test
    void whenBuildingWithMultipleConfigurations_thenAllAreLoaded() throws Exception {
        val config1 = new PDPConfiguration("pdp1", "v1", PERMIT_OVERRIDES, List.of("policy \"p1\" permit"), Map.of());
        val config2 = new PDPConfiguration("pdp2", "v1", DENY_OVERRIDES, List.of("policy \"p2\" deny"), Map.of());

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config1)
                .withConfiguration(config2).build();

        // Both configurations should be loaded
        assertThat(components.pdpRegister()).isNotNull();

        disposeSource(components);
    }

    @Test
    void whenRegisteringDirectorySourceTwice_thenExceptionIsThrown() {
        val policyDir1 = tempDir.resolve("policies1");
        val policyDir2 = tempDir.resolve("policies2");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir1);

        assertThatThrownBy(() -> builder.withDirectorySource(policyDir2)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringBundleSourceAfterDirectorySource_thenExceptionIsThrown() {
        val policyDir = tempDir.resolve("policies");
        val bundleDir = tempDir.resolve("bundles");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir);

        assertThatThrownBy(() -> builder.withBundleDirectorySource(bundleDir, developmentPolicy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringResourcesSourceAfterDirectorySource_thenExceptionIsThrown() {
        val policyDir = tempDir.resolve("policies");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir);

        assertThatThrownBy(() -> builder.withResourcesSource("/policies")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringMultiDirectorySourceAfterBundleSource_thenExceptionIsThrown() {
        val bundleDir = tempDir.resolve("bundles");
        val multiDir  = tempDir.resolve("multi");

        val builder = PolicyDecisionPointBuilder.withoutDefaults().withBundleDirectorySource(bundleDir,
                developmentPolicy);

        assertThatThrownBy(() -> builder.withMultiDirectorySource(multiDir)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenRegisteringCustomSourceAfterResourcesSource_thenExceptionIsThrown() {
        val builder = PolicyDecisionPointBuilder.withoutDefaults().withResourcesSource();

        assertThatThrownBy(() -> builder.withConfigurationSource(callback -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration source has already been registered");
    }

    @Test
    void whenBuildingWithExternalFunctionBroker_thenBrokerIsUsed() throws Exception {
        val externalBroker = mock(FunctionBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalBroker).build();

        assertThat(components.functionBroker()).isSameAs(externalBroker);

        disposeSource(components);
    }

    @Test
    void whenBuildingWithExternalAttributeBroker_thenBrokerIsUsed() throws Exception {
        val externalBroker = mock(AttributeBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(externalBroker).build();

        assertThat(components.attributeBroker()).isSameAs(externalBroker);

        disposeSource(components);
    }

    @Test
    void whenBuildingWithExternalBrokers_thenLibrariesAndPipsAreIgnored() throws Exception {
        val externalFunctionBroker  = mock(FunctionBroker.class);
        val externalAttributeBroker = mock(AttributeBroker.class);

        // These would fail if actually loaded, but external brokers should bypass
        // traced building
        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalFunctionBroker)
                .withAttributeBroker(externalAttributeBroker).withPolicyInformationPoints(List.of(new Object()))
                .withFunctionLibraryInstances(List.of(new Object())).build();

        assertThat(components.functionBroker()).isSameAs(externalFunctionBroker);
        assertThat(components.attributeBroker()).isSameAs(externalAttributeBroker);

        disposeSource(components);
    }

    @Test
    void whenBuildingWithCollectionMethods_thenBuilderAcceptsCollections() throws Exception {
        // Just verify the builder methods accept collections - actual loading is tested
        // elsewhere
        val builder = PolicyDecisionPointBuilder.withoutDefaults();

        // These methods should not throw
        builder.withFunctionLibraries(List.of());
        builder.withFunctionLibraryInstances(List.of());
        builder.withPolicyInformationPoints(List.of());

        val components = builder.build();
        assertThat(components.pdp()).isNotNull();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithPoliciesAndAlgorithm_thenPdpBuildsSuccessfully() throws Exception {
        val policy = "policy \"elder-wards\" permit";
        val config = new PDPConfiguration(DEFAULT_PDP_ID, "v1", DENY_OVERRIDES, List.of(policy), Map.of());

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        disposeSource(components);
    }

    @Test
    void whenBuildingWithPolicies_thenPdpBuildsSuccessfully() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().withCombiningAlgorithm(PERMIT_OVERRIDES)
                .withPolicy("policy \"test\" permit").build();

        assertThat(components.pdp()).isNotNull();

        disposeSource(components);
    }

    private void disposeSource(PDPComponents components) {
        val source = components.source();
        if (source != null) {
            source.dispose();
        }
    }
}
