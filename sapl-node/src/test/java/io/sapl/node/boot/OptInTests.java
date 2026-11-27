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
package io.sapl.node.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import lombok.val;

/**
 * An operator who finds a warning of an insecure mode in the log must learn
 * where the opt-in came from, so that it can be removed: the command-line
 * option, the key in its file, or the environment variable as it is spelled.
 */
@DisplayName("OptIn")
class OptInTests {

    private static final String FILE = "config/application.yml";

    @Test
    @DisplayName("the command-line options map to their opt-ins, and other arguments to none")
    void whenArgumentIsAnOptionThenItsOptInIsFound() {
        assertThat(Stream.of("--no-tls", "--no-auth", "--server.port=1", "no-tls").map(OptIn::ofOption))
                .containsExactly(Optional.of(OptIn.NO_TLS), Optional.of(OptIn.NO_AUTH), Optional.empty(),
                        Optional.empty());
    }

    static Stream<Arguments> sources() {
        return Stream.of(
                arguments("the command-line option",
                        new MapPropertySource(OptIn.COMMAND_LINE_OPTIONS, Map.of("io.sapl.node.allow-no-tls", true)),
                        "the command-line option --no-tls"),
                arguments("a Spring command-line argument",
                        new SimpleCommandLinePropertySource("--io.sapl.node.allow-no-tls=true"),
                        "the command-line option --io.sapl.node.allow-no-tls=true"),
                arguments("an environment variable as spelled",
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("IO_SAPL_NODE_ALLOWNOTLS", "true")),
                        "the environment variable IO_SAPL_NODE_ALLOWNOTLS=true"),
                arguments("the key in its file", fileSource("io.sapl.node.allow-no-tls"),
                        "io.sapl.node.allow-no-tls=true in " + Path.of(FILE)),
                arguments("a camel case key in its file", fileSource("io.sapl.node.allowNoTls"),
                        "io.sapl.node.allow-no-tls=true in " + Path.of(FILE)),
                arguments("a source that is no file",
                        new MapPropertySource("Inlined Test Properties", Map.of("io.sapl.node.allow-no-tls", "true")),
                        "io.sapl.node.allow-no-tls=true in Inlined Test Properties"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    @DisplayName("the source of an opt-in is named as the operator gave it")
    void whenOptInIsGivenThenItsSourceIsNamed(String description, PropertySource<?> source, String expected) {
        assertThat(OptIn.NO_TLS.source(environment(source))).contains(expected);
    }

    @Test
    @DisplayName("the source with the highest precedence decides, so a command line false overrides a file true")
    void whenHigherSourceSaysFalseThenNotAllowed() {
        val environment = environment(fileSource("io.sapl.node.allow-no-tls"));
        environment.getPropertySources()
                .addFirst(new SimpleCommandLinePropertySource("--io.sapl.node.allow-no-tls=false"));

        assertThat(OptIn.NO_TLS.source(environment)).isEmpty();
    }

    @Test
    @DisplayName("a mode that is not set is not allowed")
    void whenKeyAbsentThenNotAllowed() {
        assertThat(OptIn.NO_AUTH.source(environment(new MapPropertySource("empty", Map.of())))).isEmpty();
    }

    @Test
    @DisplayName("a value that is not a boolean allows nothing, so a typo fails closed")
    void whenValueIsNotBooleanThenNotAllowed() {
        val source = new MapPropertySource("typo", Map.of("io.sapl.node.allow-no-auth", "ture"));

        assertThat(OptIn.NO_AUTH.source(environment(source))).isEmpty();
    }

    private static StandardEnvironment environment(PropertySource<?> source) {
        val environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(source);
        return environment;
    }

    private static PropertySource<?> fileSource(String key) {
        val origin = new TextResourceOrigin(new FileSystemResource(FILE), new TextResourceOrigin.Location(3, 5));
        return new OriginTrackedMapPropertySource("Config resource 'file [" + FILE + "]'",
                Map.of(key, OriginTrackedValue.of("true", origin)));
    }

}
