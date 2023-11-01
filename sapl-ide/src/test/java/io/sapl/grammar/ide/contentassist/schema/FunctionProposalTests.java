package io.sapl.grammar.ide.contentassist.schema;

import io.sapl.grammar.ide.contentassist.CompletionTests;
import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

class FunctionProposalTests extends CompletionTests {

    @Test
    void testCompletion_PolicyBody_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where var foo = schemaTest.person();
                    schemaTe""";

            String cursor = "foo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.person();
                    tes""";

            String cursor = "tes";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("test.person().name", "test.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where var foo = schemaTest.dog();
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_function_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.dog();
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo.race");
                assertProposalsSimple(expected, completionList);
                var unwanted = List.of("foo.name");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.person().name", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_multiple_parameters() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race",
                        "schemaTest.food(String species)", "schemaTest.person()", "schemaTest.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_does_not_exist() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest.cat""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race",
                        "schemaTest.food(String species)", "schemaTest.person()", "schemaTest.person().name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_does_not_exist2() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = schemaTest.dog""";

            String cursor = "var foo = schemaTest";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("schemaTest.dog()", "schemaTest.dog().race");
                assertProposalsSimple(expected, completionList);
            });
        });
    }
}
