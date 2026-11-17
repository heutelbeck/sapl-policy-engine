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

import static io.sapl.compiler.util.StringsUtil.unquoteString;
import static io.sapl.compiler.util.TrojanSourceUtil.assertNoTrojanSourceCharacters;

@UtilityClass
public class DocumentCompiler {
    private static final String ERROR_AT_POSITION          = "line %d:%d %s";
    private static final String ERROR_COMPILATION_TOO_DEEP = "Compilation of SAPL document failed: expression tree is too deep.";
    private static final String ERROR_DOCUMENT_TOO_LARGE   = "Parsing of SAPL document failed: document exceeds the maximum size of %d bytes.";
    private static final String ERROR_LONE_SURROGATE       = "Incomplete surrogate pair. A string holds one half of a surrogate pair, which is not a character on its own. Add the second half, for example '\\uD83D\\uDE00', or remove it.";
    private static final String ERROR_NESTING_TOO_DEEP     = "Parsing of SAPL document failed: document nesting is too deep.";
    private static final String ERROR_PARSING_AST_NULL     = "Parsing of SAPL document failed: AST was null.";
    private static final String ERROR_PARSING_FAILED       = "Parsing of SAPL document failed: %s.";

    /**
     * Maximum size of a SAPL document the engine will compile. Shared with the
     * language server so the editor enforces the same bound as the compile path.
     */
    public static final int MAX_DOCUMENT_SIZE_BYTES = 2 * 1024 * 1024;

    /**
     * Maximum nesting depth of parentheses, brackets and braces in a SAPL
     * document. All three kinds count towards the same depth. A document nested
     * more deeply is rejected instead of being parsed. The bound keeps the
     * recursive passes over a document well within the stack of an ordinary
     * thread.
     */
    static final int MAX_NESTING_DEPTH = 32;

    private final SAPLValidator validator = new SAPLValidator();

    public static CompiledDocument compileDocument(String saplDocument, CompilationContext ctx) {
        val transformer = new AstTransformer();
        transformer.setContext(ctx.getPdpId(), ctx.getConfigurationId());
        val parsedDocument = parseDocument(saplDocument, transformer);
        if (parsedDocument.isInvalid()) {
            throw new SaplCompilerException(ERROR_PARSING_FAILED.formatted(parsedDocument.errors()));
        }
        if (parsedDocument.saplDocument() == null) {
            throw new SaplCompilerException(ERROR_PARSING_AST_NULL);
        }
        return compileDocument(parsedDocument.saplDocument(), ctx);
    }

    public Document parseDocument(String saplDefinition) {
        return parseDocument(saplDefinition, new AstTransformer());
    }

    // The transformer is fresh for every document. It carries per-document mutable
    // state (import map, schemas), so sharing one instance would let concurrent
    // compilations of different documents race and resolve names against another
    // document's imports.
    private Document parseDocument(String saplDefinition, AstTransformer transformer) {
        if (saplDefinition.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_SIZE_BYTES) {
            return rejectedDocument(saplDefinition, ERROR_DOCUMENT_TOO_LARGE.formatted(MAX_DOCUMENT_SIZE_BYTES));
        }
        assertNoTrojanSourceCharacters(saplDefinition);
        val result = parseWithErrors(saplDefinition);
        try {
            return validateAndTransform(saplDefinition, result, transformer);
        } catch (StackOverflowError e) {
            // Validation and transformation walk the parse tree recursively. Input that
            // nests deeply without brackets, such as a long chain of prefix operators,
            // can exhaust the stack there although the parser did not.
            return rejectedDocument(saplDefinition, ERROR_NESTING_TOO_DEEP);
        }
    }

    private Document validateAndTransform(String saplDefinition, ParseResult result, AstTransformer transformer) {
        val validationErrors = result.syntaxErrors().isEmpty() ? validator.validate(result.parseTree())
                : List.<ValidationError>of();

        SaplDocument ast = null;
        if (result.syntaxErrors().isEmpty() && validationErrors.isEmpty()) {
            try {
                ast = transformer.visitSapl(result.parseTree());
            } catch (SaplCompilerException e) {
                validationErrors.add(toValidationError(e));
            }
        }
        val errorMessage = buildErrorMessage(result.syntaxErrors(), validationErrors);
        return new Document(result.parseTree(), ast, saplDefinition, result.syntaxErrors(), validationErrors,
                errorMessage);
    }

