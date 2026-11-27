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

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.document.SyntaxAnalysis;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.lsp.core.ParsedDocument;
import lombok.Getter;
import lombok.val;

/**
 * A SAPL document as the editor holds it: its tokens, its parse tree and its
 * problems. The engine reads the document, so the editor shows the problems
 * that the compiler reports, at the same places and in the same words.
 */
@Getter
public final class SAPLParsedDocument implements ParsedDocument {

    private final String            uri;
    private final String            content;
    private final CommonTokenStream tokenStream;
    private final SaplContext       saplParseTree;
    private final List<Diagnostic>  diagnostics;

    /**
     * Whether the syntax of the document has no problem. A document with a
     * problem of its rules only has a complete parse tree.
     */
    private final boolean syntaxValid;

    /**
     * Creates a new parsed document by reading the content.
     *
     * @param uri the document URI
     * @param content the document content
     */
    public SAPLParsedDocument(String uri, String content) {
        this.uri     = uri;
        this.content = content;

        // The editor reads untrusted text on every keystroke. The engine does not
        // parse a document that is too large or nested too deeply. The providers
        // still get an empty parse tree for it.
        val analysis = SyntaxAnalysis.of(content);
        val tree     = analysis.parseTree();
        this.tokenStream   = analysis.tokenStream();
        this.saplParseTree = tree != null ? tree : emptyParseTree();
        this.syntaxValid   = analysis.syntaxIsValid();
        this.diagnostics   = DocumentCompiler.parseDocument(analysis).diagnostics();
    }

    private static SaplContext emptyParseTree() {
        val emptyParser = new SAPLParser(new CommonTokenStream(new SAPLLexer(CharStreams.fromString(""))));
        emptyParser.removeErrorListeners();
        return emptyParser.sapl();
    }

    @Override
    public ParseTree getParseTree() {
        return saplParseTree;
    }

    /**
     * @return true if the document has a problem of its syntax or of its rules
     */
    @Override
    public boolean hasErrors() {
        return !diagnostics.isEmpty();
    }

    @Override
    public synchronized List<Token> getTokens() {
        // The shared CommonTokenStream is filled lazily and mutably. Serialize so
        // concurrent provider requests cannot fill it at the same time.
        tokenStream.fill();
        return tokenStream.getTokens();
    }

}
