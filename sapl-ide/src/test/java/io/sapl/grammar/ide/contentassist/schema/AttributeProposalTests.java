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

class AttributeProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_attribute_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    subject.<""";

            String cursor = "subject.<";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import2() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    subject.<
                    var bar = 2;""";

            String cursor = "subject.<";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import3() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    subject.""";

            String cursor = "subject.";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import4() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    subject.
                    var bar = 1;""";

            String cursor = "subject.";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import5() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = 1;
                    foo.<""";

            String cursor = "foo.<";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import6() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = 1;
                    foo.<
                    var bar = 2;""";

            String cursor = "foo.<";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import7() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = 1;
                    subject.""";

            String cursor = "foo.";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import8() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = 1;
                    foo.
                    var bar = 1;""";

            String cursor = "foo.";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import9() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    subject schema general_schema
                    policy "test" deny where
                    subject
                    var foo = 1;""";

            String cursor = "subject";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("<temperature.now>.unit", "<temperature.mean(a1, a2)>.value");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = <temperature.now()>;
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_without_import_assigned_to_variable2() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = <temperature.now()>;
                    fo
                    var bar = 1;""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_function_import_assigned_to_variable() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature.now
                    policy "test" deny where
                    var foo = <now>;
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(3);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature as abc
                    policy "test" deny where
                    var foo = <ab""";

            String cursor = "var foo = <ab";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<abc.mean(a1, a2)>", "<abc.now>", "<abc.predicted(a2)>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import2() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature as abc
                    policy "test" deny where
                    <ab""";

            String cursor = "<ab";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<abc.mean(a1, a2)>", "<abc.now>", "<abc.predicted(a2)>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_alias_import3() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature as abc
                    policy "test" deny where
                    <abc.now>""";

            String cursor = "<abc.now>";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<abc.now>.unit", "<abc.now>.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_library_import_assigned_to_variable() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature as temp
                    policy "test" deny where
                    var foo = <temp.now()>;
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(3);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_with_wildcard_import_assigned_to_variable() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature.*
                    policy "test" deny where
                    var foo = <now>;
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(3);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = 1;
                    foo.<temperature""";

            String cursor = "foo.<temperature";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute2() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    <temperature.now>.""";

            String cursor = "<temperature.now>.";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("<temperature.now>.unit");
                assertProposalsSimple(expected, completionList);
            });
        });
    }
}
