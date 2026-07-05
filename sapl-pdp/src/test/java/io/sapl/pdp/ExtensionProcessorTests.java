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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;

@DisplayName("ExtensionProcessor wiring")
class ExtensionProcessorTests {

    @TempDir
    Path policyDir;

    @Test
    @DisplayName("a processor receives the unsealed extension config and secrets on load")
    void whenDirectoryHasExtensionsThenProcessorNotifiedWithCleartext() throws Exception {
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

        val captured = new CopyOnWriteArrayList<Loaded>();
        try (val components = PolicyDecisionPointBuilder.withDefaults().acceptUnencryptedSecrets()
                .withExtensionProcessor(new Recorder(captured)).withDirectorySource(policyDir).build()) {

            assertThat(captured).singleElement().satisfies(loaded -> {
                assertThat(loaded.pdpId()).isEqualTo("default");
                assertThat(endpointOf(loaded)).isEqualTo("https://mcp.internal/billing");
                assertThat(secretOf(loaded)).isEqualTo("PLAINTEXT-KEY");
            });
        }
    }

    private static String endpointOf(Loaded loaded) {
        return ((TextValue) ((ObjectValue) loaded.extensions().get("upstreams")).get("endpoint")).value();
    }

    private static String secretOf(Loaded loaded) {
        return ((TextValue) ((ObjectValue) loaded.extensionSecrets().get("upstreams")).get("apiKey")).value();
    }

    private record Loaded(String pdpId, Map<String, Value> extensions, Map<String, Value> extensionSecrets) {}

    private record Recorder(CopyOnWriteArrayList<Loaded> captured) implements ExtensionProcessor {

        @Override
        public void onLoad(String pdpId, Map<String, Value> extensions, Map<String, Value> extensionSecrets) {
            captured.add(new Loaded(pdpId, extensions, extensionSecrets));
        }

        @Override
        public void onRemove(String pdpId) {
            // Not exercised here.
        }
    }
}
