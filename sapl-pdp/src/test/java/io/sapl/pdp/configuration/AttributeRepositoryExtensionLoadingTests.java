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
import lombok.val;

class AttributeRepositoryExtensionLoadingTests {
    @Test
    @DisplayName("a valid Postgres config is read and triggers buildOrRoute method")
    void whenPostgresConfigIsValidThenRepositoryBuildIsTriggered(@TempDir Path policyDir) throws Exception {
        assertBuildIsTriggered(policyDir, """
                { "type": "postgres", "host": "localhost", "port": 5432, "database": "sapl" }""", """
                { "username": "sapl", "password": "secret" }""");
    }

    @Test
    @DisplayName("a valid Mongo config is read and triggers buildOrRoute method")
    void whenMongoConfigIsValidThenRepositoryBuildIsTriggered(@TempDir Path policyDir) throws Exception {
        assertBuildIsTriggered(policyDir, """
                { "type": "mongo", "host": "localhost", "port": 27017, "database": "sapl" }""", """
                {}""");
    }

    @Test
    @DisplayName("a valid Redis config is read and triggers buildOrRoute method")
    void whenRedisConfigIsValidThenRepositoryBuildIsTriggered(@TempDir Path policyDir) throws Exception {
        assertBuildIsTriggered(policyDir, """
                { "type": "redis", "host": "localhost", "port": 6379 }""", """
                {}""");
    }

    @Test
    @DisplayName("an invalid Postgres config is rejected")
    void whenPostgresConfigIsMissingPasswordThenConfigurationIsRejected(@TempDir Path policyDir) throws Exception {
        assertConfigurationIsRejected(policyDir, """
                { "type": "postgres", "host": "localhost", "port": 5432, "database": "sapl" }""", """
                { "username": "sapl" }""");
    }

    @Test
    @DisplayName("an invalid Mongo config is rejected")
    void whenMongoConfigIsMissingHostThenConfigurationIsRejected(@TempDir Path policyDir) throws Exception {
        assertConfigurationIsRejected(policyDir, """
                { "type": "mongo", "port": 27017, "database": "sapl" }""", """
                {}""");
    }

    @Test
    @DisplayName("an invalid Reids config is rejected")
    void whenRedisConfigIsMissingPortThenConfigurationIsRejected(@TempDir Path policyDir) throws Exception {
        assertConfigurationIsRejected(policyDir, """
                { "type": "redis", "host": "localhost" }""", """
                {}""");
    }

    private void assertBuildIsTriggered(Path policyDir, String config, String secrets) throws Exception {
        writePolicyFiles(policyDir, config, secrets);

        val repository = spy(new RoutingAttributeRepository());
        val processor  = new AttributeRepositoryExtensionsProcessor(repository);

        try (val components = PolicyDecisionPointBuilder.withDefaults().acceptUnencryptedSecrets()
                .withExtensionsProcessor(processor).withDirectorySource(policyDir).build()) {

            verify(repository).canPrepare(eq("default"), any());
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
        Files.writeString(policyDir.resolve("ext-attributeRepository.json"), config);
        Files.writeString(policyDir.resolve("ext-attributeRepository-secrets.json"), secrets);
    }
}
