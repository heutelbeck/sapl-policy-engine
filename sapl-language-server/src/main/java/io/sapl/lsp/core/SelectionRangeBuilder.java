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

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Builds selection range chains from ANTLR parse trees.
 * Walks from the innermost enclosing node to the root, building a linked
 * chain of selection ranges for smart expand/shrink selection.
 */
@UtilityClass
public class SelectionRangeBuilder {

    /**
     * Builds a selection range chain for the given position in the parse tree.
     *
     * @param root the parse tree root
     * @param position the cursor position (0-based line and character)
     * @return the innermost selection range with parent chain, or null if position
     * is outside the tree
     */
    public static SelectionRange buildSelectionRange(ParseTree root, Position position) {
        val chain = new ArrayList<ParserRuleContext>();
        collectEnclosingNodes(root, position, chain);
        if (chain.isEmpty()) {
            return null;
        }
        SelectionRange current = null;
        for (var i = chain.size() - 1; i >= 0; i--) {
            val ctx            = chain.get(i);
            val range          = rangeOf(ctx);
            val selectionRange = new SelectionRange(range, current);
            current = selectionRange;
        }
        return current;
    }

    private static void collectEnclosingNodes(ParseTree node, Position position, List<ParserRuleContext> chain) {
        if (!(node instanceof ParserRuleContext ctx)) {
            return;
        }
        if (!contains(ctx, position)) {
            return;
        }
        chain.add(ctx);
        for (var i = 0; i < ctx.getChildCount(); i++) {
            collectEnclosingNodes(ctx.getChild(i), position, chain);
        }
    }

    private static boolean contains(ParserRuleContext ctx, Position position) {
        if (ctx.getStart() == null || ctx.getStop() == null) {
            return false;
        }
        val startLine = ctx.getStart().getLine() - 1;
        val startChar = ctx.getStart().getCharPositionInLine();
        val stopLine  = ctx.getStop().getLine() - 1;
        val stopChar  = ctx.getStop().getCharPositionInLine() + ctx.getStop().getText().length();

        val line      = position.getLine();
        val character = position.getCharacter();

        if (line < startLine || line > stopLine) {
            return false;
        }
        if (line == startLine && character < startChar) {
            return false;
        }
        return line != stopLine || character <= stopChar;
    }

    private static Range rangeOf(ParserRuleContext ctx) {
        val start = new Position(ctx.getStart().getLine() - 1, ctx.getStart().getCharPositionInLine());
        val stop  = ctx.getStop();
        val end   = new Position(stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
        return new Range(start, end);
    }

}
