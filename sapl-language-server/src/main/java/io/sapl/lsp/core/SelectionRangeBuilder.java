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
        if (!(root instanceof ParserRuleContext rootContext) || rootContext.getStart() == null) {
            return null;
        }
        val positions = ProtocolPositions.of(rootContext.getStart());
        if (!positions.holds(position)) {
            return null;
        }
        val chain = new ArrayList<ParserRuleContext>();
        collectEnclosingNodes(root, positions, positions.offsetOf(position), chain);
        if (chain.isEmpty()) {
            return null;
        }
        SelectionRange current = null;
        // Outermost first: each node becomes the parent of the next-inner, so the
        // returned head is the innermost range with its ancestors as parents.
        for (var i = 0; i < chain.size(); i++) {
            val ctx            = chain.get(i);
            val range          = positions.rangeOf(ctx);
            val selectionRange = new SelectionRange(range, current);
            current = selectionRange;
        }
        return current;
    }

    private static void collectEnclosingNodes(ParseTree node, ProtocolPositions positions, int offset,
            List<ParserRuleContext> chain) {
        if (!(node instanceof ParserRuleContext ctx) || !positions.encloses(ctx, offset)) {
            return;
        }
        chain.add(ctx);
        // Recurse into the first containing child only, keeping the chain a strict
        // ancestor path.
        for (var i = 0; i < ctx.getChildCount(); i++) {
            val before = chain.size();
            collectEnclosingNodes(ctx.getChild(i), positions, offset, chain);
            if (chain.size() > before) {
                return;
            }
        }
    }

}
