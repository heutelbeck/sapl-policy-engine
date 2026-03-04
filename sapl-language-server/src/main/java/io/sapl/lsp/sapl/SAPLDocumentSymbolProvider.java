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
            symbols.add(buildPolicySetSymbol(policySetElement.policySet()));
        } else if (policyElement instanceof PolicyOnlyElementContext policyOnlyElement) {
            symbols.add(buildPolicySymbol(policyOnlyElement.policy()));
        }
        return symbols;
    }

    private DocumentSymbol buildPolicySetSymbol(PolicySetContext ctx) {
        val name     = stripQuotes(ctx.saplName.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Module, rangeOf(ctx), rangeOfToken(ctx.saplName));
        val children = new ArrayList<DocumentSymbol>();
        for (val varDef : ctx.valueDefinition()) {
            children.add(buildVariableSymbol(varDef));
        }
        for (val policy : ctx.policy()) {
            children.add(buildPolicySymbol(policy));
        }
        symbol.setChildren(children);
        return symbol;
    }

    private DocumentSymbol buildPolicySymbol(PolicyContext ctx) {
        val name     = stripQuotes(ctx.saplName.getText());
        val symbol   = new DocumentSymbol(name, SymbolKind.Function, rangeOf(ctx), rangeOfToken(ctx.saplName));
        val children = new ArrayList<DocumentSymbol>();
        if (ctx.policyBody() != null) {
            for (val statement : ctx.policyBody().statements) {
                if (statement instanceof ValueDefinitionStatementContext varDefStmt) {
                    children.add(buildVariableSymbol(varDefStmt.valueDefinition()));
                }
            }
        }
        if (!children.isEmpty()) {
            symbol.setChildren(children);
        }
        return symbol;
    }

    private DocumentSymbol buildVariableSymbol(ValueDefinitionContext ctx) {
        val name = ctx.name.getText();
        return new DocumentSymbol(name, SymbolKind.Variable, rangeOf(ctx), rangeOf(ctx.name));
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
