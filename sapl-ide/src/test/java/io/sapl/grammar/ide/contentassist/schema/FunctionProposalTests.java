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
package io.sapl.grammar.ide.contentassist.schema;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

import io.sapl.grammar.ide.contentassist.CompletionTests;

class FunctionProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where var foo = schemaTest.person();
                    schemaTe""";

            final var cursor = "schemaTe";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.person();
                    tes""";

            final var cursor = "tes";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("test.person().name", "test.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import_inMultipleBrackets() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = ((test.person()));
                    foo""";

            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import_inMultipleBrackets_butWithSteps() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = ((test.person())).name;
                    tes""";

            final var cursor = "tes";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var unwanted = List.of("test.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where var foo = schemaTest.dog();
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.dog();
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("foo.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_two_variables_assigned_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.dog();
                    final var bar = test.dog();
                    ba""";

            final var cursor = "ba";
            it.setModel(policy);
            it.setLine(3);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("bar.race");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("bar.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_two_variables_assigned_function_with_alias_import_and_trailing_dot() {
        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.dog();
                    foo.""";

            final var cursor = "foo.";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("foo.name", "filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    final var foo = schemaTest""";

            final var cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_multiple_parameters() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    final var foo = schemaTest""";

            final var cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.dog()", "schemaTest.dog().race",
                        "schemaTest.food(String species)", "schemaTest.person()", "schemaTest.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_does_not_exist() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    final var foo = schemaTest.cat""";

            final var cursor = "var foo = schemaTest.cat";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var unwanted = List.of("schemaTest.dog()", "schemaTest.dog().race",
                        "schemaTest.food(String species)", "schemaTest.person()", "schemaTest.person().name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_exists() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    final var foo = schemaTest.dog""";

            final var cursor = "var foo = schemaTest.dog";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_without_assignment() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    schemaTest""";

            final var cursor = "schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_not_suggest_out_of_scope() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    foo
                    final var foo = schemaTest.dog""";

            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var unwanted = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_function_assignment_schema_from_path() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    final var foo = schemaTest.locatio""";

            final var cursor = "var foo = schemaTest.locatio";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.location()", "schemaTest.location().latitude");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_function_schema_from_path() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where
                    schemaTest.locatio""";

            final var cursor = "schemaTest.locatio";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schemaTest.location()", "schemaTest.location().latitude");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_variable_no_unrelated_suggestions_after_dot() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    policy "test" deny where var foo = schemaTest.location();
                    foo.""";

            final var cursor = "foo.";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.latitude");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("clock.millis>", "filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_variable_no_import_suggestions_after_dot() {

        testCompletion((TestCompletionConfiguration it) -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.person();
                    foo.""";

            final var cursor = "foo.";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.name");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("test.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

}
