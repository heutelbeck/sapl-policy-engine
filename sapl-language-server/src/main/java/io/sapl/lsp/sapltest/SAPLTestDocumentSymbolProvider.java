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

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
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
            val symbol = buildRequirementSymbol(requirement);
            if (symbol != null) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private DocumentSymbol buildRequirementSymbol(RequirementContext ctx) {
        if (ctx == null || ctx.name == null) {
            return null;
        }
        val name     = stripQuotes(ctx.name.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Module, ProtocolPositions.rangeOfNode(ctx),
                rangeOfToken(ctx.name));
        val children = new ArrayList<DocumentSymbol>();
        for (val scenario : ctx.scenario()) {
            val child = buildScenarioSymbol(scenario);
            if (child != null) {
                children.add(child);
            }
        }
        symbol.setChildren(children);
        return symbol;
    }

    private DocumentSymbol buildScenarioSymbol(ScenarioContext ctx) {
        if (ctx == null || ctx.name == null) {
            return null;
        }
        val name = stripQuotes(ctx.name.getText());
        return new DocumentSymbol(name, SymbolKind.Function, ProtocolPositions.rangeOfNode(ctx),
                rangeOfToken(ctx.name));
    }

    private static Range rangeOfToken(Token token) {
        return ProtocolPositions.of(token).rangeOf(token);
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

}
