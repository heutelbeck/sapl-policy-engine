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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import lombok.val;

/**
 * The configurations that ship with the node are secure by default: none
 * allows plaintext or anonymous access, so an operator opts in explicitly,
 * and none switches TLS off, so that naming an SSL bundle is enough to serve
 * HTTPS.
 */
@DisplayName("Shipped configuration")
class ShippedConfigurationTests {

    static Stream<String> shippedFiles() {
        return Stream.of("src/main/resources/application.yml", "src/main/resources/application-docker.yml",
                "packaging/default-config.yml");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedFiles")
    @DisplayName("sets no opt-in and does not switch TLS off")
    void whenShippedThenNoOptInAndTlsNotSwitchedOff(String file) {
        val yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(Path.of(file)));
        val properties = yaml.getObject();

        assertThat(properties).isNotNull().doesNotContainKeys("io.sapl.node.allow-no-auth", "io.sapl.node.allow-no-tls",
                "server.ssl.enabled");
    }

}
