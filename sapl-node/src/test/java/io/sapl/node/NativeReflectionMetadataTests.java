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
package io.sapl.node;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;

import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.functions.DefaultLibraries;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Native node plugin reflection metadata")
class NativeReflectionMetadataTests {

    @MethodSource("bundledPluginTypes")
    @ParameterizedTest(name = "supports broker method discovery and invocation for {0}")
    @DisplayName("registers methods of bundled function libraries and extension PIPs")
    void whenNodeLoadsBundledPluginThenMethodsAreRegistered(Class<?> pluginType) throws IOException {
        val metadata = reflectionMetadata();
        assertThat(metadata).extractingByKey(pluginType.getName()).as(pluginType.getName()).isNotNull()
                .satisfies(entry -> assertThat(entry.path("allDeclaredMethods").asBoolean()).isTrue());
    }

    private static Stream<Class<?>> bundledPluginTypes() {
        val defaults   = DefaultLibraries.defaults().stream().map(Object::getClass);
        val extensions = new SaplExtensionsConfiguration().extensionFunctionLibraries().functionLibraries().stream()
                .map(Object::getClass);
        val pips       = stream(SaplExtensionsConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class)).map(Method::getReturnType)
                .filter(type -> type.isAnnotationPresent(PolicyInformationPoint.class));
        return Stream.concat(Stream.concat(defaults, extensions), pips).distinct();
    }

    private Map<String, JsonNode> reflectionMetadata() throws IOException {
        val resource = "/META-INF/native-image/io.sapl/sapl-node/reflect-config.json";
        try (val stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            val metadata = new HashMap<String, JsonNode>();
            for (val entry : JsonMapper.shared().readTree(stream)) {
                metadata.put(entry.path("name").asString(), entry);
            }
            return metadata;
        }
    }
}
