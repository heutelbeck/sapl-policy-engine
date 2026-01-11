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
package io.sapl.util;

import java.util.Map;

import io.sapl.functions.libraries.StringFunctionLibrary;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.List;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Value;
import io.sapl.ast.Expression;
import io.sapl.ast.SaplDocument;
import io.sapl.ast.Statement;
import io.sapl.compiler.SAPLCompiler;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.model.Document;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import reactor.core.publisher.Flux;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.StatementContext;
import lombok.experimental.UtilityClass;

/**
 * Parses and compiles standalone SAPL expressions and statements.
 * Function/attribute names must be fully qualified (e.g., {@code time.now()}).
 */
@UtilityClass
public class CompilerUtil {

    public static final FunctionBroker  FUNCTION_BROKER;
    public static final AttributeBroker ATTRIBUTE_BROKER;
    public static final SAPLCompiler    COMPILER = new SAPLCompiler();

    static {
        var functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockFunctionLibrary.class);
        FUNCTION_BROKER = functionBroker;

        ATTRIBUTE_BROKER = new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                return Flux.just(Value.error("No PIP registered for: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    private static final StandaloneTransformer TRANSFORMER = new StandaloneTransformer();

    public static Expression parseExpression(String expressionSource) {
        var parser = createParser(expressionSource);
        return TRANSFORMER.expression(parser.expression());
    }

    public static Statement parseStatement(String statementSource) {
        var parser = createParser(statementSource);
        return TRANSFORMER.statement(parser.statement());
    }

    public static Document parseDocument(String documentSource) {
        return COMPILER.parseDocument(documentSource);
    }

    public static SaplDocument document(String documentSource) {
        return COMPILER.parseDocument(documentSource).saplDocument();
    }

    public static CompiledExpression compileExpression(String expressionSource) {
        return compileExpression(expressionSource, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static CompiledExpression compileExpression(String expressionSource, AttributeBroker attributeBroker) {
        return compileExpression(expressionSource, FUNCTION_BROKER, attributeBroker);
    }

    public static CompiledExpression compileExpression(String expressionSource, FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        var expression = parseExpression(expressionSource);
        var ctx        = new CompilationContext(functionBroker, attributeBroker);
        return ExpressionCompiler.compile(expression, ctx);
    }

    private static SAPLParser createParser(String source) {
        var charStream  = CharStreams.fromString(source);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        return parser;
    }

    private static class StandaloneTransformer extends AstTransformer {
        StandaloneTransformer() {
            initializeImportMap(Map.of());
        }

        Expression expression(ExpressionContext ctx) {
            return (Expression) visit(ctx);
        }

        Statement statement(StatementContext ctx) {
            return (Statement) visit(ctx);
        }
    }
}
