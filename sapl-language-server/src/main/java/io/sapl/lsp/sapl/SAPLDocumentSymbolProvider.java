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

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.lsp.core.ParsedDocument;
import io.sapl.lsp.core.ProtocolPositions;
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
        val symbol   = new DocumentSymbol(name, SymbolKind.Module, ProtocolPositions.rangeOfNode(ctx),
                rangeOfToken(ctx.saplName));
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
        val symbol   = new DocumentSymbol(name, SymbolKind.Function, ProtocolPositions.rangeOfNode(ctx),
                rangeOfToken(ctx.saplName));
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
        return new DocumentSymbol(name, SymbolKind.Variable, ProtocolPositions.rangeOfNode(ctx),
                ProtocolPositions.rangeOfNode(ctx.name));
    }

    private static <T> void addIfNotNull(List<T> list, T element) {
        if (element != null) {
            list.add(element);
        }
    }

    private static Range rangeOfToken(Token token) {
        return ProtocolPositions.of(token).rangeOf(token);
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            val name = text.substring(1, text.length() - 1);
            return name.isBlank() ? text : name;
        }
        return text;
    }

}
