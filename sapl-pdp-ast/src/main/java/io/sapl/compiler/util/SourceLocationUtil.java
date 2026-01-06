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
package io.sapl.compiler.util;

import io.sapl.api.model.SourceLocation;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

/**
 * Utility class for extracting source location information from ANTLR parse
 * tree nodes.
 */
@UtilityClass
public class SourceLocationUtil {

    /**
     * Extracts the source location from an ANTLR parse tree context.
     *
     * @param context the parse tree context to extract location from (may be null)
     * @return the source location, or null if the context is null or has no
     * location information
     */
    public static SourceLocation fromContext(ParserRuleContext context) {
        if (context == null) {
            return null;
        }

        val startToken = context.getStart();
        val stopToken  = context.getStop();

        if (startToken == null) {
            return null;
        }

        val start     = startToken.getStartIndex();
        val end       = stopToken != null ? stopToken.getStopIndex() + 1 : start + startToken.getText().length();
        val line      = startToken.getLine();
        val column    = startToken.getCharPositionInLine() + 1;
        val endLine   = stopToken != null ? stopToken.getLine() : line;
        val endColumn = stopToken != null ? stopToken.getCharPositionInLine() + stopToken.getText().length() + 1
                : column + startToken.getText().length();

        val documentSource = getDocumentSource(context);
        val documentName   = getDocumentName(context);

        return new SourceLocation(documentName, documentSource, start, end, line, column, endLine, endColumn);
    }

    /**
     * Extracts the source location from an ANTLR token.
     *
     * @param token the token to extract location from (may be null)
     * @param context the containing parse tree context for document information
     * @return the source location, or null if the token is null
     */
    public static SourceLocation fromToken(Token token, ParserRuleContext context) {
        if (token == null) {
            return null;
        }

        val start     = token.getStartIndex();
        val end       = token.getStopIndex() + 1;
        val line      = token.getLine();
        val column    = token.getCharPositionInLine() + 1;
        val endColumn = column + token.getText().length();

        val documentSource = context != null ? getDocumentSource(context) : null;
        val documentName   = context != null ? getDocumentName(context) : null;

        return new SourceLocation(documentName, documentSource, start, end, line, column, line, endColumn);
    }

    /**
     * Gets the full document source from the parse tree root.
     */
    private static String getDocumentSource(ParserRuleContext context) {
        val root = findRoot(context);
        if (root == null || root.getStart() == null) {
            return null;
        }
        val inputStream = root.getStart().getInputStream();
        if (inputStream == null) {
            return null;
        }
        return inputStream.getText(new org.antlr.v4.runtime.misc.Interval(0, inputStream.size() - 1));
    }

    /**
     * Gets the document name from the SAPL policy/policy-set name.
     */
    private static String getDocumentName(ParserRuleContext context) {
        val root = findRoot(context);
        if (!(root instanceof SaplContext saplContext)) {
            return null;
        }

        return switch (saplContext.policyElement()) {
        case PolicyOnlyElementContext p when p.policy().saplName != null     ->
            unquoteString(p.policy().saplName.getText());
        case PolicySetElementContext ps when ps.policySet().saplName != null ->
            unquoteString(ps.policySet().saplName.getText());
        case null, default                                                   -> null;
        };
    }

    /**
     * Finds the root of the parse tree.
     */
    private static ParserRuleContext findRoot(ParserRuleContext context) {
        if (context == null) {
            return null;
        }
        ParseTree current = context;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return (ParserRuleContext) current;
    }

}
