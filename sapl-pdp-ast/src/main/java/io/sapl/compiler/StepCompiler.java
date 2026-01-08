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
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

/**
 * Compiles navigation steps using cost-stratified evaluation.
 * <p>
 * Steps navigate into their base expression's value. The compiler handles
 * three strata:
 * <ul>
 * <li>{@link Value} - Apply step at compile time (constant folding)</li>
 * <li>{@link PureOperator} - Wrap in PureOperator that applies step at
 * runtime</li>
 * <li>{@link StreamOperator} - Wrap in StreamOperator that applies step to each
 * emission</li>
 * </ul>
 */
@UtilityClass
public class StepCompiler {

    private static final int    MAX_RECURSION_DEPTH                   = 500;
    private static final String ERROR_INDEX_OUT_OF_BOUNDS             = "Array index out of bounds: %d (size: %d).";
    private static final String ERROR_INDEX_ON_NON_ARRAY              = "Cannot apply index step to %s.";
    private static final String ERROR_WILDCARD_ON_INVALID             = "Cannot apply wildcard step to %s.";
    private static final String ERROR_INDEX_UNION_ON_INVALID          = "Cannot apply index union to %s.";
    private static final String ERROR_ATTR_UNION_ON_INVALID           = "Cannot apply attribute union to %s.";
    private static final String ERROR_SLICE_ON_NON_ARRAY              = "Cannot apply slice to %s.";
    private static final String ERROR_SLICE_STEP_ZERO                 = "Slice step cannot be zero.";
    private static final String ERROR_EXPR_STEP_INVALID_TYPE          = "Expression step requires number or string, got %s.";
    private static final String ERROR_CONDITION_ON_INVALID            = "Cannot apply condition step to %s.";
    private static final String ERROR_CONDITION_NON_BOOLEAN           = "Condition must evaluate to boolean, got %s.";
    private static final String ERROR_CONDITION_STREAMING_UNSUPPORTED = "Condition step with streaming condition not yet supported";
    private static final String ERROR_EXPR_STEP_STREAMING_UNSUPPORTED = "Expression step with streaming expression not yet supported";
    private static final String ERROR_MAX_RECURSION_DEPTH_INDEX       = "Maximum nesting depth exceeded during recursive index step.";
    private static final String ERROR_MAX_RECURSION_DEPTH_KEY         = "Maximum nesting depth exceeded during recursive key step.";
    private static final String ERROR_MAX_RECURSION_DEPTH_WILDCARD    = "Maximum nesting depth exceeded during recursive wildcard step.";

    public CompiledExpression compile(Step step, CompilationContext ctx) {
        return switch (step) {
        case KeyStep ks                -> compileKeyStep(ks, ctx);
        case IndexStep is              -> compileIndexStep(is, ctx);
        case WildcardStep ws           -> compileWildcardStep(ws, ctx);
        case IndexUnionStep ius        -> compileIndexUnionStep(ius, ctx);
        case AttributeUnionStep as     -> compileAttributeUnionStep(as, ctx);
        case SliceStep ss              -> compileSliceStep(ss, ctx);
        case ExpressionStep es         -> compileExpressionStep(es, ctx);
        case ConditionStep cs          -> compileConditionStep(cs, ctx);
        case RecursiveKeyStep rks      -> compileRecursiveKeyStep(rks, ctx);
        case RecursiveIndexStep ris    -> compileRecursiveIndexStep(ris, ctx);
        case RecursiveWildcardStep rws -> compileRecursiveWildcardStep(rws, ctx);
        case AttributeStep as          -> AttributeCompiler.compileAttributeStep(as, ctx);
        };
    }

