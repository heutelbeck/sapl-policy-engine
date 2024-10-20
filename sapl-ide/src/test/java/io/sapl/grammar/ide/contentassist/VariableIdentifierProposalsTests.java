package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;

public class VariableIdentifierProposalsTests extends CompletionTests {
    @Test
    void testCompletion_PolicyBody_previous_variable() {
        final var document = """
                policy "test" deny
                where
                   var toast = "bread";
                   "bread" == t§""";
        final var expected = List.of("toast");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_variable_after_cursor() {
        final var document = """
                policy "test" deny
                where
                   "bread" == t§;
                   var toast = "bread";""";
        final var unwanted = List.of("toast");
        assertProposalsDoNotContain(document, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_environmentVariable() {
        final var document = """
                policy "x" permit abba.a.§""";
        final var expected = List.of(".x");
        assertProposalsContain(document, expected);
    }
}
