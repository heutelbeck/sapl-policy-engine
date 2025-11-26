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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.grammar.sapl.Expression;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Compiles SAPL subtemplate expressions (:: operator) into optimized executable
 * representations.
 * <p>
 * Subtemplates apply template expressions to values, with implicit array
 * mapping. The template expression can reference
 * the current value via @ (relative node).
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

    private static final String ERROR_SUBTEMPLATE_STREAMING_COMPILE_TIME = "Subtemplate contains streaming operations, cannot be evaluated at compile time.";
    private static final String ERROR_SUBTEMPLATE_STREAMING_RUNTIME      = "Subtemplate contains streaming operations, cannot be evaluated in this context.";
    public static final String  COMPILE_TIME_ID_PLACEHOLDER              = "compile-time";

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
     * the template expression to apply
     * @param context
     * the compilation context
     *
     * @return the compiled subtemplate expression
     */
    public CompiledExpression compileSubtemplate(CompiledExpression parent, Expression subtemplateExpression,
            CompilationContext context) {
        // Propagate errors and undefined
        if (parent instanceof ErrorValue || parent instanceof UndefinedValue) {
            return parent;
        }

        // Compile the subtemplate expression
        val compiledTemplate = ExpressionCompiler.compileExpression(subtemplateExpression, context);

        // Handle different parent expression types
        return switch (parent) {
        case Value value                   -> applySubtemplateToValue(value, compiledTemplate);
        case PureExpression pureParent     -> applySubtemplateToPureExpression(pureParent, compiledTemplate);
        case StreamExpression streamParent -> applySubtemplateToStreamExpression(streamParent, compiledTemplate);
        };
    }

    /**
     * Applies a subtemplate to a constant value.
     * <p>
     * Handles implicit array mapping for array values.
     *
     * @param parentValue
     * the value to apply the template to
     * @param compiledTemplate
     * the compiled template expression
     *
     * @return the result of applying the template
     */
    private CompiledExpression applySubtemplateToValue(Value parentValue, CompiledExpression compiledTemplate) {
        // Error/Undefined: already handled at top level
        if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
            return parentValue;
        }

        // Array: implicit array mapping
        if (parentValue instanceof ArrayValue arrayValue) {
            // Empty array returns empty array
            if (arrayValue.isEmpty()) {
                return arrayValue;
            }

            // Map template over each element
            val builder = ArrayValue.builder();
            for (val element : arrayValue) {
                val result = evaluateTemplateWithRelativeNode(element, compiledTemplate);

                if (result instanceof ErrorValue) {
                    return result; // Propagate errors
                }

                builder.add(result);
            }
            return builder.build();
        }

        // Non-array: apply template directly
        return evaluateTemplateWithRelativeNode(parentValue, compiledTemplate);
    }

    /**
     * Applies a subtemplate to a pure expression.
     * <p>
     * Creates a PureExpression that evaluates the parent and applies the template.
     *
     * @param pureParent
     * the pure expression parent
     * @param compiledTemplate
     * the compiled template expression
     *
     * @return a PureExpression applying the subtemplate
     */
    private CompiledExpression applySubtemplateToPureExpression(PureExpression pureParent,
            CompiledExpression compiledTemplate) {
        return new PureExpression(ctx -> {
            val parentValue = pureParent.evaluate(ctx);

            // Propagate errors/undefined
            if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
                return parentValue;
            }

            // Handle arrays
            if (parentValue instanceof ArrayValue arrayValue) {
                if (arrayValue.isEmpty()) {
                    return arrayValue;
                }

                val builder = ArrayValue.builder();
                for (val element : arrayValue) {
                    val result = evaluateTemplateWithRelativeNodeInContext(element, compiledTemplate, ctx);

                    if (result instanceof ErrorValue) {
                        return result;
                    }

                    builder.add(result);
                }
                return builder.build();
            }

            // Non-array
            return evaluateTemplateWithRelativeNodeInContext(parentValue, compiledTemplate, ctx);
        }, pureParent.isSubscriptionScoped());
    }

    /**
     * Applies a subtemplate to a stream expression.
     * <p>
     * Creates a StreamExpression that applies the template to each emitted value.
     *
     * @param streamParent
     * the stream expression parent
     * @param compiledTemplate
     * the compiled template expression
     *
     * @return a StreamExpression applying the subtemplate
     */
    private CompiledExpression applySubtemplateToStreamExpression(StreamExpression streamParent,
            CompiledExpression compiledTemplate) {
        val resultStream = streamParent.stream()
                .flatMap(parentValue -> applyTemplateToValueInStream(parentValue, compiledTemplate));
        return new StreamExpression(resultStream);
    }

    /**
     * Applies a template to a value within a stream context.
     * Handles error/undefined propagation, empty arrays, array mapping, and
     * non-array values.
     */
    private Flux<Value> applyTemplateToValueInStream(Value parentValue, CompiledExpression compiledTemplate) {
        if (parentValue instanceof ErrorValue || parentValue instanceof UndefinedValue) {
            return Flux.just(parentValue);
        }

        if (parentValue instanceof ArrayValue arrayValue) {
            return arrayValue.isEmpty() ? Flux.just(arrayValue)
                    : mapTemplateOverArrayInStream(arrayValue, compiledTemplate);
        }

        return evaluateTemplateInStreamContext(parentValue, compiledTemplate);
    }

    /**
     * Maps a template over each element of an array within a stream context.
     */
    private Flux<Value> mapTemplateOverArrayInStream(ArrayValue arrayValue, CompiledExpression compiledTemplate) {
        return Flux.deferContextual(ctx -> {
            val evaluationContext = ctx.get(EvaluationContext.class);
            val builder           = ArrayValue.builder();

            for (val element : arrayValue) {
                val result = evaluateTemplateWithRelativeNodeInContext(element, compiledTemplate, evaluationContext);
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
    private Flux<Value> evaluateTemplateInStreamContext(Value parentValue, CompiledExpression compiledTemplate) {
        return Flux.deferContextual(ctx -> {
            val evaluationContext = ctx.get(EvaluationContext.class);
            val result            = evaluateTemplateWithRelativeNodeInContext(parentValue, compiledTemplate,
                    evaluationContext);
            return Flux.just(result);
        });
    }

    /**
     * Evaluates a template expression with a specific value as the relative node
     * (@).
     * <p>
     * This is for compile-time evaluation (when both parent and template are
     * Values).
     *
     * @param relativeNode
     * the value to use as @ in the template
     * @param compiledTemplate
     * the compiled template expression
     *
     * @return the evaluated result
     */
    private Value evaluateTemplateWithRelativeNode(Value relativeNode, CompiledExpression compiledTemplate) {
        return switch (compiledTemplate) {
        case Value value                 -> value; // Template is constant, ignore relative node
        case PureExpression pureTemplate -> {
            // Create a minimal evaluation context with the relative node
            val evaluationContext = new EvaluationContext(COMPILE_TIME_ID_PLACEHOLDER, COMPILE_TIME_ID_PLACEHOLDER,
                    COMPILE_TIME_ID_PLACEHOLDER, null, null, null).withRelativeValue(relativeNode);
            yield pureTemplate.evaluate(evaluationContext);
        }
        case StreamExpression ignored    -> Value.error(ERROR_SUBTEMPLATE_STREAMING_COMPILE_TIME);
        };
    }

    /**
     * Evaluates a template expression with a specific value as the relative node
     * (@) in an existing evaluation context.
     * <p>
     * This is for runtime evaluation within an existing context.
     *
     * @param relativeNode
     * the value to use as @ in the template
     * @param compiledTemplate
     * the compiled template expression
     * @param ctx
     * the evaluation context
     *
     * @return the evaluated result
     */
    private Value evaluateTemplateWithRelativeNodeInContext(Value relativeNode, CompiledExpression compiledTemplate,
            EvaluationContext ctx) {
        return switch (compiledTemplate) {
        case Value value                 -> value; // Template is constant, ignore relative node
        case PureExpression pureTemplate -> {
            // Create new context with the relative node (EvaluationContext is immutable)
            val contextWithRelativeNode = ctx.withRelativeValue(relativeNode);
            yield pureTemplate.evaluate(contextWithRelativeNode);
        }
        case StreamExpression ignored    -> Value.error(ERROR_SUBTEMPLATE_STREAMING_RUNTIME);
        };
    }
}
