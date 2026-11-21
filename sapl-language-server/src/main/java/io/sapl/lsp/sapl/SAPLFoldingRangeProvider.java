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
package io.sapl.lsp.sapl;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
import lombok.val;

/**
 * Provides folding ranges for SAPL documents.
 * Emits foldable regions for policy sets, policies, and block comments.
 */
class SAPLFoldingRangeProvider {

    List<FoldingRange> provideFoldingRanges(ParsedDocument document) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return List.of();
        }
        val ranges = new ArrayList<FoldingRange>();
        addCommentFoldingRanges(saplDocument, ranges);
        addStructuralFoldingRanges(saplDocument, ranges);
        return ranges;
    }

    private void addCommentFoldingRanges(SAPLParsedDocument document, List<FoldingRange> ranges) {
        val tokenStream = document.getTokenStream();
        for (var i = 0; i < tokenStream.size(); i++) {
            val token = tokenStream.get(i);
            if (token.getType() == SAPLLexer.ML_COMMENT) {
                val positions = ProtocolPositions.of(token);
                val startLine = positions.lineOf(token);
                val endLine   = positions.lineOf(positions.endOf(token));
                if (endLine > startLine) {
                    val range = new FoldingRange(startLine, endLine);
                    range.setKind(FoldingRangeKind.Comment);
                    ranges.add(range);
                }
            }
        }
    }

    private void addStructuralFoldingRanges(SAPLParsedDocument document, List<FoldingRange> ranges) {
        val tree          = document.getSaplParseTree();
        val policyElement = tree.policyElement();
        if (policyElement == null) {
            return;
        }
        if (policyElement instanceof PolicySetElementContext policySetElement) {
            val policySet = policySetElement.policySet();
            addRangeIfMultiLine(policySet, ranges);
            for (val policy : policySet.policy()) {
                addRangeIfMultiLine(policy, ranges);
            }
        } else if (policyElement instanceof PolicyOnlyElementContext policyOnlyElement) {
            addRangeIfMultiLine(policyOnlyElement.policy(), ranges);
        }
    }

    private void addRangeIfMultiLine(ParserRuleContext ctx, List<FoldingRange> ranges) {
        val positions = ProtocolPositions.of(ctx.getStart());
        val startLine = positions.lineOf(ctx.getStart());
        val endLine   = positions.lineOf(ctx.getStop());
        if (endLine > startLine) {
            ranges.add(new FoldingRange(startLine, endLine));
        }
    }

}
