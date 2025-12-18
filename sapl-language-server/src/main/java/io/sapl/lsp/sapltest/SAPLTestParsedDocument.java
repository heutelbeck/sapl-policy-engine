/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import io.sapl.lsp.core.ParsedDocument;
import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.validation.SAPLTestValidator;
import lombok.Getter;

/**
 * Represents a parsed SAPLTest document with its AST and any parse errors.
 */
@Getter
public final class SAPLTestParsedDocument implements ParsedDocument {

    private final String                uri;
    private final String                content;
    private final CommonTokenStream     tokenStream;
    private final SaplTestContext       saplTestParseTree;
    private final List<ParseError>      parseErrors;
    private final List<ValidationError> validationErrors;

    /**
     * Creates a new parsed document by parsing the content.
     *
     * @param uri the document URI
     * @param content the document content
     */
    public SAPLTestParsedDocument(String uri, String content) {
        this.uri     = uri;
        this.content = content;

        var errors = new ArrayList<ParseError>();

        // Create lexer
        var charStream = CharStreams.fromString(content);
        var lexer      = new SAPLTestLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new ErrorCollector(errors));

        // Create parser
        this.tokenStream = new CommonTokenStream(lexer);
        var parser = new SAPLTestParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(new ErrorCollector(errors));

        // Parse
        this.saplTestParseTree = parser.saplTest();
        this.parseErrors       = List.copyOf(errors);

        // Semantic validation
        var validator            = new SAPLTestValidator();
        var testValidationErrors = validator.validate(saplTestParseTree);

        // Convert SAPLTest validation errors to generic ValidationError
        this.validationErrors = testValidationErrors.stream()
                .map(e -> new ValidationError(e.line(), e.charPositionInLine(), e.message(), e.offendingText()))
                .toList();
    }

    @Override
    public ParseTree getParseTree() {
        return saplTestParseTree;
    }

    /**
     * Gets the SAPLTest-specific parse tree.
     *
     * @return the SAPLTest parse tree
     */
    public SaplTestContext getSaplTestParseTree() {
        return saplTestParseTree;
    }

    @Override
    public boolean hasErrors() {
        return !parseErrors.isEmpty() || !validationErrors.isEmpty();
    }

    @Override
    public List<Token> getTokens() {
        tokenStream.fill();
        return tokenStream.getTokens();
    }

    /**
     * ANTLR error listener that collects errors into a list.
     */
    private static class ErrorCollector extends BaseErrorListener {

        private final List<ParseError> errors;

        ErrorCollector(List<ParseError> errors) {
            this.errors = errors;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String message, RecognitionException e) {
            var symbolText = offendingSymbol instanceof Token token ? token.getText() : "";
            errors.add(new ParseError(line, charPositionInLine, message, symbolText));
        }

    }

}
