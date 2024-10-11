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

import org.eclipse.xtext.testing.TestCompletionConfiguration;
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
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where var foo = 5; var bar = 6; ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action", "environment", "foo", "resource", "subject", "bar");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestVariableInBodyAfterSubject() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where var foo = 5; var bar = 6; subject.attribute == ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action", "bar", "environment", "foo", "resource", "subject");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestVariableInBody_NotSuggestOutOfScopeVariable() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where var foo = 5; var bar = 6;";
            String cursor = "policy \"test\" permit where var foo = 5; ";
            it.setModel(policy);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action", "environment", "foo", "resource", "subject");
                final var unwanted = List.of("bar");
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestVariableInBodyAfterSubject_NotSuggestOutOfScopeVariable() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "policy \"test\" permit where var foo = 5; subject.attribute == abc; subject.attribute == var bar = 6;";
            String cursor = "policy \"test\" permit where var foo = 5; subject.attribute == abc; subject.attribute == ";
            it.setModel(policy);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action", "environment", "foo", "resource", "subject");
                final var unwanted = List.of("bar");
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestFunctionsFromWildcardImport() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import schemaTest.*\npolicy \"test policy\" deny where var foo = 5;";
            String cursor = "policy \"test policy\" deny where var foo = 5;";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("dog()", "dog().race", "food()", "food(String species)", "location()",
                        "location().latitude", "location().longitude", "person()", "person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestFunctionsFromLibraryImport() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "import schemaTest as abc\npolicy \"test policy\" deny where var foo = 5;";
            String cursor = "policy \"test policy\" deny where var foo = 5;";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("abc.dog()", "abc.dog().race", "abc.food()", "abc.food(String species)",
                        "abc.location()", "abc.location().latitude", "abc.location().longitude", "abc.person()",
                        "abc.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

}
