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

import lombok.experimental.UtilityClass;

/**
 * Quotes a literal string so it survives copy-paste into a shell command line
 * exactly as given, regardless of which shell metacharacters it contains.
 *
 * <p>
 * Generated credentials may contain any of {@code $-_.+!*'(),} as well as
 * other characters. A naive single-quoted example such as {@code 'pa'ss'}
 * breaks as soon as the value itself contains a single quote, so a per-shell
 * quoting strategy is required.
 */
@UtilityClass
public class ShellQuoting {

    /**
     * Quotes a literal for POSIX shells (bash, zsh, dash, sh).
     *
     * <p>
     * The value is wrapped in single quotes, inside which every character is
     * literal except the single quote itself. Each embedded single quote is
     * emitted as {@code '\''}: close the quoted span, write an escaped quote
     * outside it, then reopen the span. This is the standard robust POSIX
     * idiom and handles every character without exception.
     *
     * @param value the literal to quote
     * @return a POSIX-quoted word equal to {@code value} when read by the shell
     */
    public static String posix(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * Quotes a literal for Windows PowerShell.
     *
     * <p>
     * The value is wrapped in single quotes, which form a verbatim string
     * literal in PowerShell. The only character needing escaping is the single
     * quote, which is doubled.
     *
     * @param value the literal to quote
     * @return a PowerShell-quoted literal equal to {@code value}
     */
    public static String powerShell(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
