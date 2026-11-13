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

import static org.assertj.core.api.Assertions.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@DisplayName("attribute command tests")
class AttributesCommandTests {
    private final StringWriter out = new StringWriter();
    private final CommandLine  cmd = new CommandLine(new AttributesCommand());

    @BeforeEach
    void setUp() {
        cmd.setOut(new PrintWriter(out));
    }

    @Test
    @DisplayName("when help is requested then all subcommands are listed")
    void whenHelpIsRequestedThenAllSubcommandsAreListed() {
        cmd.execute("--help");
        assertThat(out.toString()).contains("publish", "delete", "get", "getall", "count");
        assertThat(cmd.execute()).isZero();
    }
}
