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
package io.sapl.lsp.sapltest;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;

import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides folding ranges for SAPLTest documents.
 * Emits foldable regions for requirements, scenarios, and block comments.
 */
class SAPLTestFoldingRangeProvider {

    List<FoldingRange> provideFoldingRanges(ParsedDocument document) {
        if (!(document instanceof SAPLTestParsedDocument saplTestDocument)) {
            return List.of();
        }
        val ranges = new ArrayList<FoldingRange>();
        addCommentFoldingRanges(saplTestDocument, ranges);
        addStructuralFoldingRanges(saplTestDocument, ranges);
        return ranges;
    }

    private void addCommentFoldingRanges(SAPLTestParsedDocument document, List<FoldingRange> ranges) {
        val tokenStream = document.getTokenStream();
        for (var i = 0; i < tokenStream.size(); i++) {
            val token = tokenStream.get(i);
            if (token.getType() == SAPLTestLexer.ML_COMMENT) {
                val startLine = token.getLine() - 1;
                val text      = token.getText();
                val lineCount = text.split("\n", -1).length - 1;
                if (lineCount > 0) {
                    val range = new FoldingRange(startLine, startLine + lineCount);
                    range.setKind(FoldingRangeKind.Comment);
                    ranges.add(range);
                }
            }
        }
    }

    private void addStructuralFoldingRanges(SAPLTestParsedDocument document, List<FoldingRange> ranges) {
        val tree = document.getSaplTestParseTree();
        for (val requirement : tree.requirement()) {
            addRangeIfMultiLine(requirement, ranges);
            for (val scenario : requirement.scenario()) {
                addRangeIfMultiLine(scenario, ranges);
            }
        }
    }

    private void addRangeIfMultiLine(ParserRuleContext ctx, List<FoldingRange> ranges) {
        val startLine = ctx.getStart().getLine() - 1;
        val endLine   = ctx.getStop().getLine() - 1;
        if (endLine > startLine) {
            ranges.add(new FoldingRange(startLine, endLine));
        }
    }

}