    private ValidationError toValidationError(SaplCompilerException exception) {
        val location = exception.getLocation();
        if (location == null) {
            return new ValidationError(exception.getMessage(), 0, 0, "");
        }
        // Source locations count columns from one, validation errors from zero.
        return new ValidationError(exception.getMessage(), location.line(), location.column() - 1, "");
    }

    private Document rejectedDocument(String source, String message) {
        return new Document(null, null, source, List.of(message), List.of(), message);
    }

    private String buildErrorMessage(List<String> syntaxErrors, List<ValidationError> validationErrors) {
        if (syntaxErrors.isEmpty() && validationErrors.isEmpty()) {
            return "OK";
        }

        val messages = new ArrayList<String>();
        messages.addAll(syntaxErrors);
        messages.addAll(validationErrors.stream().map(ValidationError::toString).toList());

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
                syntaxErrors.add(ERROR_AT_POSITION.formatted(line, charPositionInLine, message));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        tokenStream.fill();
        val tokens = tokenStream.getTokens();
        assertNoTrojanSourceEscapes(tokens);
        syntaxErrors.addAll(loneSurrogateErrors(tokens));
        if (exceedsNestingLimit(tokens)) {
            syntaxErrors.add(ERROR_NESTING_TOO_DEEP);
            return new ParseResult(null, syntaxErrors);
        }

        SAPLParser.SaplContext tree = null;
        try {
            tree = parser.sapl();
        } catch (StackOverflowError e) {
            // Input that nests deeply without brackets, such as a long chain of prefix
            // operators, overflows the recursive-descent parser. Report it as a syntax
            // error.
            syntaxErrors.add(ERROR_NESTING_TOO_DEEP);
        }
        return new ParseResult(tree, syntaxErrors);
    }

    /**
     * A string escape can denote a bidirectional control character without the
     * character itself appearing in the source. Such an escape is rejected like
     * the character it denotes.
     */
    private void assertNoTrojanSourceEscapes(List<Token> tokens) {
        for (val token : tokens) {
            if (token.getType() == SAPLLexer.STRING) {
                assertNoTrojanSourceCharacters(unquoteString(token.getText()));
            }
        }
    }

    /**
     * A string escape can denote one half of a surrogate pair. On its own such
     * a half is no character and has no UTF-8 form: the text would change or
     * fail, depending on where it is written to.
     */
    private List<String> loneSurrogateErrors(List<Token> tokens) {
        return tokens.stream().filter(
                token -> token.getType() == SAPLLexer.STRING && holdsLoneSurrogate(unquoteString(token.getText())))
                .map(token -> ERROR_AT_POSITION.formatted(token.getLine(), token.getCharPositionInLine(),
                        ERROR_LONE_SURROGATE))
                .toList();
    }

    private boolean holdsLoneSurrogate(String text) {
        // A complete pair is one code point outside the range of the surrogates. Only
        // a half on its own stays a code point of that range.
        return text.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.SURROGATE);
    }

    private boolean exceedsNestingLimit(List<Token> tokens) {
        var depth = 0;
        for (val token : tokens) {
            // A closing bracket without an opening one must not lower the depth below
            // zero. Otherwise it would buy additional nesting for what follows.
            depth = switch (token.getType()) {
            case SAPLLexer.LPAREN, SAPLLexer.LBRACKET, SAPLLexer.LBRACE -> depth + 1;
            case SAPLLexer.RPAREN, SAPLLexer.RBRACKET, SAPLLexer.RBRACE -> Math.max(0, depth - 1);
            default                                                     -> depth;
            };
            if (depth > MAX_NESTING_DEPTH) {
                return true;
            }
        }
        return false;
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
