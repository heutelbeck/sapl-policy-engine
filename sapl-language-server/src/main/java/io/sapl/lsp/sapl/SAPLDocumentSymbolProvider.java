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
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides document symbols for SAPL documents.
 * Emits policy sets, policies, and variable definitions as symbols
 * for the editor outline view.
 */
class SAPLDocumentSymbolProvider {

    private static final Range ZERO_RANGE = new Range(new Position(0, 0), new Position(0, 0));

    List<DocumentSymbol> provideDocumentSymbols(ParsedDocument document) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return List.of();
        }
        val tree          = saplDocument.getSaplParseTree();
        val policyElement = tree.policyElement();
        if (policyElement == null) {
            return List.of();
        }
        val symbols = new ArrayList<DocumentSymbol>();
        if (policyElement instanceof PolicySetElementContext policySetElement) {
            addIfNotNull(symbols, buildPolicySetSymbol(policySetElement.policySet()));
        } else if (policyElement instanceof PolicyOnlyElementContext policyOnlyElement) {
            addIfNotNull(symbols, buildPolicySymbol(policyOnlyElement.policy()));
        }
        return symbols;
    }

    private DocumentSymbol buildPolicySetSymbol(PolicySetContext ctx) {
        if (ctx == null || ctx.saplName == null) {
            return null;
        }
        val name     = stripQuotes(ctx.saplName.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Module, rangeOf(ctx), rangeOfToken(ctx.saplName));
        val children = new ArrayList<DocumentSymbol>();
        for (val varDef : ctx.valueDefinition()) {
            addIfNotNull(children, buildVariableSymbol(varDef));
        }
        for (val policy : ctx.policy()) {
            addIfNotNull(children, buildPolicySymbol(policy));
        }
        symbol.setChildren(children);
        return symbol;
    }

    private DocumentSymbol buildPolicySymbol(PolicyContext ctx) {
        if (ctx == null || ctx.saplName == null) {
            return null;
        }
        val name     = stripQuotes(ctx.saplName.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Function, rangeOf(ctx), rangeOfToken(ctx.saplName));
        val children = new ArrayList<DocumentSymbol>();
        if (ctx.policyBody() != null) {
            for (val statement : ctx.policyBody().statements) {
                if (statement instanceof ValueDefinitionStatementContext varDefStmt) {
                    addIfNotNull(children, buildVariableSymbol(varDefStmt.valueDefinition()));
                }
            }
        }
        if (!children.isEmpty()) {
            symbol.setChildren(children);
        }
        return symbol;
    }

    private DocumentSymbol buildVariableSymbol(ValueDefinitionContext ctx) {
        if (ctx == null || ctx.name == null) {
            return null;
        }
        val name = ctx.name.getText();
        return new DocumentSymbol(name, SymbolKind.Variable, rangeOf(ctx), rangeOf(ctx.name));
    }

    private static <T> void addIfNotNull(List<T> list, T element) {
        if (element != null) {
            list.add(element);
        }
    }

    private static Range rangeOf(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) {
            return ZERO_RANGE;
        }
        val start = new Position(ctx.getStart().getLine() - 1, ctx.getStart().getCharPositionInLine());
        val stop  = ctx.getStop();
        return new Range(start, endPositionOf(stop));
    }

    private static Position endPositionOf(Token token) {
        val text        = token.getText();
        val startLine   = token.getLine() - 1;
        val startColumn = token.getCharPositionInLine();
        val lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) {
            return new Position(startLine, startColumn + text.length());
        }
        val newlineCount  = (int) text.chars().filter(c -> c == '\n').count();
        val trailingChars = text.length() - lastNewline - 1;
        return new Position(startLine + newlineCount, trailingChars);
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
