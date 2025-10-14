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

import java.util.List;

class FunctionProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_function_without_import() {
        final var document = """
                policy "test" deny where var foo = schemaTest.person();
                schemaTe§""";
        final var expected = List.of("schemaTest.dog(dogRegistryRecord)", "schemaTest.food(species)",
                "schemaTest.foodPrice(food)", "schemaTest.location()", "schemaTest.person(name, nationality, age)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_without_import_expansion() {
        final var document = """
                policy "test" deny where var foo = schemaTest.person();
                foo.§""";
        final var expected = List.of(".name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {
        final var document = """
                import schemaTest.dog as canine
                policy "test"
                deny
                can§""";
        final var expected = List.of("canine(dogRegistryRecord)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import_inMultipleBrackets() {
        final var document = """
                import schemaTest.person as somebody
                policy "test" deny where var foo = ((somebody()));
                foo§""";
        final var expected = List.of(".name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import_inMultipleBrackets_butWithSteps() {
        final var document = """
                import schemaTest.person as test
                policy "test" deny where var foo = ((test())).name;
                tes§""";
        final var unwanted = List.of("test.name");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_without_import() {
        final var document = """
                policy "test" deny where var foo = schemaTest.dog();
                fo§""";
        final var expected = List.of("foo", "foo.age", "foo.fur_color", "foo.name", "foo.species");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_with_alias_import() {
        final var document = """
                import schemaTest.dog as canine
                policy "test" deny where var foo = canine();
                fo§""";
        final var expected = List.of("foo", "foo.age", "foo.fur_color", "foo.name", "foo.species");
        final var unwanted = List.of("foo.nationality");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_two_variables_assigned_function_with_alias_import() {
        final var document = """
                import schemaTest.dog as canine
                policy "test" deny where var foo = canine();
                var bar = canine();
                bar.§""";
        final var expected = List.of(".age", ".fur_color", ".name", ".species");
        final var unwanted = List.of(".nationality");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_two_variables_assigned_function_with_alias_import_and_trailing_dot() {
        final var document = """
                import schemaTest.dog as canine
                policy "test" deny where var foo = canine();
                foo.§""";
        final var expected = List.of(".age", ".fur_color", ".name", ".species");
        final var unwanted = List.of("foo.name", "filter.blacken");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_function() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.per§""";
        final var expected = List.of("person(name, nationality, age)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_extended() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.person().§""";
        final var expected = List.of(".name", ".age", ".nationality");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_multiple_parameters() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest§""";
        final var expected = List.of("schemaTest.dog(dogRegistryRecord)", "schemaTest.food(species)",
                "schemaTest.foodPrice(food)", "schemaTest.location()", "schemaTest.person(name, nationality, age)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_does_not_exist() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.cat§""";
        final var unwanted = List.of(".dog(dogRegistryRecord)", ".food(species)", ".foodPrice(food)", ".location()",
                ".person(name, nationality, age)");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_function_exists() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.dog§""";
        final var expected = List.of("(dogRegistryRecord)", "dog(dogRegistryRecord)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_exists_extended() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.dog(dogRegistryRecord).§""";
        final var expected = List.of(".age", ".fur_color", ".name", ".species");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_without_assignment() {
        final var document = """
                policy "test" deny where
                schemaTest§""";
        final var expected = List.of("schemaTest.dog(dogRegistryRecord)", "schemaTest.food(species)",
                "schemaTest.foodPrice(food)", "schemaTest.location()", "schemaTest.person(name, nationality, age)");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_not_suggest_out_of_scope() {
        final var document = """
                policy "test" deny where
                foo§
                final var foo = schemaTest.dog""";
        final var unwanted = List.of("schemaTest.dog()", "schemaTest.dog().race");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_function_assignment_schema_from_path() {
        final var document = """
                policy "test" deny where
                final var foo = schemaTest.location().§""";
        final var expected = List.of(".latitude");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_function_schema_from_path() {
        final var document = """
                policy "test" deny where
                schemaTest.locatio§""";
        final var expected = List.of("location()");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_variable_no_unrelated_suggestions_after_dot() {
        final var document = """
                policy "test" deny where var foo = schemaTest.location();
                foo.§""";
        final var expected = List.of(".latitude");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_variable_no_import_suggestions_after_dot() {
        final var document = """
                import schemaTest.person as someone
                policy "test" deny where var foo = someone();
                foo.§""";
        final var expected = List.of(".name");
        assertProposalsContain(document, expected);
    }

}
