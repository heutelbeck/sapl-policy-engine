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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;

@DisplayName("DecideOnceRunner")
class DecideOnceRunnerTests {

    private PrintStream           originalOut;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Nested
    @DisplayName("with permit-all policy")
    class PermitAllPolicyTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("outputs PERMIT decision as JSON and exits with code 0")
        void whenPermitPolicyExists_thenOutputsPermitJson() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

            val exitCode = SaplNodeApplication.run(new String[] { "decide-once", "-s", "alice", "-a", "read", "-r",
                    "document", "--io.sapl.pdp.embedded.pdp-config-type=DIRECTORY",
                    "--io.sapl.pdp.embedded.config-path=" + policyDir,
                    "--io.sapl.pdp.embedded.policies-path=" + policyDir });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString().trim()).isEqualTo("{\"decision\":\"PERMIT\"}");
        }
    }

    @Nested
    @DisplayName("with no matching policy")
    class NoMatchingPolicyTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("outputs DENY decision when no policy matches")
        void whenNoPolicyMatches_thenOutputsDenyJson() throws IOException {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"restricted\" permit subject == \"admin\";");

            val exitCode = SaplNodeApplication.run(new String[] { "decide-once", "-s", "alice", "-a", "read", "-r",
                    "document", "--io.sapl.pdp.embedded.pdp-config-type=DIRECTORY",
                    "--io.sapl.pdp.embedded.config-path=" + policyDir,
                    "--io.sapl.pdp.embedded.policies-path=" + policyDir });

            assertThat(exitCode).isZero();
            assertThat(capturedOut.toString().trim()).contains("\"decision\":");
        }
    }
}
