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

import org.junit.jupiter.api.Test;

import io.sapl.grammar.ide.contentassist.CompletionTests;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class AttributeProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_attribute_without_import() {
        final var policy   = """
                policy "test" deny where
                subject.<#""";
        final var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import2() {
        final var policy   = """
                policy "test" deny where
                subject.<#
                final var bar = 2;""";
        final var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3() {
        final var policy   = """
                policy "test" deny where
                subject.#""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import4() {
        final var policy   = """
                policy "test" deny where
                subject.#
                final var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5() {
        final var policy   = """
                policy "test" deny where
                final var foo = 1;
                foo.<#""";
        final var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import6() {
        final var policy   = """
                policy "test" deny where
                final var foo = 1;
                foo.<#
                final var bar = 2;""";
        final var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import7() {
        final var policy   = """
                policy "test" deny where
                final var foo = 1;
                subject.#""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import8() {
        final var policy   = """
                policy "test" deny where
                final var foo = 1;
                foo.#
                final var bar = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import9() {
        final var policy   = """
                subject schema general_schema
                policy "test" deny where
                subject#
                final var foo = 1;""";
        final var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable() {
        final var policy   = """
                policy "test" deny where
                final var foo = <temperature.now()>;
                fo#""";
        final var expected = List.of("foo.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable2() {
        final var policy   = """
                policy "test" deny where
                final var foo = <temperature.now()>;
                fo#
                final var bar = 1;""";
        final var expected = List.of("foo.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable() {
        final var policy   = """
                import temperature.now
                policy "test" deny where
                final var foo = <now>;
                fo#""";
        final var expected = List.of("foo.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import() {
        final var policy   = """
                import temperature as abc
                policy "test" deny where
                final var foo = <ab#""";
        final var expected = List.of("<abc.mean(a1, a2)>", "<abc.now>", "<abc.predicted(a2)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import2() {
        final var policy   = """
                import temperature as abc
                policy "test" deny where
                <ab#""";
        final var expected = List.of("<abc.mean(a1, a2)>", "<abc.now>", "<abc.predicted(a2)>");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import3() {
        final var policy   = """
                import temperature as abc
                policy "test" deny where
                <abc.now>#""";
        final var expected = List.of("<abc.now>.unit", "<abc.now>.value");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_library_import_assigned_to_variable() {
        final var policy   = """
                import temperature as temp
                policy "test" deny where
                final var foo = <temp.now()>;
                fo#""";
        final var expected = List.of("foo.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_wildcard_import_assigned_to_variable() {
        final var policy   = """
                import temperature.*
                policy "test" deny where
                final var foo = <now>;
                fo#""";
        final var expected = List.of("foo.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute() {
        final var policy   = """
                policy "test" deny where
                final var foo = 1;
                foo.<temperature#""";
        final var expected = List.of("<temperature.now>.unit");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_attribute2() {
        final var policy   = """
                policy "test" deny where
                <temperature.now>.#""";
        final var expected = List.of("<temperature.now>.unit");
        assertProposalsContain(policy, expected);
    }
}
