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

import static io.sapl.node.cli.support.ShellQuoting.posix;
import static io.sapl.node.cli.support.ShellQuoting.powerShell;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import lombok.val;
import picocli.CommandLine;

@DisplayName("generate basic command shell examples")
class GenerateCredentialsCommandTests {

    private static final Pattern PASSWORD_LINE = Pattern.compile("Password: (.+)\\R");
    private static final Pattern USERNAME_LINE = Pattern.compile("Username: (.+)\\R");

    private static String group(Pattern pattern, String haystack) {
        val matcher = pattern.matcher(haystack);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String runGenerateBasic() {
        val out = new StringWriter();
        val cmd = new CommandLine(new GenerateCredentialsCommand());
        cmd.setOut(new PrintWriter(out));
        val exitCode = cmd.execute("basic");
        assertThat(exitCode).isZero();
        return out.toString();
    }

    @RepeatedTest(50)
    @DisplayName("prints copy-paste-safe bash and PowerShell curl examples for the generated password")
    void whenGeneratingBasicCredentialsThenPerShellExamplesAreCopyPasteSafe() {
        val output     = runGenerateBasic();
        val username   = group(USERNAME_LINE, output);
        val password   = group(PASSWORD_LINE, output);
        val credential = username + ":" + password;

        assertThat(output).contains("bash/POSIX", "PowerShell").contains("curl").contains(posix(credential))
                .contains(powerShell(credential));
    }
}
