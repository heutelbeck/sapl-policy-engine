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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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

@DisplayName("decide-once integration")
class DecideOnceIntegrationTests {

    private PrintStream           originalOut;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Nested
    @DisplayName("directory mode with named flags")
    class DirectoryWithNamedFlagsTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("permit-all policy outputs PERMIT JSON")
        void whenPermitPolicy_thenPermitJson() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

            val exitCode = SaplNodeApplication.run(new String[] { "decide-once", "--dir", policyDir.toString(), "-s",
                    "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString(StandardCharsets.UTF_8).trim()).isEqualTo("{\"decision\":\"PERMIT\"}");
        }

        @Test
        @DisplayName("non-matching policy produces decision output")
        void whenNoPolicyMatches_thenOutputContainsDecision() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"restricted\" permit subject == \"admin\";");

            val exitCode = SaplNodeApplication.run(new String[] { "decide-once", "--dir", policyDir.toString(), "-s",
                    "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString(StandardCharsets.UTF_8).trim()).contains("\"decision\":");
        }

        @Test
        @DisplayName("JSON object subject is accessible in policy conditions")
        void whenJsonObjectSubject_thenPolicyMatchesFields() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"by-name\" permit subject.name == \"alice\";");

            val exitCode = SaplNodeApplication.run(new String[] { "decide-once", "--dir", policyDir.toString(), "-s",
                    "{\"name\":\"alice\"}", "-a", "\"read\"", "-r", "\"document\"" });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString(StandardCharsets.UTF_8).trim()).isEqualTo("{\"decision\":\"PERMIT\"}");
        }
    }

    @Nested
    @DisplayName("directory mode with file input")
    class DirectoryWithFileInputTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("subscription from JSON file produces correct decision")
        void whenSubscriptionFile_thenCorrectDecision() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");
            val subscriptionFile = policyDir.resolve("request.json");
            Files.writeString(subscriptionFile,
                    "{\"subject\":\"alice\",\"action\":\"read\",\"resource\":\"document\"}");

            val exitCode = SaplNodeApplication.run(
                    new String[] { "decide-once", "--dir", policyDir.toString(), "-f", subscriptionFile.toString() });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString(StandardCharsets.UTF_8).trim()).isEqualTo("{\"decision\":\"PERMIT\"}");
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
        void whenStdinInput_thenCorrectDecision() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");
            val json = "{\"subject\":\"alice\",\"action\":\"read\",\"resource\":\"document\"}";
            System.setIn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

            val exitCode = SaplNodeApplication
                    .run(new String[] { "decide-once", "--dir", policyDir.toString(), "-f", "-" });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString(StandardCharsets.UTF_8).trim()).isEqualTo("{\"decision\":\"PERMIT\"}");
        }
    }

}
