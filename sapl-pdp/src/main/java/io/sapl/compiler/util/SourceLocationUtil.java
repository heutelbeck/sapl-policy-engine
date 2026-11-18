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

import io.sapl.api.model.DocumentLines;
import io.sapl.api.model.SourceLocation;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

/**
 * Utility class for extracting source location information from ANTLR
 * parse
 * tree nodes. ANTLR indexes the document in code points and ends lines only at
 * a line feed. A source location instead holds offsets into the document
 * string and the lines and columns that {@link DocumentLines} counts.
 */
@UtilityClass
public class SourceLocationUtil {

    // ANTLR getText copies the whole document per call, once per AST node, and
    // the lines of the document are counted in one pass over it. Share one copy
    // and one count per parse.
    private static final Map<CharStream, DocumentLines> DOCUMENT_LINES_BY_STREAM = Collections
            .synchronizedMap(new WeakHashMap<>());

    /**
     * Extracts the source location from an ANTLR parse tree context.
     *
     * @param context the parse tree context to extract location from (may be null)
     * @return the source location, or null if the context is null or was not
     * parsed from a document
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

        val lines = documentLinesOf(startToken);
        if (lines == null) {
            return null;
        }

        val start = lines.offsetOfCodePoint(startToken.getStartIndex());
        val end   = stopToken != null ? lines.offsetOfCodePoint(stopToken.getStopIndex() + 1)
                : start + startToken.getText().length();

        val documentName = getDocumentName(context);

        return new SourceLocation(documentName, lines.text(), start, end, lines.lineOf(start), lines.columnOf(start),
                lines.lineOf(end), lines.columnOf(end));
    }

    /**
     * Gets the lines of the document a token was read from. They are counted once
     * per parse and shared by every caller.
     *
     * @param token a token
     * @return the lines of its document, or null for a token that was not read
     * from a document
     */
    public static DocumentLines documentLinesOf(Token token) {
        val inputStream = token.getInputStream();
        if (inputStream == null) {
            return null;
        }
        return DOCUMENT_LINES_BY_STREAM.computeIfAbsent(inputStream,
                stream -> DocumentLines.of(stream.getText(new Interval(0, stream.size() - 1))));
    }

    /**
     * Gets the document name from the SAPL policy/policy-set name. For a node
     * inside a policy set, the name is the responsible child policy qualified
     * by the enclosing set as {@code setname->policyname}, so attribute reads
     * are attributed to the individual policy rather than the whole set.
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
            policySetScope(unquoteString(ps.policySet().saplName.getText()), context);
        case null, default                                                   -> null;
        };
    }

    /**
     * Renders the document name for a node inside a policy set. If the node lies
     * within a specific child policy, the name is qualified as
     * {@code setname->policyname}. Otherwise (set-level nodes such as the target
     * expression or set value definitions) the plain set name is returned.
     */
    private static String policySetScope(String setName, ParserRuleContext context) {
        val policy = enclosingPolicy(context);
        if (policy == null || policy.saplName == null) {
            return setName;
        }
        return setName + "->" + unquoteString(policy.saplName.getText());
    }

    /**
     * Walks up from the given node to the nearest enclosing policy context, or
     * null if the node is not contained in a policy.
     */
    private static PolicyContext enclosingPolicy(ParserRuleContext context) {
        ParseTree current = context;
        while (current != null) {
            if (current instanceof PolicyContext policy) {
                return policy;
            }
            current = current.getParent();
        }
        return null;
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
