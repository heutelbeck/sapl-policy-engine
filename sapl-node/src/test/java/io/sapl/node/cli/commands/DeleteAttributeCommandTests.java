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
package io.sapl.node.cli.commands;

import lombok.val;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("delete attribute command")
class DeleteAttributeCommandTests {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelpThenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new DeleteAttributeCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("delete", "--url", "--name");
        }
    }

    @Nested
    @DisplayName("when the attribute api responds")
    class WhenAttributeApiResponds {
        private static final String NAME   = "attribute.test";
        private static final String ENTITY = "alice";

        private final StringWriter out = new StringWriter();
        private final CommandLine  cmd = new CommandLine(new DeleteAttributeCommand());
        private AttributeApiStub   apiServer;

        @BeforeEach
        void setUp() throws IOException {
            apiServer = new AttributeApiStub();
            cmd.setOut(new PrintWriter(out));
        }

        @AfterEach
        void tearDown() {
            apiServer.close();
        }

        @Test
        @DisplayName("then a request with entity, arguments and the attribute name is 204 no content")
        void thenRequestForAttributeIsPublished() {
            apiServer.respondWith(204, "");
            val exitCode = cmd.execute("--url", apiServer.url(), "--entity", ENTITY, "--name", NAME, "--arguments",
                    "1,x");

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().method()).isEqualTo("DELETE");
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes/alice/attribute.test?arg=1&arg=x");
        }

        @Test
        @DisplayName("then a global attribute is requested without entity path")
        void thenRequestForGlobalAttributeIsPublished() {
            apiServer.respondWith(204, "");
            val exitCode = cmd.execute("--url", apiServer.url(), "--name", NAME);

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().method()).isEqualTo("DELETE");
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes/attribute.test");
        }
    }
}
