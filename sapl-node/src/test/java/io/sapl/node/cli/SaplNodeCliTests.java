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
package io.sapl.node.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

@DisplayName("SAPL Node CLI")
class SaplNodeCliTests {

    private StringWriter out;
    private CommandLine  cmd;

    @BeforeEach
    void setUp() {
        out = new StringWriter();
        cmd = new CommandLine(new SaplNodeCli());
        cmd.setOut(new PrintWriter(out));
    }

    @Nested
    @DisplayName("global options")
    class GlobalOptionsTests {

        @Test
        @DisplayName("--version outputs SAPL version, build info, Java, and OS details")
        void whenVersionFlag_thenSucceedsWithVersionInfo() {
            val exitCode = cmd.execute("--version");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("SAPL Node");
                assertThat(output).contains("Built:");
                assertThat(output).contains("Java:");
                assertThat(output).contains("OS:");
            });
        }

        @Test
        @DisplayName("--help lists available subcommands")
        void whenHelpFlag_thenSucceedsWithSubcommands() {
            val exitCode = cmd.execute("--help");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("bundle");
                assertThat(output).contains("generate");
            });
        }

    }

    @Nested
    @DisplayName("generate credentials")
    class GenerateCredentialsTests {

        @Test
        @DisplayName("generate basic outputs credentials with config and usage examples")
        void whenGenerateBasic_thenSucceedsWithCredentials() {
            val exitCode = cmd.execute("generate", "basic");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("Basic Auth Credentials");
                assertThat(output).contains("Username:");
                assertThat(output).contains("Password:");
                assertThat(output).contains("io.sapl.node:");
                assertThat(output).contains("allowBasicAuth: true");
                assertThat(output).contains("$argon2id$");
                assertThat(output).contains("curl -u");
                assertThat(output).contains("/api/pdp/decide-once");
            });
        }

        @Test
        @DisplayName("generate apikey outputs API key with config and usage examples")
        void whenGenerateApiKey_thenSucceedsWithApiKey() {
            val exitCode = cmd.execute("generate", "apikey");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("API Key");
                assertThat(output).contains("sapl_");
                assertThat(output).contains("io.sapl.node:");
                assertThat(output).contains("allowApiKeyAuth: true");
                assertThat(output).contains("users:");
                assertThat(output).contains("$argon2id$");
                assertThat(output).contains("Authorization: Bearer");
                assertThat(output).contains("/api/pdp/decide-once");
            });
        }

    }

}
