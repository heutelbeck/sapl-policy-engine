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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

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
        final var expected = List.of("ction");
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
        final var expected = List.of("nvironment");
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
        final var expected = List.of("oo");
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
        final var expected = List.of("ar");
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
        final var expected = List.of("ubject");
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
        final var expected = List.of("esource");
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
        final var expected = List.of("oo");
        final var unwanted = List.of("ar");
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
    void testCompletion_SuggestFunctionsFromWildcardImport() {
        final var document = """
                import schemaTest.*
                policy "test policy"
                deny
                where
                  var foo = 5;
                  d§""";
        final var expected = List.of("og(dogRegistryRecord)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestFunctionsFromLibraryImport() {
        final var document = """
                import schemaTest as abc
                policy "test policy"
                deny
                where
                  var foo = 5;
                  a§""";
        final var expected = List.of("bc.dog(dogRegistryRecord)");
        assertProposalsContain(document, expected);
    }

}
