package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;

public class VariableIdentifierProposalsTests extends CompletionTests {
    @Test
    void testCompletion_PolicyBody_previous_variable() {
        final var policy   = """
                policy "test" deny
                where
                   var toast = "bread";
                   "bread" == t#""";
        final var expected = List.of("oast");
        assertProposalsContain(policy, expected);
    }

    @Test
    void testCompletion_PolicyBody_variable_after_cursor() {
        final var policy   = """
                policy "test" deny
                where
                   "bread" == t#;
                   var toast = "bread";""";
        final var unwanted = List.of("oast");
        assertProposalsDoNotContain(policy, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_environmentVariable() {
        final var policy   = """
                policy "x" permit abba.a.#""";
        final var expected = List.of(".x");
        assertProposalsContain(policy, expected);
    }
}
