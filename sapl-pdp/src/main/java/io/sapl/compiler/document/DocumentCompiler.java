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
package io.sapl.compiler.document;

import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SaplDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.validation.SAPLValidator;
import io.sapl.grammar.antlr.validation.ValidationError;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.sapl.compiler.util.TrojanSourceUtil.assertNoTrojanSourceCharacters;

@UtilityClass
public class DocumentCompiler {
    private static final String ERROR_COMPILATION_TOO_DEEP = "Compilation of SAPL document failed: expression tree is too deep.";
    private static final String ERROR_DOCUMENT_TOO_LARGE   = "Parsing of SAPL document failed: document exceeds the maximum size of %d bytes.";
    private static final String ERROR_NESTING_TOO_DEEP     = "Parsing of SAPL document failed: document nesting is too deep.";
    private static final String ERROR_PARSING_AST_NULL     = "Parsing of SAPL document failed: AST was null.";
    private static final String ERROR_PARSING_FAILED       = "Parsing of SAPL document failed: %s.";

    /**
     * Maximum size of a SAPL document the engine will compile. Shared with the
     * language server so the editor enforces the same bound as the compile path.
     */
    public static final int MAX_DOCUMENT_SIZE_BYTES = 2 * 1024 * 1024;

    private final SAPLValidator validator = new SAPLValidator();

    public static CompiledDocument compileDocument(String saplDocument, CompilationContext ctx) {
        val parsedDocument = parseDocument(saplDocument);
        if (parsedDocument.isInvalid()) {
            throw new SaplCompilerException(ERROR_PARSING_FAILED.formatted(parsedDocument.errors()));
        }
        if (parsedDocument.saplDocument() == null) {
            throw new SaplCompilerException(ERROR_PARSING_AST_NULL);
        }
        return compileDocument(parsedDocument.saplDocument(), ctx);
    }

    public Document parseDocument(String saplDefinition) {
        if (saplDefinition.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_SIZE_BYTES) {
            return rejectedDocument(saplDefinition, ERROR_DOCUMENT_TOO_LARGE.formatted(MAX_DOCUMENT_SIZE_BYTES));
        }
        assertNoTrojanSourceCharacters(saplDefinition);
        val result           = parseWithErrors(saplDefinition);
        val validationErrors = result.syntaxErrors().isEmpty() ? validator.validate(result.parseTree())
                : List.<ValidationError>of();

        SaplDocument ast          = null;
        Exception    astException = null;
        if (result.syntaxErrors().isEmpty() && validationErrors.isEmpty()) {
            try {
                // A fresh transformer per call. The transformer carries per-document
                // mutable state (import map, schemas), so sharing one instance would
                // let concurrent compilations of different documents race and resolve
                // names against another document's imports.
                ast = new AstTransformer().visitSapl(result.parseTree());
            } catch (SaplCompilerException e) {
                val location = e.getLocation();
                if (location != null) {
                    validationErrors.add(new ValidationError(e.getMessage(), location.line(), location.column(), ""));
                } else {
                    validationErrors.add(new ValidationError(e.getMessage(), 0, 0, ""));
                }
                astException = e;
            }
        }
        val errorMessage = buildErrorMessage(result.syntaxErrors(), validationErrors, astException);
        return new Document(result.parseTree(), ast, saplDefinition, result.syntaxErrors(), validationErrors,
                errorMessage);
    }

    private Document rejectedDocument(String source, String message) {
        return new Document(null, null, source, List.of(message), List.of(), message);
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

    private ParseResult parseWithErrors(String input) {
        val charStream  = CharStreams.fromString(input);
        val lexer       = new SAPLLexer(charStream);
        val tokenStream = new CommonTokenStream(lexer);
        val parser      = new SAPLParser(tokenStream);

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

        SAPLParser.SaplContext tree = null;
        try {
            tree = parser.sapl();
        } catch (StackOverflowError e) {
            // Deeply nested input overflows the recursive-descent parser. Report it as a
            // syntax error.
            syntaxErrors.add(ERROR_NESTING_TOO_DEEP);
        }
        return new ParseResult(tree, syntaxErrors);
    }

    private record ParseResult(SAPLParser.SaplContext parseTree, List<String> syntaxErrors) {}

    private static CompiledDocument compileDocument(SaplDocument saplDocument, CompilationContext ctx) {
        ctx.resetForNextDocument();
        try {
            return switch (saplDocument) {
            case Policy policy       -> PolicyCompiler.compilePolicy(policy, ctx);
            case PolicySet policySet -> PolicySetCompiler.compilePolicySet(policySet, ctx);
            };
        } catch (StackOverflowError e) {
            throw new SaplCompilerException(ERROR_COMPILATION_TOO_DEEP, e);
        }
    }
}
