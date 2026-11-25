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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.pdp.configuration.bundle.BundleManifest;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;

class SecretsFileFieldsTests {
    private static final String CONTROL = "{\"one\":{\"label\":{}},\"two\":{\"label\":{}},\"array\":[{\"label\":{}},{\"label\":{}}]}";

    static Stream<Arguments> duplicateFiles() {
        return Stream.of("secrets.json", "secrets.sealed.json", "ext-desk-secrets.json", "ext-desk-secrets.sealed.json")
                .flatMap(file -> Stream.of(arguments(file, "{\"department\":{},\"department\":{}}", "department"),
                        arguments(file, "{\"office\":{\"label\":{},\"label\":{}}}", "label"),
                        arguments(file, "{\"department\":{},\"depart\\u006dent\":{}}", "department")));
    }

    @ParameterizedTest
    @MethodSource("duplicateFiles")
    void whenResourceSecretsRepeatFieldsThenTheFileAndFieldAreReported(String file, String document, String field) {
        val files = Map.of(file, document);
        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromContent("{}", Map.of(), files, "tenant", "fields"))
                .isInstanceOf(PDPConfigurationException.class)
                .hasMessage("Secrets file '%s' contains duplicate field '%s'.", file, field);
    }

    @ParameterizedTest
    @MethodSource("duplicateFiles")
    void whenDirectorySecretsRepeatFieldsThenTheFileAndFieldAreReported(String file, String document, String field,
            @TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("pdp.json"), "{}");
        Files.writeString(directory.resolve(file), document);
        assertThatThrownBy(() -> PDPConfigurationLoader.loadFromDirectory(directory, "tenant"))
                .isInstanceOf(PDPConfigurationException.class)
                .hasMessage("Secrets file '%s' contains duplicate field '%s'.", file, field);
    }

    @ParameterizedTest
    @MethodSource("duplicateFiles")
    void whenBundleSecretsRepeatFieldsThenTheFileAndFieldAreReported(String file, String document, String field)
            throws IOException {
        val bytes  = bundle(file, document);
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().withSealingKeyIds(Set.of("fixture"))
                .build();
        assertThatThrownBy(() -> BundleParser.parse(bytes, "tenant", policy))
                .isInstanceOf(PDPConfigurationException.class)
                .hasMessage("Secrets file '%s' contains duplicate field '%s'.", file, field);
    }

    @Test
    void whenFieldNamesBelongToDifferentObjectsThenResourcesAndBundlesAcceptThem() throws IOException {
        val policy = BundleSecurityPolicy.builder().disableSignatureVerification().withSealingKeyIds(Set.of("fixture"))
                .build();
        for (val file : new String[] { "secrets.json", "secrets.sealed.json", "ext-desk-secrets.json",
                "ext-desk-secrets.sealed.json" }) {
            assertThat(
                    PDPConfigurationLoader.loadFromContent("{}", Map.of(), Map.of(file, CONTROL), "tenant", "fields"))
                    .isNotNull();
            assertThat(BundleParser.parse(bundle(file, CONTROL), "tenant", policy)).isNotNull();
        }
    }

    @Test
    void whenOrdinaryExtensionsRepeatFieldsThenTheirExistingLastValueWins() {
        val configuration = PDPConfigurationLoader.loadFromContent("{}", Map.of(),
                Map.of("ext-desk.json", "{\"label\":1,\"label\":2}"), "tenant", "fields");
        assertThat(configuration.extensions().get("desk")).isEqualTo(Value.ofJson("{\"label\":2}"));
    }

    private static byte[] bundle(String file, String document) throws IOException {
        val files = new LinkedHashMap<String, String>();
        files.put("pdp.json", "{}");
        files.put("entry.sapl", "policy \"entry\" permit true;");
        files.put(file, document);
        val hashes = new TreeMap<String, String>();
        files.forEach((name, value) -> hashes.put(name, BundleManifest.computeHash(value)));
        val manifest = BundleManifest.builder().configurationId("fields")
                .attribution(ValueJsonMarshaller.toJsonNode(Value.of("fields")))
                .audience(file.endsWith(".sealed.json") ? "fixture" : null).files(hashes).buildUnsigned();
        files.put(BundleManifest.MANIFEST_FILENAME, manifest.toJson());
        val output = new ByteArrayOutputStream();
        try (val zip = new ZipOutputStream(output)) {
            for (val entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
