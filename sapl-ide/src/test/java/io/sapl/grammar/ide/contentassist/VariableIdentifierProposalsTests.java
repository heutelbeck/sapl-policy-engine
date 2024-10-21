/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;

public class VariableIdentifierProposalsTests extends CompletionTests {
    @Test
    void testCompletion_PolicyBody_previous_variable() {
        final var document = """
                policy "test" deny
                where
                   var toast = "bread";
                   "bread" == t§""";
        final var expected = List.of("toast");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_variable_after_cursor() {
        final var document = """
                policy "test" deny
                where
                   "bread" == t§;
                   var toast = "bread";""";
        final var unwanted = List.of("toast");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_environmentVariable() {
        final var document = """
                policy "x" permit abba.a.§""";
        final var expected = List.of(".x");
        assertProposalsContain(document, expected);
    }
}
