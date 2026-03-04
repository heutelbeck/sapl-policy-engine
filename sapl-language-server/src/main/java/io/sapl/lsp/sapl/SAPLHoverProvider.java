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
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

import io.sapl.api.documentation.EntryDocumentation;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.grammar.antlr.SAPLParser.AttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicFunctionContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderStepContext;
import io.sapl.lsp.configuration.ConfigurationManager;
import io.sapl.lsp.core.ParsedDocument;
import lombok.val;

/**
 * Provides hover information for SAPL documents.
 * Shows documentation for functions and attributes when hovering.
 */
class SAPLHoverProvider {

    Hover provideHover(ParsedDocument document, Position position, ConfigurationManager configurationManager) {
        if (!(document instanceof SAPLParsedDocument saplDocument)) {
            return null;
        }
        val tree           = saplDocument.getSaplParseTree();
        val enclosingNodes = new ArrayList<ParserRuleContext>();
        collectEnclosingNodes(tree, position, enclosingNodes);

        val config = configurationManager.getConfigurationForUri(document.getUri());
        val bundle = config.documentationBundle();

        for (var i = enclosingNodes.size() - 1; i >= 0; i--) {
            val node = enclosingNodes.get(i);
            if (node instanceof FunctionIdentifierContext funcId) {
                val qualifiedName = extractQualifiedName(funcId);
                val parent        = funcId.getParent();
                if (parent instanceof BasicFunctionContext) {
                    return findFunctionHover(qualifiedName, bundle.functionLibraries());
                }
                if (parent instanceof BasicEnvironmentAttributeContext
                        || parent instanceof BasicEnvironmentHeadAttributeContext
                        || parent instanceof AttributeFinderStepContext
                        || parent instanceof HeadAttributeFinderStepContext) {
                    return findAttributeHover(qualifiedName, bundle.policyInformationPoints());
                }
                return findFunctionHover(qualifiedName, bundle.functionLibraries());
            }
        }
        return null;
    }

    private Hover findFunctionHover(String qualifiedName, List<LibraryDocumentation> libraries) {
        val parts = splitQualifiedName(qualifiedName);
        if (parts == null) {
            return null;
        }
        for (val library : libraries) {
            if (library.name().equals(parts[0])) {
                val entry = library.findEntry(parts[1]);
                if (entry != null && entry.type() == EntryType.FUNCTION) {
                    return createHover(library, entry);
                }
            }
        }
        return null;
    }

    private Hover findAttributeHover(String qualifiedName, List<LibraryDocumentation> libraries) {
        val parts = splitQualifiedName(qualifiedName);
        if (parts == null) {
            return null;
        }
        for (val library : libraries) {
            if (library.name().equals(parts[0])) {
                val entry = library.findEntry(parts[1]);
                if (entry != null) {
                    return createHover(library, entry);
                }
            }
        }
        return null;
    }

    private Hover createHover(LibraryDocumentation library, EntryDocumentation entry) {
        val sb = new StringBuilder();
        sb.append("**").append(library.name()).append('.').append(entry.name()).append("**\n\n");
        if (entry.documentation() != null && !entry.documentation().isEmpty()) {
            sb.append(entry.documentation());
        }
        val content = new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
        return new Hover(content);
    }

    private String extractQualifiedName(FunctionIdentifierContext ctx) {
        val sb = new StringBuilder();
        for (var i = 0; i < ctx.idFragment.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(ctx.idFragment.get(i).getText());
        }
        return sb.toString();
    }

    private String[] splitQualifiedName(String qualifiedName) {
        val lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return new String[] { qualifiedName.substring(0, lastDot), qualifiedName.substring(lastDot + 1) };
    }

    private void collectEnclosingNodes(ParseTree node, Position position, List<ParserRuleContext> chain) {
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

    private boolean contains(ParserRuleContext ctx, Position position) {
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

}
