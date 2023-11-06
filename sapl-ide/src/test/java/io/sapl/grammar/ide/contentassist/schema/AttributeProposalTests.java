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
                    policy "test" deny where ;
                    var foo = subject.|<temperatur""";

            String cursor = "var foo = subject.|<temperatur";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("temperature.mean(a1, a2)>", "temperature.mean(a1, a2)>.period",
                        "temperature.mean(a1, a2)>.value", "temperature.now()>", "temperature.now()>.unit",
                        "temperature.now()>.value", "temperature.predicted(a2)>");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_assigned_to_variable_with_alias_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    import temperature as temp
                    policy "test" deny where var foo = subject.<temp.mean(a1, a2)>;
                    foo""";

            String cursor = "foo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo", "foo.period", "foo.value");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_variable_assigned_attribute_without_import() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where var foo = subject.<temperature.mean(a1, a2)>;
                    fo""";

            String cursor = "fo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("foo", "foo.period", "foo.value");
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
    void testCompletion_PolicyBody_attribute() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = subject.<temperature""";

            String cursor = "var foo = subject.<temperature";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var expected = List.of("temperature.mean(a1, a2)>", "temperature.mean(a1, a2)>.period",
                        "temperature.mean(a1, a2)>.value", "temperature.now()>", "temperature.now()>.unit",
                        "temperature.now()>.value", "temperature.predicted(a2)>");
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_not_suggest_out_of_scope() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    foo
                    var foo = subject.<temperature""";

            String cursor = "foo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                var unwanted = List.of("temperature.mean(a1, a2)>", "temperature.mean(a1, a2)>.period");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_attribute_does_not_exist() {

        testCompletion((TestCompletionConfiguration it) -> {
            String policy = """
                    policy "test" deny where
                    var foo = subject.<temperature.max>""";

            String cursor = "var foo = subject.<temperature.max>";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                assertProposalsSimple(List.of(), completionList);
            });
        });
    }

}
