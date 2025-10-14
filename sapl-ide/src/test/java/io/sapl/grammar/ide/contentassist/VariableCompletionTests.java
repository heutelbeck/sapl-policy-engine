/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

/**
 * Tests regarding the auto-completion of variables
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
class VariableCompletionTests extends CompletionTests {

    @Test
    void testCompletion_SuggestVariableInBody() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  a§""";
        final var expected = List.of("action");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody2() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  e§""";
        final var expected = List.of("environment");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody3() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  f§""";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody4() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  b§""";
        final var expected = List.of("bar");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody5() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  s§""";
        final var expected = List.of("subject");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody6() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  r§""";
        final var expected = List.of("resource");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBodyAfterSubject() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  var bar = 6;
                  subject.attribute == f§""";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestVariableInBody_NotSuggestOutOfScopeVariable() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  f§
                  var bar = 6;""";
        final var expected = List.of("foo");
        final var unwanted = List.of("bar");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_SuggestVariableInBodyAfterSubject_NotSuggestOutOfScopeVariable() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 5;
                  subject.attribute == abc;
                  subject.attribute == f§
                  var bar = 6;""";
        final var expected = List.of("foo");
        final var unwanted = List.of("bar");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_SuggestFromAliasImport() {
        final var document = """
                import schemaTest.dog as xyz
                policy "test policy"
                deny
                where
                  var foo = 5;
                  x§""";
        final var expected = List.of("xyz(dogRegistryRecord)");
        assertProposalsContain(document, expected);
    }

}
