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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;

@DisplayName("check integration")
class CheckIntegrationTests extends AbstractCliIntegrationTests {

    @Nested
    @DisplayName("directory mode with named flags")
    class DirectoryWithNamedFlagsTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("permit-all policy exits with code 0")
        void whenPermitPolicy_thenExitZero() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

            val exitCode = SaplNodeApplication.run(new String[] { "check", "--dir", policyDir.toString(), "-s",
                    "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isZero();
        }

        @Test
        @DisplayName("non-matching policy exits with non-zero code")
        void whenNoPolicyMatches_thenNonZeroExit() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"restricted\" permit subject == \"admin\";");

            val exitCode = SaplNodeApplication.run(new String[] { "check", "--dir", policyDir.toString(), "-s",
                    "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isNotZero();
        }

        @Test
        @DisplayName("policy with obligation exits with code 4")
        void whenPolicyWithObligation_thenExitCode4() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"),
                    "policy \"with-obligation\" permit obligation \"log-access\"");

            val exitCode = SaplNodeApplication.run(new String[] { "check", "--dir", policyDir.toString(), "-s",
                    "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isEqualTo(4);
        }

    }

    @Nested
    @DisplayName("directory mode with file input")
    class DirectoryWithFileInputTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("subscription from JSON file produces correct exit code")
        void whenSubscriptionFile_thenCorrectExitCode() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");
            val subscriptionFile = policyDir.resolve("request.json");
            Files.writeString(subscriptionFile, SUBSCRIPTION_JSON);

            val exitCode = SaplNodeApplication
                    .run(new String[] { "check", "--dir", policyDir.toString(), "-f", subscriptionFile.toString() });

            assertThat(exitCode).isZero();
        }

    }

    @Nested
    @DisplayName("directory mode with stdin input")
    class DirectoryWithStdinInputTests {

        @TempDir
        Path policyDir;

        private InputStream originalIn;

        @BeforeEach
        void captureStdin() {
            originalIn = System.in;
        }

        @AfterEach
        void restoreStdin() {
            System.setIn(originalIn);
        }

        @Test
        @DisplayName("-f - reads subscription from stdin")
        void whenStdinInput_thenCorrectExitCode() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");
            System.setIn(new ByteArrayInputStream(SUBSCRIPTION_JSON.getBytes(StandardCharsets.UTF_8)));

            val exitCode = SaplNodeApplication.run(new String[] { "check", "--dir", policyDir.toString(), "-f", "-" });

            assertThat(exitCode).isZero();
        }

    }

}
