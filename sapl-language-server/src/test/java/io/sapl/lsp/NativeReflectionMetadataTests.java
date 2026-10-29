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
package io.sapl.lsp;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ClientCapabilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import lombok.val;

@DisplayName("Native language server reflection metadata")
class NativeReflectionMetadataTests {

    private static final JsonPrimitive TRUE = new JsonPrimitive(true);

    @MethodSource("lspTypes")
    @ParameterizedTest(name = "registers LSP4J type {0}")
    @DisplayName("covers every type in the current LSP4J dependency")
    void whenClientUsesLspTypeThenReflectionMetadataSupportsGson(String className) throws Exception {
        val metadata = reflectionMetadata();
        val type     = Class.forName(className, false, ClientCapabilities.class.getClassLoader());
        assertThat(metadata.get(className)).as(className).isNotNull().satisfies(entry -> {
            if ("org.eclipse.lsp4j".equals(type.getPackageName()) && !type.isEnum()) {
                assertThat(entry.get("allDeclaredFields")).isEqualTo(TRUE);
                assertThat(entry.get("unsafeAllocated")).isEqualTo(TRUE);
            }
        });
    }

    @ValueSource(strings = { "PSMS", "SSMS" })
    @ParameterizedTest(name = "supports size-bounded cache factory {0}")
    @DisplayName("covers the function broker cache and node factories")
    void whenFunctionBrokerBuildsCacheThenFactoriesAreRegistered(String simpleName) throws Exception {
        val className = "com.github.benmanes.caffeine.cache." + simpleName;
        val metadata  = reflectionMetadata();
        assertThat(metadata.get(className)).as(className).isNotNull().satisfies(entry -> {
            assertThat(entry.get("allDeclaredConstructors")).isEqualTo(TRUE);
            assertThat(entry.get("allDeclaredFields")).isEqualTo(TRUE);
        });
    }

    private static Stream<String> lspTypes() throws Exception {
        val location = ClientCapabilities.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        try (val jar = new JarFile(Path.of(location).toFile())) {
            return jar.stream().map(JarEntry::getName)
                    .filter(name -> name.startsWith("org/eclipse/lsp4j/") && name.endsWith(".class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.')).sorted()
                    .toList().stream();
        }
    }

    private Map<String, JsonObject> reflectionMetadata() throws Exception {
        val resource = "/META-INF/native-image/io.sapl/sapl-language-server/reflect-config.json";
        try (val stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            try (val reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonArray().asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .collect(Collectors.toMap(entry -> entry.get("name").getAsString(), identity()));
            }
        }
    }
}
