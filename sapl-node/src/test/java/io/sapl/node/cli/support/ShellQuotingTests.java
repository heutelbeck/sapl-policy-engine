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
package io.sapl.node.cli.support;

import static io.sapl.node.cli.support.ShellQuoting.posix;
import static io.sapl.node.cli.support.ShellQuoting.powerShell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

@DisplayName("shell quoting of credentials")
class ShellQuotingTests {

    /**
     * Decodes a POSIX single-quoted shell word the way the operator's shell
     * would, so the test asserts the quoted form actually round-trips back to
     * the original credential. The only metacharacter inside single quotes is
     * the single quote itself, which terminates the quoted span. Everything
     * else is literal.
     */
    private static String unquotePosix(String quoted) {
        val out      = new StringBuilder();
        var inQuotes = false;
        var i        = 0;
        while (i < quoted.length()) {
            val c = quoted.charAt(i);
            if (c == '\'') {
                inQuotes = !inQuotes;
                i++;
            } else if (!inQuotes && c == '\\' && i + 1 < quoted.length()) {
                out.append(quoted.charAt(i + 1));
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Decodes a PowerShell single-quoted string literal: the whole word is
     * wrapped in single quotes and a literal single quote is encoded as two
     * single quotes. No other escaping applies inside the literal.
     */
    private static String unquotePowerShell(String quoted) {
        assertThat(quoted).startsWith("'").endsWith("'");
        val inner = quoted.substring(1, quoted.length() - 1);
        return inner.replace("''", "'");
    }

    static Stream<Arguments> credentialsWithSpecials() {
        return Stream.of(arguments("password with single quote", "pa'ss"),
                arguments("password with every generator special", "$-_.+!*'(),"),
                arguments("basic-auth pair with single quote", "user:pa'ss+word!"),
                arguments("dollar and backtick", "a$b`c"), arguments("double quote and backslash", "a\"b\\c"),
                arguments("plain alphanumeric", "abcXYZ123"));
    }

    @Nested
    @DisplayName("POSIX/bash")
    class PosixTests {

        @ParameterizedTest(name = "{0}")
        @DisplayName("quoted credential round-trips to the original")
        @MethodSource("io.sapl.node.cli.support.ShellQuotingTests#credentialsWithSpecials")
        void whenCredentialHasSpecialsThenPosixQuotingRoundTrips(String description, String credential) {
            val quoted = posix(credential);
            assertThat(unquotePosix(quoted)).isEqualTo(credential);
        }
    }

    @Nested
    @DisplayName("PowerShell")
    class PowerShellTests {

        @ParameterizedTest(name = "{0}")
        @DisplayName("quoted credential round-trips to the original")
        @MethodSource("io.sapl.node.cli.support.ShellQuotingTests#credentialsWithSpecials")
        void whenCredentialHasSpecialsThenPowerShellQuotingRoundTrips(String description, String credential) {
            val quoted = powerShell(credential);
            assertThat(unquotePowerShell(quoted)).isEqualTo(credential);
        }
    }
}
