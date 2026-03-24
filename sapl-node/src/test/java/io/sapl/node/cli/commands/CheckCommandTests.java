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

import static io.sapl.node.cli.commands.CheckCommand.toExitCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.val;
import picocli.CommandLine;

@DisplayName("check command")
class CheckCommandTests {

    @Nested
    @DisplayName("exit code mapping")
    class ExitCodeMappingTests {

        @ParameterizedTest(name = "{0} -> exit {1}")
        @DisplayName("decision maps to expected exit code")
        @MethodSource
        void whenDecision_thenExpectedExitCode(String description, AuthorizationDecision decision, int expectedCode) {
            assertThat(toExitCode(decision)).isEqualTo(expectedCode);
        }

        static Stream<Arguments> whenDecision_thenExpectedExitCode() {
            return Stream.of(arguments("simple PERMIT", AuthorizationDecision.PERMIT, 0),
                    arguments("DENY", AuthorizationDecision.DENY, 2),
                    arguments("NOT_APPLICABLE", AuthorizationDecision.NOT_APPLICABLE, 3),
                    arguments("INDETERMINATE", AuthorizationDecision.INDETERMINATE, 4));
        }

        @ParameterizedTest(name = "PERMIT with {0} -> exit 4")
        @DisplayName("PERMIT with constraints returns exit code 4")
        @MethodSource
        void whenPermitWithConstraints_thenExitCode4(String description, AuthorizationDecision decision) {
            assertThat(toExitCode(decision)).isEqualTo(4);
        }

        static Stream<Arguments> whenPermitWithConstraints_thenExitCode4() {
            val obligation = new ArrayValue(List.of(new TextValue("log-access")));
            return Stream.of(
                    arguments("obligations",
                            new AuthorizationDecision(Decision.PERMIT, obligation, Value.EMPTY_ARRAY, Value.UNDEFINED)),
                    arguments("resource transformation",
                            new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                                    new TextValue("transformed"))),
                    arguments("obligations and resource", new AuthorizationDecision(Decision.PERMIT, obligation,
                            Value.EMPTY_ARRAY, new TextValue("transformed"))));
        }

        @Test
        @DisplayName("PERMIT with advice but no obligations returns exit code 0")
        void whenPermitWithAdviceOnly_thenExitCode0() {
            val advice   = new ArrayValue(List.of(new TextValue("consider-logging")));
            val decision = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, advice, Value.UNDEFINED);
            assertThat(toExitCode(decision)).isZero();
        }

    }

    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelp_thenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new CheckCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("check", "--dir", "--bundle", "-s", "-f");
        }

    }

}
