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

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Selection range builder")
class SelectionRangeBuilderTests {

    @Test
    @DisplayName("every parent in the chain strictly encloses its child")
    void whenSiblingsTouchAtCursorThenParentChainStaysStrictlyNesting() {
        // A well-formed parse tree where a cursor position sits exactly on the
        // boundary between two adjacent sibling nodes. An expand-selection chain
        // must never present a node as the parent of another unless it actually
        // encloses it, otherwise pressing "expand selection" would shrink or jump
        // the editor selection sideways instead of widening it.
        var root  = ruleContext(0, 0, 0, 10);
        var left  = ruleContext(0, 0, 0, 5);
        var right = ruleContext(0, 5, 0, 10);
        root.addChild(left);
        root.addChild(right);

        var boundary = new Position(0, 5);
        var chain    = SelectionRangeBuilder.buildSelectionRange(root, boundary);

        assertThat(chain).isNotNull();
        assertThat(isStrictlyNesting(chain)).as("each parent range must enclose its child range").isTrue();
    }

    @Test
    @DisplayName("range end of a node closing with a multi-line token sits at the token's real end")
    void whenStopTokenSpansMultipleLinesThenRangeEndIsAtTokenEnd() {
        // A node closing on a multi-line SAPL string literal: the stop token starts
        // on line 0 column 8 and its text "a\nbcd" continues onto line 1. An operator
        // expanding the selection expects the highlight to end where the literal
        // actually ends (line 1, after "bcd"), not at a phantom column past the start.
        var ctx = new ParserRuleContext();
        ctx.start = token(0, 0, "");
        ctx.stop  = token(0, 8, "a\nbcd");

        var chain = SelectionRangeBuilder.buildSelectionRange(ctx, new Position(0, 0));

        assertThat(chain).isNotNull();
        assertThat(chain.getRange().getEnd()).satisfies(end -> {
            assertThat(end.getLine()).isEqualTo(1);
            assertThat(end.getCharacter()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("a cursor inside a multi-line closing token is recognised as enclosed")
    void whenCursorSitsInsideMultiLineStopTokenThenNodeIsContained() {
        // The cursor rests on the second physical line of a multi-line closing token.
        // Containment must follow the token onto that line, otherwise the node is
        // wrongly dropped from the expand-selection chain.
        var ctx = new ParserRuleContext();
        ctx.start = token(0, 0, "");
        ctx.stop  = token(0, 8, "a\nbcd");

        var chain = SelectionRangeBuilder.buildSelectionRange(ctx, new Position(1, 1));

        assertThat(chain).isNotNull();
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

    private static ParserRuleContext ruleContext(int startLine, int startChar, int stopLine, int stopChar) {
        var ctx = new ParserRuleContext();
        ctx.start = token(startLine, startChar, "");
        ctx.stop  = token(stopLine, stopChar, "");
        return ctx;
    }

    private static CommonToken token(int line, int charPositionInLine, String text) {
        var token = new CommonToken(0, text);
        token.setLine(line + 1);
        token.setCharPositionInLine(charPositionInLine);
        return token;
    }

}
