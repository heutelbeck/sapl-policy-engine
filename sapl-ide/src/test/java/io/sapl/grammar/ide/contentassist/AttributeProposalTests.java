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

import lombok.extern.slf4j.Slf4j;

@Slf4j
class AttributeProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_attribute_without_import() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import2() {
        final var document = """
                policy "test" deny where
                subject.<t§
                var bar = 2;""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var unwanted = List.of("tempetature.atLocation>.unit", "temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3_extend() {
        final var document = """
                policy "test" deny where
                subject.<t§""";
        final var unwanted = List.of("temperature.now>.unit", "temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import4() {
        final var document = """
                policy "test" deny where
                subject.§
                var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<t§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5_extension() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature.atLocation>.§""";
        final var expected = List.of(".unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import6() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<t§
                var bar = 2;""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import7() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                subject.§""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import8() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.§
                var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import9() {
        final var document = """
                subject schema general_schema
                policy "test" deny where
                subject§
                var foo = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable() {
        final var document = """
                policy "test" deny where
                var foo = <temperature.now()>;
                foo.§""";
        final var expected = List.of(".unit", ".value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable2() {
        final var document = """
                policy "test" deny where
                var foo = <temperature.now()>;
                foo.§
                var bar = 1;""";
        final var expected = List.of(".unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable() {
        final var document = """
                import temperature.now
                policy "test" deny where
                var foo = "".<now>;
                fo§""";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable_extended() {
        final var document = """
                import temperature.now
                policy "test" deny where
                var foo = <now>;
                foo.§""";
        final var expected = List.of(".unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import() {
        final var document = """
                import temperature as abc
                policy "test" deny where
                var foo = <ab§""";
        final var expected = List.of("abc.mean(a1, a2)>", "abc.now>", "abc.predicted(a1)>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import2() {
        final var document = """
                import temperature as abc
                policy "test" deny where
                <ab§""";
        final var expected = List.of("abc.mean(a1, a2)>", "abc.now>", "abc.predicted(a1)>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import3() {
        final var document = """
                import temperature as abc
                policy "test" deny where
                <abc.now>.§""";
        final var expected = List.of("unit", "value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_library_import_assigned_to_variable() {
        final var document = """
                import temperature as temp
                policy "test" deny where
                var foo = <temp.now()>;
                foo.§""";
        final var expected = List.of(".unit", ".value");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_wildcard_import_assigned_to_variable() {
        final var document = """
                import temperature.*
                policy "test" deny where
                var foo = <now>;
                foo.§""";
        final var expected = List.of("unit");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute() {
        final var document = """
                policy "test" deny where
                var foo = 1;
                foo.<temperature§""";
        final var expected = List.of("temperature.atLocation>");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute2() {
        final var document = """
                policy "test" deny where
                <temperature.now>.§""";
        final var expected = List.of("unit", "value");
        assertProposalsContain(document, expected);
    }
}
