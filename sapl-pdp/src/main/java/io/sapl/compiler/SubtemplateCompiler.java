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

import io.sapl.api.model.*;
import io.sapl.grammar.antlr.SAPLParser.BasicExpressionContext;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import reactor.core.publisher.Flux;

/**
 * Compiles SAPL subtemplate expressions (:: operator) into optimized executable
 * representations.
 * <p>
 * Subtemplates apply template expressions to values, with implicit array
 * mapping. The template expression can reference the current value via @
 * (relative node).
 * <p>
 * Examples:
 * <ul>
 * <li>{@code { "name": "Alice" } :: { "newName": @.name }} - Object
 * transformation</li>
 * <li>{@code [ { "x": 1 }, { "x": 2 } ] :: { "double": @.x * 2 }} - Array
 * mapping</li>
 * <li>{@code [] :: { "foo": "bar" }} - Empty array stays empty</li>
 * </ul>
 */
@UtilityClass
public class SubtemplateCompiler {

    private static final String RUNTIME_ERROR_SUBTEMPLATE_STREAMING_COMPILE_TIME = "Subtemplate contains streaming operations, cannot be evaluated at compile time.";
    private static final String RUNTIME_ERROR_SUBTEMPLATE_STREAMING_RUNTIME      = "Subtemplate contains streaming operations, cannot be evaluated in this context.";

    /**
     * Compiles a subtemplate expression (:: operator).
     * <p>
     * Subtemplates apply template expressions to values:
     * <ul>
     * <li>Error values: propagate error</li>
     * <li>Undefined values: propagate undefined</li>
     * <li>Empty arrays: return empty array</li>
     * <li>Non-empty arrays: map template over each element</li>
     * <li>Other values: apply template with value as @ context</li>
     * </ul>
     *
     * @param parent
     * the expression to apply the template to
     * @param subtemplateExpression
     * the template expression context from the parser
     * @param context
     * the compilation context
     *
     * @return the compiled subtemplate expression
     */
    public CompiledExpression compileSubtemplate(CompiledExpression parent,
            BasicExpressionContext subtemplateExpression, CompilationContext context) {
        // Propagate errors and undefined
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }

        // Compile the subtemplate expression
        val compiledTemplate = ExpressionCompiler.compileBasicExpression(subtemplateExpression, context);

        // Check if template is subscription-scoped (uses variables/attributes, not
        // just @)
        val isTemplateSubscriptionScoped = compiledTemplate instanceof PureExpression pe && pe.isSubscriptionScoped();

