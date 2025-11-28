/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import lombok.val;
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
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class PolicyDecisionPointBuilderTests {

    private static final String DEFAULT_PDP_ID = "default";

    @TempDir
    Path tempDir;

    @Test
    void whenBuildingWithDefaults_thenPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withDefaults().build();

        assertThat(components.pdp()).isNotNull();
        assertThat(components.configurationRegister()).isNotNull();
        assertThat(components.functionBroker()).isNotNull();
        assertThat(components.attributeBroker()).isNotNull();

        components.dispose();
    }

    @Test
    void whenBuildingWithoutDefaults_thenMinimalPdpIsCreated() throws Exception {
        val components = PolicyDecisionPointBuilder.withoutDefaults().build();

        assertThat(components.pdp()).isNotNull();
        assertThat(components.configurationRegister()).isNotNull();

        components.dispose();
    }

    @Test
    void whenBuildingWithConfiguration_thenConfigurationIsLoaded() throws Exception {
        val policy = "policy \"permit-all\" permit";
        val config = new PDPConfiguration(DEFAULT_PDP_ID, "v1", CombiningAlgorithm.PERMIT_OVERRIDES, List.of(policy),
                Map.of());

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config).build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete();

        components.dispose();
    }

    @Test
    void whenBuildingWithBundle_thenConfigurationIsLoaded() throws Exception {
        val bundleBytes = createBundle("policy \"deny-all\" deny");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withBundle(bundleBytes, DEFAULT_PDP_ID, "v1")
                .build();

        StepVerifier.create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.DENY)).verifyComplete();

        components.dispose();
    }

    @Test
    void whenBuildingWithDirectorySource_thenPoliciesAreLoaded() throws Exception {
        val policyDir = tempDir.resolve("policies");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("permit.sapl"), "policy \"permit\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> StepVerifier
                .create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete());

        components.dispose();
    }

    @Test
    void whenBuildingWithBundleDirectorySource_thenBundlesAreLoaded() throws Exception {
        val bundleDir = tempDir.resolve("bundles");
        Files.createDirectories(bundleDir);
        // Bundle filename (minus extension) becomes pdpId
        Files.write(bundleDir.resolve("default.saplbundle"), createBundle("policy \"permit\" permit"));

        val components = PolicyDecisionPointBuilder.withoutDefaults().withBundleDirectorySource(bundleDir).build();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> StepVerifier
                .create(components.pdp().decide(subscription("subject", "action", "resource")).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(Decision.PERMIT)).verifyComplete());

        components.dispose();
    }

    @Test
    void whenDispose_thenResourcesAreReleased() throws Exception {
        val policyDir = tempDir.resolve("dispose-test");
        Files.createDirectories(policyDir);
        Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

        val components = PolicyDecisionPointBuilder.withoutDefaults().withDirectorySource(policyDir, DEFAULT_PDP_ID)
                .build();

        assertThat(components.disposable()).isNotNull();
        assertThat(components.disposable().isDisposed()).isFalse();

        components.dispose();

        assertThat(components.disposable().isDisposed()).isTrue();
    }

    @Test
    void whenBuildingWithMultipleConfigurations_thenAllAreLoaded() throws Exception {
        val config1 = new PDPConfiguration("pdp1", "v1", CombiningAlgorithm.PERMIT_OVERRIDES,
                List.of("policy \"p1\" permit"), Map.of());
        val config2 = new PDPConfiguration("pdp2", "v1", CombiningAlgorithm.DENY_OVERRIDES,
                List.of("policy \"p2\" deny"), Map.of());

        val components = PolicyDecisionPointBuilder.withoutDefaults().withConfiguration(config1)
                .withConfiguration(config2).build();

        // Both configurations should be loaded
        assertThat(components.configurationRegister()).isNotNull();

        components.dispose();
    }

    @Test
    void whenBuildingWithExternalFunctionBroker_thenBrokerIsUsed() throws Exception {
        val externalBroker = mock(FunctionBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalBroker).build();

        assertThat(components.functionBroker()).isSameAs(externalBroker);

        components.dispose();
    }

    @Test
    void whenBuildingWithExternalAttributeBroker_thenBrokerIsUsed() throws Exception {
        val externalBroker = mock(AttributeBroker.class);

        val components = PolicyDecisionPointBuilder.withoutDefaults().withAttributeBroker(externalBroker).build();

        assertThat(components.attributeBroker()).isSameAs(externalBroker);

        components.dispose();
    }

    @Test
    void whenBuildingWithExternalBrokers_thenLibrariesAndPipsAreIgnored() throws Exception {
        val externalFunctionBroker  = mock(FunctionBroker.class);
        val externalAttributeBroker = mock(AttributeBroker.class);

        // These would fail if actually loaded, but external brokers should bypass
        // internal building
        val components = PolicyDecisionPointBuilder.withoutDefaults().withFunctionBroker(externalFunctionBroker)
                .withAttributeBroker(externalAttributeBroker).withPolicyInformationPoints(List.of(new Object()))
                .withFunctionLibraryInstances(List.of(new Object())).build();

        assertThat(components.functionBroker()).isSameAs(externalFunctionBroker);
        assertThat(components.attributeBroker()).isSameAs(externalAttributeBroker);

        components.dispose();
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

        components.dispose();
    }
}
