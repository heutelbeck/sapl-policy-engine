/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.sapl.ProtocolText;
import io.sapl.lsp.sapl.SAPLParsedDocument;
import io.sapl.lsp.sapltest.SAPLTestParsedDocument;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import lombok.val;

@DisplayName("Selection range builder")
class SelectionRangeBuilderTests {

    /**
     * A test whose subject is a text over two lines. Counted from 0, the text
     * starts on line 2 at character 13 and ends on line 3 after character 4.
     */
    private static final String TEST_WITH_A_TEXT_OVER_TWO_LINES = """
            requirement "r" {
                scenario "s"
                    when "a
            bcd" attempts "read" on "document"
                    expect permit;
            }
            """;

    private static final Range RANGE_OF_THE_TEXT_OVER_TWO_LINES = new Range(new Position(2, 13), new Position(3, 4));

    @Test
    @DisplayName("every parent in the chain strictly encloses its child")
    void whenSiblingsTouchAtCursorThenParentChainStaysStrictlyNesting() {
        // The cursor sits exactly on the boundary between two adjacent sibling nodes:
        // the identifier subject ends where the step .mood starts. An expand-selection
        // chain must never present a node as the parent of another unless it actually
        // encloses it, otherwise pressing "expand selection" would shrink or jump the
        // editor selection sideways instead of widening it.
        val content  = "policy \"p\" permit subject.mood;";
        val boundary = new Position(0, content.indexOf(".mood"));

        val chain = SelectionRangeBuilder.buildSelectionRange(saplTreeOf(content), boundary);

        assertThat(chain).isNotNull().satisfies(innermost -> assertThat(isStrictlyNesting(innermost))
                .as("each parent range must enclose its child range").isTrue());
    }

    @Test
    @DisplayName("range end of a node closing with a multi-line token sits at the token's real end")
    void whenStopTokenSpansMultipleLinesThenRangeEndIsAtTokenEnd() {
        // A node closing on a multi-line SAPLTest text: the text starts on line 2 and
        // continues onto line 3. An operator expanding the selection expects the
        // highlight to end where the text actually ends (line 3, after bcd"), not at a
        // phantom column past its start.
        val chain = SelectionRangeBuilder.buildSelectionRange(saplTestTreeOf(TEST_WITH_A_TEXT_OVER_TWO_LINES),
                RANGE_OF_THE_TEXT_OVER_TWO_LINES.getStart());

        assertThat(chain).isNotNull().extracting(SelectionRange::getRange).isEqualTo(RANGE_OF_THE_TEXT_OVER_TWO_LINES);
    }

    @Test
    @DisplayName("a cursor inside a multi-line closing token is recognised as enclosed")
    void whenCursorSitsInsideMultiLineStopTokenThenNodeIsContained() {
        // The cursor rests on the second physical line of a multi-line closing token.
        // Containment must follow the token onto that line, otherwise the node is
        // wrongly dropped from the expand-selection chain.
        val chain = SelectionRangeBuilder.buildSelectionRange(saplTestTreeOf(TEST_WITH_A_TEXT_OVER_TWO_LINES),
                new Position(3, 1));

        assertThat(chain).isNotNull().extracting(SelectionRange::getRange).isEqualTo(RANGE_OF_THE_TEXT_OVER_TWO_LINES);
    }

    @Nested
    @DisplayName("the innermost selection at a cursor in an identifier")
    class InnermostSelectionTests {

        static Stream<Arguments> documentsWithAnIdentifier() {
            val afterTheFace = "policy \"p\" permit \"" + ProtocolText.GRINNING_FACE + "\" == subject.mood;";
            val onLineTwo    = ProtocolText.documentOf("\r", "    subject.mood;");
            return Stream.of(
                    arguments("a character outside the basic plane before the identifier, on its line", afterTheFace,
                            identifierOnLine(0, afterTheFace.indexOf("subject"))),
                    arguments("lines that end with a carriage return", onLineTwo, identifierOnLine(2, 4)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("documentsWithAnIdentifier")
        @DisplayName("selects the identifier the person points at")
        void whenCursorIsInAnIdentifierThenInnermostSelectionIsThatIdentifier(String description, String content,
                Range identifier) {
            val insideTheIdentifier = new Position(identifier.getStart().getLine(),
                    identifier.getStart().getCharacter() + 1);

            val chain = SelectionRangeBuilder.buildSelectionRange(saplTreeOf(content), insideTheIdentifier);

            assertThat(chain).isNotNull().extracting(SelectionRange::getRange).isEqualTo(identifier);
        }

        private static Range identifierOnLine(int line, int character) {
            return new Range(new Position(line, character), new Position(line, character + "subject".length()));
        }
    }

    private static SaplContext saplTreeOf(String content) {
        return new SAPLParsedDocument("test.sapl", content).getSaplParseTree();
    }

    private static SaplTestContext saplTestTreeOf(String content) {
        return new SAPLTestParsedDocument("test.sapltest", content).getSaplTestParseTree();
    }

    private static boolean isStrictlyNesting(SelectionRange innermost) {
        var child  = innermost;
        var parent = innermost.getParent();
        while (parent != null) {
            if (!encloses(parent.getRange(), child.getRange())) {
                return false;
            }
            child  = parent;
            parent = parent.getParent();
        }
        return true;
    }

    private static boolean encloses(Range outer, Range inner) {
        return !isBefore(inner.getStart(), outer.getStart()) && !isBefore(outer.getEnd(), inner.getEnd());
    }

    private static boolean isBefore(Position a, Position b) {
        if (a.getLine() != b.getLine()) {
            return a.getLine() < b.getLine();
        }
        return a.getCharacter() < b.getCharacter();
    }

}
