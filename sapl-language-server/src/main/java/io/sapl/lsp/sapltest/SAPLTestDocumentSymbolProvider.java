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
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides document symbols for SAPLTest documents.
 * Emits requirements and scenarios as symbols for the editor outline view.
 */
class SAPLTestDocumentSymbolProvider {

    List<DocumentSymbol> provideDocumentSymbols(ParsedDocument document) {
        if (!(document instanceof SAPLTestParsedDocument saplTestDocument)) {
            return List.of();
        }
        val tree    = saplTestDocument.getSaplTestParseTree();
        val symbols = new ArrayList<DocumentSymbol>();
        for (val requirement : tree.requirement()) {
            symbols.add(buildRequirementSymbol(requirement));
        }
        return symbols;
    }

    private DocumentSymbol buildRequirementSymbol(RequirementContext ctx) {
        val name     = stripQuotes(ctx.name.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Module, rangeOf(ctx), rangeOfToken(ctx.name));
        val children = new ArrayList<DocumentSymbol>();
        for (val scenario : ctx.scenario()) {
            children.add(buildScenarioSymbol(scenario));
        }
        symbol.setChildren(children);
        return symbol;
    }

    private DocumentSymbol buildScenarioSymbol(ScenarioContext ctx) {
        val name = stripQuotes(ctx.name.getText());
        return new DocumentSymbol(name, SymbolKind.Function, rangeOf(ctx), rangeOfToken(ctx.name));
    }

    private static Range rangeOf(ParserRuleContext ctx) {
        val start = new Position(ctx.getStart().getLine() - 1, ctx.getStart().getCharPositionInLine());
        val stop  = ctx.getStop();
        val end   = new Position(stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
        return new Range(start, end);
    }

    private static Range rangeOfToken(Token token) {
        val start = new Position(token.getLine() - 1, token.getCharPositionInLine());
        val end   = new Position(token.getLine() - 1, token.getCharPositionInLine() + token.getText().length());
        return new Range(start, end);
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

}
