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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.ExtensionsProcessor;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PdpState;
import lombok.val;

@DisplayName("ExtensionsProcessor wiring")
class ExtensionsProcessorTests {

    @TempDir
    Path policyDir;

    @Test
    @DisplayName("the processor is committed the unsealed extension config and secrets when a configuration goes live")
    void whenDirectoryHasExtensionsThenProcessorCommittedWithCleartext() throws Exception {
        Files.writeString(policyDir.resolve("pdp.json"), """
                { "configurationId": "cfg-1" }""");
        Files.writeString(policyDir.resolve("policy.sapl"), """
                policy "p" permit true;
                """);
        Files.writeString(policyDir.resolve("ext-upstreams.json"), """
                { "endpoint": "https://mcp.internal/billing" }""");
        Files.writeString(policyDir.resolve("secrets.json"), """
                { "token": "PLAINTEXT" }""");
        Files.writeString(policyDir.resolve("ext-upstreams-secrets.json"), """
                { "apiKey": "PLAINTEXT-KEY" }""");

        val committed = new CopyOnWriteArrayList<PDPConfiguration>();
        try (val components = PolicyDecisionPointBuilder.withDefaults().acceptUnencryptedSecrets()
                .withExtensionsProcessor(new Recorder(committed)).withDirectorySource(policyDir).build()) {

            assertThat(committed).singleElement().satisfies(configuration -> {
                assertThat(configuration.pdpId()).isEqualTo("default");
                assertThat(endpointOf(configuration)).isEqualTo("https://mcp.internal/billing");
                assertThat(secretOf(configuration)).isEqualTo("PLAINTEXT-KEY");
            });
        }
    }

    @Test
    @DisplayName("the default processor rejects a configuration that declares a critical capability it cannot deploy")
    void whenCriticalCapabilityAndDefaultProcessorThenConfigurationRejected() throws Exception {
        Files.writeString(policyDir.resolve("pdp.json"), """
                { "configurationId": "cfg-1" }""");
        Files.writeString(policyDir.resolve("policy.sapl"), """
                policy "p" permit true;
                """);
        Files.writeString(policyDir.resolve("ext-upstreams.json"), """
                { "endpoint": "https://mcp.internal/billing" }""");
        Files.writeString(policyDir.resolve("critical-extensions.json"), """
                ["upstreams"]""");

        try (val components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(policyDir).build()) {
            assertThat(components.pdpVoterSource().getPdpStatus("default"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.ERROR));
            assertThat(components.pdpVoterSource().getCurrentConfiguration("default")).isEmpty();
        }
    }

    @Test
    @DisplayName("an initial configuration declaring a critical capability the default processor cannot deploy fails the build")
    void whenInitialConfigurationDeclaresUncoveredCriticalThenBuildFails() {
        val configuration = new PDPConfiguration("default", "cfg-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of(), Map.of(), Set.of("upstreams"));
        val builder       = PolicyDecisionPointBuilder.withDefaults().withConfiguration(configuration);

        assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class);
    }

    private static String endpointOf(PDPConfiguration configuration) {
        val upstreams = (ObjectValue) requireNonNull(configuration.extensions().get("upstreams"));
        return ((TextValue) requireNonNull(upstreams.get("endpoint"))).value();
    }

    private static String secretOf(PDPConfiguration configuration) {
        val upstreams = (ObjectValue) requireNonNull(configuration.extensionSecrets().get("upstreams"));
        return ((TextValue) requireNonNull(upstreams.get("apiKey"))).value();
    }

    private record Recorder(CopyOnWriteArrayList<PDPConfiguration> committed) implements ExtensionsProcessor {

        @Override
        public boolean prepare(String pdpId, PDPConfiguration configuration) {
            return true;
        }

        @Override
        public void commit(String pdpId, PDPConfiguration configuration) {
            committed.add(configuration);
        }

        @Override
        public void remove(String pdpId) {
            // Not exercised here.
        }
    }
}
