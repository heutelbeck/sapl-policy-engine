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
import io.sapl.ast.BinaryOperator;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Compiler for SAPL subtemplate expressions (:: operator).
 * <p>
 * Subtemplates apply a template expression to values, with implicit
 * array/object
 * mapping. The template can reference:
 * <ul>
 * <li>{@code @} - the current element value</li>
 * <li>{@code #} - the current index (for arrays) or key (for objects)</li>
 * </ul>
 * <p>
 * Examples:
 * <ul>
 * <li>{@code [1, 2, 3] :: @ * 2} results in {@code [2, 4, 6]}</li>
 * <li>{@code [a, b, c] :: #} results in {@code [0, 1, 2]}</li>
 * <li>{@code [10, 20] :: @ + #} results in {@code [10, 21]}</li>
 * <li>{@code {"x": 1, "y": 2} :: #} results in {@code ["x", "y"]}</li>
 * <li>{@code 5 :: @ * 2} results in {@code 10}</li>
 * </ul>
 */
public class SubtemplateCompiler {

    private static final String ERROR_STREAMING_TEMPLATE = "Subtemplate cannot contain streaming expressions.";

    public CompiledExpression compile(BinaryOperator binaryOperation, CompilationContext ctx) {
        val parent   = ExpressionCompiler.compile(binaryOperation.left(), ctx);
        val template = ExpressionCompiler.compile(binaryOperation.right(), ctx);
        val loc      = binaryOperation.location();

        // Propagate errors
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (template instanceof ErrorValue) {
            return template;
        }

        // Streaming templates are not supported
        if (template instanceof StreamOperator) {
            return Value.errorAt(loc, ERROR_STREAMING_TEMPLATE);
        }

        return switch (parent) {
        case Value pv when pv instanceof UndefinedValue -> pv;
        case Value pv                                   -> compileValueParent(pv, template, loc, ctx);
        case PureOperator pp                            -> compilePureParent(pp, template, loc);
        case StreamOperator ps                          -> compileStreamParent(ps, template, loc);
        };
    }

    private CompiledExpression compileValueParent(Value parent, CompiledExpression template, SourceLocation loc,
            CompilationContext ctx) {
        return switch (template) {
        case Value tv               -> applyTemplateToValue(parent, tv, loc);
        case PureOperator tp        -> {
            if (!tp.isDependingOnSubscription()) {
                // Constant fold: template only uses @ and #, can evaluate at compile time
                yield applyTemplateToValueAtCompileTime(parent, tp, loc, ctx);
            }
            // Template depends on subscription, must defer to runtime
            yield new SubtemplateValuePure(parent, tp, loc);
        }
        case StreamOperator ignored -> throw new IllegalStateException("Handled above");
        };
    }

    private CompiledExpression compilePureParent(PureOperator parent, CompiledExpression template, SourceLocation loc) {
        return switch (template) {
        case Value tv               -> new SubtemplatePureValue(parent, tv, loc);
        case PureOperator tp        -> new SubtemplatePurePure(parent, tp, loc);
        case StreamOperator ignored -> throw new IllegalStateException("Handled above");
        };
    }

    private CompiledExpression compileStreamParent(StreamOperator parent, CompiledExpression template,
            SourceLocation loc) {
        return switch (template) {
        case Value tv               -> new SubtemplateStreamValue(parent, tv, loc);
        case PureOperator tp        -> new SubtemplateStreamPure(parent, tp, loc);
        case StreamOperator ignored -> throw new IllegalStateException("Handled above");
        };
    }

    private static Value applyTemplateToValue(Value parent, Value template, SourceLocation loc) {
        // Template is a constant value - ignore parent, return template (or map over
        // array)
        if (parent instanceof ArrayValue arr) {
            if (arr.isEmpty()) {
                return arr;
            }
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                builder.add(template);
            }
            return builder.build();
        }
        return template;
    }

    private static CompiledExpression applyTemplateToValueAtCompileTime(Value parent, PureOperator template,
            SourceLocation loc, CompilationContext compilationCtx) {
        // Create context with function broker for compile-time evaluation
        val baseCtx = new EvaluationContext(null, null, null, null, compilationCtx.getFunctionBroker(),
                compilationCtx.getAttributeBroker());

        if (parent instanceof ArrayValue arr) {
            if (arr.isEmpty()) {
                return arr;
            }
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                val element = arr.get(i);
                val ctx     = baseCtx.withRelativeValue(element, Value.of(i));
                val result  = template.evaluate(ctx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }

        if (parent instanceof ObjectValue obj) {
            if (obj.isEmpty()) {
                return Value.EMPTY_ARRAY;
            }
            val builder = ArrayValue.builder();
            for (val entry : obj.entrySet()) {
                val ctx    = baseCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val result = template.evaluate(ctx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }

        // Scalar: apply template with @ = value, # = 0
        val ctx = baseCtx.withRelativeValue(parent, Value.of(0));
        return template.evaluate(ctx);
    }

    /**
     * Applies a constant template value to parent. No context needed since template
     * is already evaluated.
     */
    static Value applyConstantTemplate(Value parent, Value template) {
        if (parent instanceof UndefinedValue) {
            return parent;
        }
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof ArrayValue arr) {
            if (arr.isEmpty()) {
                return arr;
            }
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                builder.add(template);
            }
            return builder.build();
        }
        if (parent instanceof ObjectValue obj) {
            if (obj.isEmpty()) {
                return Value.EMPTY_ARRAY;
            }
            val builder = ArrayValue.builder();
            for (int i = 0; i < obj.size(); i++) {
                builder.add(template);
            }
            return builder.build();
        }
        // Scalar
        return template;
    }

    /**
     * Applies a pure template operator to parent, evaluating it for each element
     * with @ and # bound appropriately.
     */
    static Value applyPureTemplate(Value parent, PureOperator template, EvaluationContext ctx) {
        if (parent instanceof UndefinedValue) {
            return parent;
        }
        if (parent instanceof ErrorValue) {
            return parent;
        }
        if (parent instanceof ArrayValue arr) {
            if (arr.isEmpty()) {
                return arr;
            }
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                val element = arr.get(i);
                val elemCtx = ctx.withRelativeValue(element, Value.of(i));
                val result  = template.evaluate(elemCtx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }
        if (parent instanceof ObjectValue obj) {
            if (obj.isEmpty()) {
                return Value.EMPTY_ARRAY;
            }
            val builder = ArrayValue.builder();
            for (val entry : obj.entrySet()) {
                val elemCtx = ctx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val result  = template.evaluate(elemCtx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                builder.add(result);
            }
            return builder.build();
        }
        // Scalar: apply template with @ = value, # = 0
        val elemCtx = ctx.withRelativeValue(parent, Value.of(0));
        return template.evaluate(elemCtx);
    }

    public record SubtemplateValuePure(Value parent, PureOperator template, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyPureTemplate(parent, template, ctx);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return template.isDependingOnSubscription();
        }
    }

    public record SubtemplatePureValue(PureOperator parent, Value template, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val parentValue = parent.evaluate(ctx);
            return applyConstantTemplate(parentValue, template);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return parent.isDependingOnSubscription();
        }
    }

    public record SubtemplatePurePure(PureOperator parent, PureOperator template, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            val parentValue = parent.evaluate(ctx);
            return applyPureTemplate(parentValue, template, ctx);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return parent.isDependingOnSubscription() || template.isDependingOnSubscription();
        }
    }

    public record SubtemplateStreamValue(StreamOperator parent, Value template, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return parent.stream().map(tv -> {
                val parentValue = tv.value();
                val result      = applyConstantTemplate(parentValue, template);
                return new TracedValue(result, tv.contributingAttributes());
            });
        }
    }

    public record SubtemplateStreamPure(StreamOperator parent, PureOperator template, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(reactorCtx -> {
                val ctx = reactorCtx.get(EvaluationContext.class);
                return parent.stream().map(tv -> {
                    val parentValue = tv.value();
                    val result      = applyPureTemplate(parentValue, template, ctx);
                    return new TracedValue(result, tv.contributingAttributes());
                });
            });
        }
    }

}