    private CompiledExpression compileKeyStep(KeyStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var key  = step.key();
        var loc  = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyKeyStep(v, key);
        case PureOperator p   -> new KeyStepPure(p, key, loc);
        case StreamOperator s -> new KeyStepStream(s, key, loc);
        };
    }

    static Value applyKeyStep(Value base, String key) {
        return switch (base) {
        case ErrorValue e    -> e;
        case ObjectValue obj -> {
            var result = obj.get(key);
            yield result != null ? result : Value.UNDEFINED;
        }
        case ArrayValue arr  -> projectKeyOverArray(arr, key);
        default              -> Value.UNDEFINED;
        };
    }

    private static Value projectKeyOverArray(ArrayValue arr, String key) {
        var builder = ArrayValue.builder();
        for (var element : arr) {
            if (element instanceof ObjectValue obj) {
                var value = obj.get(key);
                if (value != null) {
                    builder.add(value);
                }
            }
        }
        return builder.build();
    }

    record KeyStepPure(PureOperator base, String key, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyKeyStep(base.evaluate(ctx), key);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record KeyStepStream(StreamOperator base, String key, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyKeyStep(tv.value(), key), tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileIndexStep(IndexStep step, CompilationContext ctx) {
        var base  = ExpressionCompiler.compile(step.base(), ctx);
        var index = step.index();
        var loc   = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyIndexStep(v, index, loc);
        case PureOperator p   -> new IndexStepPure(p, index, loc);
        case StreamOperator s -> new IndexStepStream(s, index, loc);
        };
    }

    static Value applyIndexStep(Value base, int index, SourceLocation loc) {
        return switch (base) {
        case ErrorValue e   -> e;
        case ArrayValue arr -> {
            int normalizedIndex = index >= 0 ? index : arr.size() + index;
            if (normalizedIndex < 0 || normalizedIndex >= arr.size()) {
                yield Value.errorAt(loc, ERROR_INDEX_OUT_OF_BOUNDS, index, arr.size());
            }
            yield arr.get(normalizedIndex);
        }
        default             -> Value.errorAt(loc, ERROR_INDEX_ON_NON_ARRAY, base.getClass().getSimpleName());
        };
    }

    record IndexStepPure(PureOperator base, int index, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyIndexStep(base.evaluate(ctx), index, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record IndexStepStream(StreamOperator base, int index, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(
                    tv -> new TracedValue(applyIndexStep(tv.value(), index, location), tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileWildcardStep(WildcardStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var loc  = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyWildcardStep(v, loc);
        case PureOperator p   -> new WildcardStepPure(p, loc);
        case StreamOperator s -> new WildcardStepStream(s, loc);
        };
    }

    static Value applyWildcardStep(Value base, SourceLocation loc) {
        return switch (base) {
        case ErrorValue e    -> e;
        case ArrayValue arr  -> arr;
        case ObjectValue obj -> Value.ofArray(obj.values().toArray(Value[]::new));
        default              -> Value.errorAt(loc, ERROR_WILDCARD_ON_INVALID, base.getClass().getSimpleName());
        };
    }

    record WildcardStepPure(PureOperator base, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyWildcardStep(base.evaluate(ctx), location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record WildcardStepStream(StreamOperator base, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream()
                    .map(tv -> new TracedValue(applyWildcardStep(tv.value(), location), tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileIndexUnionStep(IndexUnionStep step, CompilationContext ctx) {
        var base    = ExpressionCompiler.compile(step.base(), ctx);
        var indices = step.indices();
        var loc     = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyIndexUnionStep(v, indices, loc);
        case PureOperator p   -> new IndexUnionStepPure(p, indices, loc);
        case StreamOperator s -> new IndexUnionStepStream(s, indices, loc);
        };
    }

    static Value applyIndexUnionStep(Value base, List<Integer> indices, SourceLocation loc) {
        return switch (base) {
        case ErrorValue e   -> e;
        case ArrayValue arr -> {
            var size = arr.size();
            // Normalize indices and skip out-of-bounds (silently ignored per sapl-lang
            // behavior)
            var normalizedIndices = new java.util.ArrayList<Integer>();
            var seen              = new HashSet<Integer>();
            for (var index : indices) {
                int normalized = index >= 0 ? index : size + index;
                // Skip out-of-bounds indices silently
                if (normalized >= 0 && normalized < size && seen.add(normalized)) {
                    normalizedIndices.add(normalized);
                }
            }
            // Collect values in array order (preserving original array order)
            var builder       = ArrayValue.builder();
            var normalizedSet = new HashSet<>(normalizedIndices);
            for (int i = 0; i < size; i++) {
                if (normalizedSet.contains(i)) {
                    builder.add(arr.get(i));
                }
            }
            yield builder.build();
        }
        default             -> Value.errorAt(loc, ERROR_INDEX_UNION_ON_INVALID, base.getClass().getSimpleName());
        };
    }

    record IndexUnionStepPure(PureOperator base, List<Integer> indices, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyIndexUnionStep(base.evaluate(ctx), indices, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record IndexUnionStepStream(StreamOperator base, List<Integer> indices, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyIndexUnionStep(tv.value(), indices, location),
                    tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileAttributeUnionStep(AttributeUnionStep step, CompilationContext ctx) {
        var base       = ExpressionCompiler.compile(step.base(), ctx);
        var attributes = step.attributes();
        var loc        = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyAttributeUnionStep(v, attributes, loc);
        case PureOperator p   -> new AttributeUnionStepPure(p, attributes, loc);
        case StreamOperator s -> new AttributeUnionStepStream(s, attributes, loc);
        };
    }

    static Value applyAttributeUnionStep(Value base, List<String> attributes, SourceLocation loc) {
        return switch (base) {
        case ErrorValue e    -> e;
        case ObjectValue obj -> {
            var builder = ArrayValue.builder();
            var seen    = new HashSet<String>();
            for (var attr : attributes) {
                if (seen.add(attr) && obj.containsKey(attr)) {
                    builder.add(obj.get(attr));
                }
            }
            yield builder.build();
        }
        default              -> Value.errorAt(loc, ERROR_ATTR_UNION_ON_INVALID, base.getClass().getSimpleName());
        };
    }

    record AttributeUnionStepPure(PureOperator base, List<String> attributes, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyAttributeUnionStep(base.evaluate(ctx), attributes, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record AttributeUnionStepStream(StreamOperator base, List<String> attributes, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyAttributeUnionStep(tv.value(), attributes, location),
                    tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileSliceStep(SliceStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var from = step.from();
        var to   = step.to();
        var s    = step.step();
        var loc  = step.location();

        // Validate step at compile time
        if (s != null && s == 0) {
            return Value.errorAt(loc, ERROR_SLICE_STEP_ZERO);
        }

        return switch (base) {
        case ErrorValue e      -> e;
        case Value v           -> applySliceStep(v, from, to, s, loc);
        case PureOperator p    -> new SliceStepPure(p, from, to, s, loc);
        case StreamOperator st -> new SliceStepStream(st, from, to, s, loc);
        };
    }

    static Value applySliceStep(Value base, Integer from, Integer to, Integer step, SourceLocation loc) {
        if (base instanceof ErrorValue e) {
            return e;
        }
        if (!(base instanceof ArrayValue arr)) {
            return Value.errorAt(loc, ERROR_SLICE_ON_NON_ARRAY, base.getClass().getSimpleName());
        }

        int size    = arr.size();
        int stepVal = step != null ? step : 1;
        int fromVal = normalizeSliceIndex(from, size, stepVal > 0 ? 0 : size - 1);
        int toVal   = normalizeSliceIndex(to, size, stepVal > 0 ? size : -size - 1);

        var builder = ArrayValue.builder();
        if (stepVal > 0) {
            for (int i = fromVal; i < toVal && i < size; i += stepVal) {
                if (i >= 0) {
                    builder.add(arr.get(i));
                }
            }
        } else {
            for (int i = fromVal; i > toVal && i >= 0; i += stepVal) {
                if (i < size) {
                    builder.add(arr.get(i));
                }
            }
        }
        return builder.build();
    }

    private static int normalizeSliceIndex(Integer index, int size, int defaultVal) {
        if (index == null) {
            return defaultVal;
        }
        if (index < 0) {
            return Math.max(0, size + index);
        }
        return Math.min(index, size);
    }

    record SliceStepPure(PureOperator base, Integer from, Integer to, Integer step, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applySliceStep(base.evaluate(ctx), from, to, step, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record SliceStepStream(StreamOperator base, Integer from, Integer to, Integer step, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applySliceStep(tv.value(), from, to, step, location),
                    tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileExpressionStep(ExpressionStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var expr = ExpressionCompiler.compile(step.expression(), ctx);
        var loc  = step.location();

        if (base instanceof ErrorValue e)
            return e;
        if (expr instanceof ErrorValue e)
            return e;

        // If expression is streaming, result is streaming
        if (expr instanceof StreamOperator) {
            return Value.error(ERROR_EXPR_STEP_STREAMING_UNSUPPORTED);
        }

        return switch (base) {
        case Value baseVal             -> switch (expr) {
                                   case Value exprVal              -> applyExpressionStep(baseVal, exprVal, loc);
                                   case PureOperator exprOp        ->
                                       new ExpressionStepPure(baseVal, null, exprOp, loc);
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        case PureOperator baseOp       -> switch (expr) {
                                   case Value exprVal              ->
                                       new ExpressionStepPure(null, baseOp, exprVal, loc);
                                   case PureOperator exprOp        -> new ExpressionStepPurePure(baseOp, exprOp, loc);
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        case StreamOperator baseStream -> switch (expr) {
                                   case Value exprVal              ->
                                       new ExpressionStepStream(baseStream, exprVal, loc);
                                   case PureOperator exprOp        ->
                                       new ExpressionStepStreamPure(baseStream, exprOp, loc);
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        };
    }

    static Value applyExpressionStep(Value base, Value expr, SourceLocation loc) {
        if (base instanceof ErrorValue e)
            return e;
        if (expr instanceof ErrorValue e)
            return e;

        return switch (expr) {
        case NumberValue(BigDecimal num) -> {
            int index = num.intValue();
            yield applyIndexStep(base, index, loc);
        }
        case TextValue(String text)      -> applyKeyStep(base, text);
        default                          ->
            Value.errorAt(loc, ERROR_EXPR_STEP_INVALID_TYPE, expr.getClass().getSimpleName());
        };
    }

    record ExpressionStepPure(Value baseValue, PureOperator baseOp, CompiledExpression expr, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            var base    = baseValue != null ? baseValue : baseOp.evaluate(ctx);
            var exprVal = expr instanceof Value v ? v : ((PureOperator) expr).evaluate(ctx);
            return applyExpressionStep(base, exprVal, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            boolean baseDep = baseOp != null && baseOp.isDependingOnSubscription();
            boolean exprDep = expr instanceof PureOperator po && po.isDependingOnSubscription();
            return baseDep || exprDep;
        }
    }

    record ExpressionStepPurePure(PureOperator base, PureOperator expr, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyExpressionStep(base.evaluate(ctx), expr.evaluate(ctx), location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription() || expr.isDependingOnSubscription();
        }
    }

    record ExpressionStepStream(StreamOperator base, Value expr, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyExpressionStep(tv.value(), expr, location),
                    tv.contributingAttributes()));
        }
    }

    record ExpressionStepStreamPure(StreamOperator base, PureOperator expr, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(reactorCtx -> {
                var ctx     = reactorCtx.get(EvaluationContext.class);
                var exprVal = expr.evaluate(ctx);
                return base.stream().map(tv -> new TracedValue(applyExpressionStep(tv.value(), exprVal, location),
                        tv.contributingAttributes()));
            });
        }
    }

    public CompiledExpression compileConditionStep(ConditionStep step, CompilationContext compilationCtx) {
        var base      = ExpressionCompiler.compile(step.base(), compilationCtx);
        var condition = ExpressionCompiler.compile(step.condition(), compilationCtx);
        var loc       = step.location();

        if (base instanceof ErrorValue e)
            return e;
        if (condition instanceof ErrorValue e)
            return e;

        // If condition is streaming, result is streaming
        if (condition instanceof StreamOperator) {
            return Value.error(ERROR_CONDITION_STREAMING_UNSUPPORTED);
        }

        return switch (base) {
        case Value baseVal             -> switch (condition) {
                                   case Value condVal              ->
                                       applyConditionStep(baseVal, condVal, null, null, loc);
                                   case PureOperator condOp        -> {
                                       if (!condOp.isDependingOnSubscription()) {
                                           // Constant fold: condition only uses @ and #, can evaluate at compile time
                                           yield applyConditionStepAtCompileTime(baseVal, condOp, loc, compilationCtx);
                                       }
                                       // Condition depends on subscription, must defer to runtime
                                       yield new ConditionStepConstBasePure(baseVal, condOp, loc);
                                   }
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        case PureOperator baseOp       -> switch (condition) {
                                   case Value condVal              ->
                                       new ConditionStepPureConstCond(baseOp, condVal, loc);
                                   case PureOperator condOp        -> new ConditionStepPurePure(baseOp, condOp, loc);
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        case StreamOperator baseStream -> switch (condition) {
                                   case Value condVal              ->
                                       new ConditionStepStreamConstCond(baseStream, condVal, loc);
                                   case PureOperator condOp        ->
                                       new ConditionStepStreamPure(baseStream, condOp, loc);
                                   case StreamOperator ignored     -> throw new IllegalStateException("Handled above");
                                   };
        };
    }

    private static Value applyConditionStepAtCompileTime(Value base, PureOperator condition, SourceLocation loc,
            CompilationContext compilationCtx) {
        // Create context with function broker for compile-time evaluation
        val baseCtx = new EvaluationContext(null, null, null, null, compilationCtx.getFunctionBroker(),
                compilationCtx.getAttributeBroker());
        return applyConditionStep(base, null, condition, baseCtx, loc);
    }

    static Value applyConditionStep(Value base, Value constantCond, PureOperator condOp, EvaluationContext ctx,
            SourceLocation loc) {
        if (base instanceof ErrorValue e)
            return e;

        return switch (base) {
        case ArrayValue arr  -> {
            var builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                var element = arr.get(i);
                var elemCtx = ctx != null ? ctx.withRelativeValue(element, Value.of(i)) : null;
                var result  = evaluateCondition(constantCond, condOp, elemCtx);
                if (result instanceof BooleanValue(boolean val)) {
                    if (val)
                        builder.add(element);
                } else if (result instanceof ErrorValue) {
                    yield result;
                } else {
                    yield Value.errorAt(loc, ERROR_CONDITION_NON_BOOLEAN, result.getClass().getSimpleName());
                }
            }
            yield builder.build();
        }
        case ObjectValue obj -> {
            var builder = ArrayValue.builder();
            for (var entry : obj.entrySet()) {
                var element = entry.getValue();
                var elemCtx = ctx != null ? ctx.withRelativeValue(element, Value.of(entry.getKey())) : null;
                var result  = evaluateCondition(constantCond, condOp, elemCtx);
                if (result instanceof BooleanValue(boolean val)) {
                    if (val)
                        builder.add(element);
                } else if (result instanceof ErrorValue) {
                    yield result;
                } else {
                    yield Value.errorAt(loc, ERROR_CONDITION_NON_BOOLEAN, result.getClass().getSimpleName());
                }
            }
            yield builder.build();
        }
        default              -> Value.errorAt(loc, ERROR_CONDITION_ON_INVALID, base.getClass().getSimpleName());
        };
    }

    private static Value evaluateCondition(Value constantCond, PureOperator condOp, EvaluationContext elemCtx) {
        if (constantCond != null) {
            return constantCond;
        }
        if (condOp != null) {
            return condOp.evaluate(elemCtx);
        }
        return Value.FALSE;
    }

    record ConditionStepConstBasePure(Value base, PureOperator condition, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyConditionStep(base, null, condition, ctx, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return condition.isDependingOnSubscription();
        }
    }

    record ConditionStepPureConstCond(PureOperator base, Value condition, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyConditionStep(base.evaluate(ctx), condition, null, ctx, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record ConditionStepPurePure(PureOperator base, PureOperator condition, SourceLocation location)
            implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyConditionStep(base.evaluate(ctx), null, condition, ctx, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription() || condition.isDependingOnSubscription();
        }
    }

    record ConditionStepStreamConstCond(StreamOperator base, Value condition, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(reactorCtx -> {
                var ctx = reactorCtx.get(EvaluationContext.class);
                return base.stream()
                        .map(tv -> new TracedValue(applyConditionStep(tv.value(), condition, null, ctx, location),
                                tv.contributingAttributes()));
            });
        }
    }

    record ConditionStepStreamPure(StreamOperator base, PureOperator condition, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(reactorCtx -> {
                var ctx = reactorCtx.get(EvaluationContext.class);
                return base.stream()
                        .map(tv -> new TracedValue(applyConditionStep(tv.value(), null, condition, ctx, location),
                                tv.contributingAttributes()));
            });
        }
    }

    public CompiledExpression compileRecursiveKeyStep(RecursiveKeyStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var key  = step.key();
        var loc  = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyRecursiveKeyStep(v, key, loc);
        case PureOperator p   -> new RecursiveKeyStepPure(p, key, loc);
        case StreamOperator s -> new RecursiveKeyStepStream(s, key, loc);
        };
    }

    static Value applyRecursiveKeyStep(Value base, String key, SourceLocation loc) {
        if (base instanceof ErrorValue e)
            return e;
        var builder = ArrayValue.builder();
        var result  = collectRecursiveKey(base, key, builder, 0);
        if (result != null) {
            return Value.errorAt(loc, ERROR_MAX_RECURSION_DEPTH_KEY);
        }
        return builder.build();
    }

    private static ErrorValue collectRecursiveKey(Value value, String key, ArrayValue.Builder builder, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.error(ERROR_MAX_RECURSION_DEPTH_KEY);
        }
        switch (value) {
        case ObjectValue obj -> {
            if (obj.containsKey(key)) {
                builder.add(obj.get(key));
            }
            for (var v : obj.values()) {
                var error = collectRecursiveKey(v, key, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        case ArrayValue arr  -> {
            for (var element : arr) {
                var error = collectRecursiveKey(element, key, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        default              -> { /* scalars have no children */ }
        }
        return null;
    }

    record RecursiveKeyStepPure(PureOperator base, String key, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyRecursiveKeyStep(base.evaluate(ctx), key, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record RecursiveKeyStepStream(StreamOperator base, String key, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyRecursiveKeyStep(tv.value(), key, location),
                    tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileRecursiveIndexStep(RecursiveIndexStep step, CompilationContext ctx) {
        var base  = ExpressionCompiler.compile(step.base(), ctx);
        var index = step.index();
        var loc   = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyRecursiveIndexStep(v, index, loc);
        case PureOperator p   -> new RecursiveIndexStepPure(p, index, loc);
        case StreamOperator s -> new RecursiveIndexStepStream(s, index, loc);
        };
    }

    static Value applyRecursiveIndexStep(Value base, int index, SourceLocation loc) {
        if (base instanceof ErrorValue e)
            return e;
        var builder = ArrayValue.builder();
        var result  = collectRecursiveIndex(base, index, builder, 0);
        if (result != null) {
            return Value.errorAt(loc, ERROR_MAX_RECURSION_DEPTH_INDEX);
        }
        return builder.build();
    }

    private static ErrorValue collectRecursiveIndex(Value value, int index, ArrayValue.Builder builder, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.error(ERROR_MAX_RECURSION_DEPTH_INDEX);
        }
        switch (value) {
        case ArrayValue arr  -> {
            int normalizedIndex = index >= 0 ? index : arr.size() + index;
            if (normalizedIndex >= 0 && normalizedIndex < arr.size()) {
                builder.add(arr.get(normalizedIndex));
            }
            for (var element : arr) {
                var error = collectRecursiveIndex(element, index, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        case ObjectValue obj -> {
            for (var v : obj.values()) {
                var error = collectRecursiveIndex(v, index, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        default              -> { /* scalars have no children */ }
        }
        return null;
    }

    record RecursiveIndexStepPure(PureOperator base, int index, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyRecursiveIndexStep(base.evaluate(ctx), index, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record RecursiveIndexStepStream(StreamOperator base, int index, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyRecursiveIndexStep(tv.value(), index, location),
                    tv.contributingAttributes()));
        }
    }

    public CompiledExpression compileRecursiveWildcardStep(RecursiveWildcardStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var loc  = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyRecursiveWildcardStep(v, loc);
        case PureOperator p   -> new RecursiveWildcardStepPure(p, loc);
        case StreamOperator s -> new RecursiveWildcardStepStream(s, loc);
        };
    }

    static Value applyRecursiveWildcardStep(Value base, SourceLocation loc) {
        if (base instanceof ErrorValue e)
            return e;
        var builder = ArrayValue.builder();
        var result  = collectRecursiveWildcard(base, builder, 0);
        if (result != null) {
            return Value.errorAt(loc, ERROR_MAX_RECURSION_DEPTH_WILDCARD);
        }
        return builder.build();
    }

    private static ErrorValue collectRecursiveWildcard(Value value, ArrayValue.Builder builder, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.error(ERROR_MAX_RECURSION_DEPTH_WILDCARD);
        }
        switch (value) {
        case ArrayValue arr  -> {
            for (var element : arr) {
                builder.add(element);
                var error = collectRecursiveWildcard(element, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        case ObjectValue obj -> {
            for (var v : obj.values()) {
                builder.add(v);
                var error = collectRecursiveWildcard(v, builder, depth + 1);
                if (error != null)
                    return error;
            }
        }
        default              -> { /* scalars have no children */ }
        }
        return null;
    }

    record RecursiveWildcardStepPure(PureOperator base, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyRecursiveWildcardStep(base.evaluate(ctx), location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record RecursiveWildcardStepStream(StreamOperator base, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream().map(tv -> new TracedValue(applyRecursiveWildcardStep(tv.value(), location),
                    tv.contributingAttributes()));
        }
    }

}
