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
package io.sapl.compiler;

import io.sapl.ast.SaplDocument;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.ast.ImportResolver;
import io.sapl.compiler.model.Document;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.validation.SAPLValidator;
import io.sapl.grammar.antlr.validation.ValidationError;
import lombok.val;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

/**
 * Default implementation of SAPLCompiler using ANTLR4.
 * This parser provides a lightweight alternative to the Xtext-based parser,
 * with no EMF or Guice dependencies.
 */
public class SAPLCompiler {

    private final SAPLValidator  validator      = new SAPLValidator();
    private final AstTransformer astTransformer = new AstTransformer();

    public SaplContext parse(String saplDefinition) {
        val result = parseWithErrors(saplDefinition);
        if (!result.syntaxErrors().isEmpty()) {
            throw new SaplCompilerException("Syntax errors: " + String.join("; ", result.syntaxErrors()));
        }
        val validationErrors = validator.validate(result.parseTree());
        if (!validationErrors.isEmpty()) {
            throw new SaplCompilerException("Validation errors: "
                    + validationErrors.stream().map(ValidationError::toString).collect(Collectors.joining("; ")));
        }
        return result.parseTree();
    }

    public SaplContext parse(InputStream saplInputStream) {
        return parse(readInputStream(saplInputStream));
    }

    public Document parseDocument(String saplDefinition) {
        return parseDocument(null, saplDefinition);
    }

    public Document parseDocument(InputStream saplInputStream) {
        return parseDocument(null, saplInputStream);
    }

    public Document parseDocument(String id, String saplDefinition) {
        val result           = parseWithErrors(saplDefinition);
        val validationErrors = result.syntaxErrors().isEmpty() ? validator.validate(result.parseTree())
                : List.<ValidationError>of();

        val name       = extractDocumentName(result.parseTree());
        val documentId = id != null ? id : name;

        SaplDocument ast          = null;
        Exception    astException = null;
        if (result.syntaxErrors().isEmpty() && validationErrors.isEmpty()) {
            try {
                ast = ImportResolver.resolve((astTransformer.visitSapl(result.parseTree())));
            } catch (SaplCompilerException e) {
                astException = e;
            }
        }

        val errorMessage = buildErrorMessage(result.syntaxErrors(), validationErrors, astException);

        return new Document(documentId, name, result.parseTree(), ast, saplDefinition, result.syntaxErrors(),
                validationErrors, errorMessage);
    }

    public Document parseDocument(String id, InputStream saplInputStream) {
        return parseDocument(id, readInputStream(saplInputStream));
    }

    private ParseResult parseWithErrors(String input) {
        val charStream  = CharStreams.fromString(input);
        val lexer       = new SAPLLexer(charStream);
        val tokenStream = new CommonTokenStream(lexer);
        val parser      = new io.sapl.grammar.antlr.SAPLParser(tokenStream);

        val syntaxErrors = new ArrayList<String>();

        val errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String message, RecognitionException exception) {
                syntaxErrors.add("line %d:%d %s".formatted(line, charPositionInLine, message));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        val tree = parser.sapl();
        return new ParseResult(tree, syntaxErrors);
    }

    private String extractDocumentName(SaplContext sapl) {
        if (sapl == null) {
            return null;
        }
        return switch (sapl.policyElement()) {
        case PolicyOnlyElementContext p when p.policy().saplName != null     ->
            unquoteString(p.policy().saplName.getText());
        case PolicySetElementContext ps when ps.policySet().saplName != null ->
            unquoteString(ps.policySet().saplName.getText());
        case null, default                                                   -> null;
        };
    }

    private String buildErrorMessage(List<String> syntaxErrors, List<ValidationError> validationErrors,
            Exception astException) {
        if (syntaxErrors.isEmpty() && validationErrors.isEmpty()) {
            return "OK";
        }

        val messages = new ArrayList<String>();
        messages.addAll(syntaxErrors);
        messages.addAll(validationErrors.stream().map(ValidationError::toString).toList());
        if (astException != null) {
            messages.add(astException.getMessage());
        }

        return String.join("; ", messages);
    }

    private String readInputStream(InputStream inputStream) {
        try {
            val bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException exception) {
            throw new SaplCompilerException("Failed to read input stream", exception);
        }
    }

    private record ParseResult(SaplContext parseTree, List<String> syntaxErrors) {}

}