        // Handle different parent expression types
        return switch (parent) {
        case Value value                   ->
            applySubtemplateToValue(subtemplateExpression, value, compiledTemplate, isTemplateSubscriptionScoped);
        case PureExpression pureParent     -> applySubtemplateToPureExpression(subtemplateExpression, pureParent,
                compiledTemplate, isTemplateSubscriptionScoped);
        case StreamExpression streamParent ->
            applySubtemplateToStreamExpression(subtemplateExpression, streamParent, compiledTemplate);
        };
    }

    /**
     * Applies a subtemplate to a constant value.
     * <p>
     * If the template is not subscription-scoped (only uses @), constant folding is
     * possible. Otherwise, must defer to runtime.
     */
    private CompiledExpression applySubtemplateToValue(ParserRuleContext astNode, Value parentValue,
            CompiledExpression compiledTemplate, boolean isTemplateSubscriptionScoped) {
        // Error/Undefined: already handled at top level
        if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
            return parentValue;
        }

        // If template is subscription-scoped, must defer to runtime
        if (isTemplateSubscriptionScoped) {
            return new PureExpression(ctx -> evaluateSubtemplateAtRuntime(astNode, parentValue, compiledTemplate, ctx),
                    true);
        }

        // Template only uses @ - can constant-fold
        if (parentValue instanceof ArrayValue arrayValue) {
            if (arrayValue.isEmpty()) {
                return arrayValue;
            }

            // Map template over each element at compile time
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = evaluateTemplateAtCompileTime(astNode, element, compiledTemplate);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }

        // Non-array: apply template directly at compile time
        return evaluateTemplateAtCompileTime(astNode, parentValue, compiledTemplate);
    }

    /**
     * Applies a subtemplate to a pure expression.
     */
    private CompiledExpression applySubtemplateToPureExpression(ParserRuleContext astNode, PureExpression pureParent,
            CompiledExpression compiledTemplate, boolean isTemplateSubscriptionScoped) {
        val isSubscriptionScoped = pureParent.isSubscriptionScoped() || isTemplateSubscriptionScoped;

        return new PureExpression(ctx -> {
            val parentValue = pureParent.evaluate(ctx);

            if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
                return parentValue;
            }

            return evaluateSubtemplateAtRuntime(astNode, parentValue, compiledTemplate, ctx);
        }, isSubscriptionScoped);
    }

    /**
     * Applies a subtemplate to a stream expression.
     */
    private CompiledExpression applySubtemplateToStreamExpression(ParserRuleContext astNode,
            StreamExpression streamParent, CompiledExpression compiledTemplate) {
        val resultStream = streamParent.stream()
                .flatMap(parentValue -> applyTemplateToValueInStream(astNode, parentValue, compiledTemplate));
        return new StreamExpression(resultStream);
    }

    /**
     * Evaluates subtemplate at runtime (when parent or template requires runtime
     * context).
     */
    private Value evaluateSubtemplateAtRuntime(ParserRuleContext astNode, Value parentValue,
            CompiledExpression compiledTemplate, EvaluationContext ctx) {
        if (parentValue instanceof ArrayValue arrayValue) {
            if (arrayValue.isEmpty()) {
                return arrayValue;
            }

            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = evaluateTemplateAtRuntime(astNode, element, compiledTemplate, ctx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }

        return evaluateTemplateAtRuntime(astNode, parentValue, compiledTemplate, ctx);
    }

    /**
     * Applies a template to a value within a stream context.
     */
    private Flux<Value> applyTemplateToValueInStream(ParserRuleContext astNode, Value parentValue,
            CompiledExpression compiledTemplate) {
        if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
            return Flux.just(parentValue);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return arrayValue.isEmpty() ? Flux.just(arrayValue)
                    : mapTemplateOverArrayInStream(astNode, arrayValue, compiledTemplate);
        }

        return evaluateTemplateInStreamContext(astNode, parentValue, compiledTemplate);
    }

    /**
     * Maps a template over each element of an array within a stream context.
     */
    private Flux<Value> mapTemplateOverArrayInStream(ParserRuleContext astNode, ArrayValue arrayValue,
            CompiledExpression compiledTemplate) {
        return Flux.deferContextual(ctx -> {
            val evaluationContext = ctx.get(EvaluationContext.class);
            val builder           = ArrayValue.builder();

            for (val element : arrayValue) {
                val result = evaluateTemplateAtRuntime(astNode, element, compiledTemplate, evaluationContext);
                if (result instanceof ErrorValue) {
                    return Flux.just(result);
                }
                builder.add(result);
            }

            return Flux.just(builder.build());
        });
    }

    /**
     * Evaluates a template with a non-array value in a stream context.
     */
    private Flux<Value> evaluateTemplateInStreamContext(ParserRuleContext astNode, Value parentValue,
            CompiledExpression compiledTemplate) {
        return Flux.deferContextual(ctx -> {
            val evaluationContext = ctx.get(EvaluationContext.class);
            val result            = evaluateTemplateAtRuntime(astNode, parentValue, compiledTemplate,
                    evaluationContext);
            return Flux.just(result);
        });
    }

    /**
     * Evaluates a template expression at compile time with a specific value as @.
     * <p>
     * For subtemplates, only @ is set (not # since there's no index/key concept in
     * subtemplate iteration).
     */
    private Value evaluateTemplateAtCompileTime(ParserRuleContext astNode, Value relativeNode,
            CompiledExpression compiledTemplate) {
        return switch (compiledTemplate) {
        case Value value                 -> value; // Template is constant, ignore relative node
        case PureExpression pureTemplate -> {
            val ctx = new EvaluationContext(null, null, null, null, null, null).withRelativeValue(relativeNode);
            yield pureTemplate.evaluate(ctx);
        }
        case StreamExpression ignored    ->
            Error.at(astNode, relativeNode.metadata(), RUNTIME_ERROR_SUBTEMPLATE_STREAMING_COMPILE_TIME);
        };
    }

    /**
     * Evaluates a template expression at runtime with a specific value as @.
     */
    private Value evaluateTemplateAtRuntime(ParserRuleContext astNode, Value relativeNode,
            CompiledExpression compiledTemplate, EvaluationContext ctx) {
        return switch (compiledTemplate) {
        case Value value                 -> value; // Template is constant, ignore relative node
        case PureExpression pureTemplate -> {
            val contextWithRelativeNode = ctx.withRelativeValue(relativeNode);
            yield pureTemplate.evaluate(contextWithRelativeNode);
        }
        case StreamExpression ignored    ->
            Error.at(astNode, relativeNode.metadata(), RUNTIME_ERROR_SUBTEMPLATE_STREAMING_RUNTIME);
        };
    }

}
