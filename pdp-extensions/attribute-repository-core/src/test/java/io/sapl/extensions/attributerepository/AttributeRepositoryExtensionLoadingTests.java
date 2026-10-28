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
package io.sapl.extensions.attributerepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.PdpState;
import lombok.val;

class AttributeRepositoryExtensionLoadingTests {
    // The required fields of the real backends are tested in the provider tests of the
    // attribute-repository-* modules. Here the test backend only checks the loading of the extension.
    @Test
    @DisplayName("a valid config split into cleartext and secrets is read and triggers buildOrRoute method")
    void whenConfigIsValidThenRepositoryBuildIsTriggered(@TempDir Path policyDir) throws Exception {
        assertBuildIsTriggered(policyDir, """
                { "type": "test", "host": "localhost", "port": 5432 }""", """
                { "password": "secret" }""");
    }

    @Test
    @DisplayName("an invalid config is rejected")
    void whenConfigIsMissingPasswordThenConfigurationIsRejected(@TempDir Path policyDir) throws Exception {
        assertConfigurationIsRejected(policyDir, """
                { "type": "test", "host": "localhost", "port": 5432 }""", """
                {}""");
    }

    @Test
    @DisplayName("a config for a backend whose module is not on the classpath is rejected")
    void whenBackendModuleIsMissingThenConfigurationIsRejected(@TempDir Path policyDir) throws Exception {
        assertConfigurationIsRejected(policyDir, """
                { "type": "redis", "host": "localhost", "port": 6379 }""", """
                {}""");
    }

    private void assertBuildIsTriggered(Path policyDir, String config, String secrets) throws Exception {
        writePolicyFiles(policyDir, config, secrets);

        val repository = spy(new RoutingAttributeRepository());
        val processor  = new AttributeRepositoryExtensionsProcessor(repository);

        try (val components = PolicyDecisionPointBuilder.withDefaults().acceptUnencryptedSecrets()
                .withExtensionsProcessor(processor).withDirectorySource(policyDir).build()) {

            verify(repository).canPrepare(any());
            verify(repository).buildOrRoute(eq("default"), any());
            assertThat(components.pdpVoterSource().getPdpStatus("default"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.LOADED));
        }
    }

    private void assertConfigurationIsRejected(Path policyDir, String config, String secrets) throws Exception {
        writePolicyFiles(policyDir, config, secrets);

        val processor = new AttributeRepositoryExtensionsProcessor(new RoutingAttributeRepository());

        try (val components = PolicyDecisionPointBuilder.withDefaults().acceptUnencryptedSecrets()
                .withExtensionsProcessor(processor).withDirectorySource(policyDir).build()) {

            assertThat(components.pdpVoterSource().getPdpStatus("default"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.ERROR));
        }
    }

    private void writePolicyFiles(Path policyDir, String config, String secrets) throws Exception {
        Files.writeString(policyDir.resolve("pdp.json"), "{}");
        Files.writeString(policyDir.resolve("policy.sapl"), "policy \"p\" permit true;");
        Files.writeString(policyDir.resolve("ext-attributerepository.json"), config);
        Files.writeString(policyDir.resolve("ext-attributerepository-secrets.json"), secrets);
    }
}
