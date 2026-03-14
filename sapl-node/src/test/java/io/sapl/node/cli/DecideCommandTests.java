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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

@DisplayName("decide command")
class DecideCommandTests {

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelp_thenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new DecideCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("decide", "--dir", "--bundle", "-s", "-f");
        }

    }

}
