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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.node.cli.SaplNodeCli;
import lombok.val;
import picocli.CommandLine;

@DisplayName("CLI error reporting")
class SaplNodeApplicationErrorHandlerTests {

    @Test
    @DisplayName("an uncaught exception is reported as a single Error line without a stack trace")
    void whenNotVerboseThenCleanErrorWithoutStackTrace() throws Exception {
        val captured = new StringWriter();
        val cli      = new SaplNodeCli();
        val cmd      = new CommandLine(cli);
        cmd.setErr(new PrintWriter(captured, true));

        val exitCode = SaplNodeApplication.cleanErrorHandler(cli)
                .handleExecutionException(new IllegalStateException("something broke"), cmd, null);

        assertThat(exitCode).isNotZero();
        assertThat(captured.toString()).contains("Error: something broke").doesNotContain("\tat ");
    }

    @Test
    @DisplayName("verbose mode appends the full stack trace")
    void whenVerboseThenIncludesStackTrace() throws Exception {
        val captured = new StringWriter();
        val cli      = new SaplNodeCli();
        val cmd      = new CommandLine(cli);
        cmd.parseArgs("-v");
        cmd.setErr(new PrintWriter(captured, true));

        SaplNodeApplication.cleanErrorHandler(cli).handleExecutionException(new IllegalStateException("boom"), cmd,
                null);

        assertThat(captured.toString()).contains("Error: boom").contains("\tat ");
    }

}
