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
import io.sapl.api.pdp.internal.AttributeRecord;
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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

    private static final String ERROR_INDEX_OUT_OF_BOUNDS    = "Array index out of bounds: %d (size: %d).";
    private static final String ERROR_INDEX_ON_NON_ARRAY     = "Cannot apply index step to %s.";
    private static final String ERROR_WILDCARD_ON_INVALID    = "Cannot apply wildcard step to %s.";
    private static final String ERROR_INDEX_UNION_ON_INVALID = "Cannot apply index union to %s.";
    private static final String ERROR_ATTR_UNION_ON_INVALID  = "Cannot apply attribute union to %s.";

    public CompiledExpression compile(Step step, CompilationContext ctx) {
        return switch (step) {
        case KeyStep ks            -> compileKeyStep(ks, ctx);
        case IndexStep is          -> compileIndexStep(is, ctx);
        case WildcardStep ws       -> compileWildcardStep(ws, ctx);
        case IndexUnionStep ius    -> compileIndexUnionStep(ius, ctx);
        case AttributeUnionStep as -> compileAttributeUnionStep(as, ctx);
        // TODO: Remaining steps
        case SliceStep ss              -> unimplemented("SliceStep");
        case ExpressionStep es         -> unimplemented("ExpressionStep");
        case ConditionStep cs          -> unimplemented("ConditionStep");
        case RecursiveKeyStep rks      -> unimplemented("RecursiveKeyStep");
        case RecursiveIndexStep ris    -> unimplemented("RecursiveIndexStep");
        case RecursiveWildcardStep rws -> unimplemented("RecursiveWildcardStep");
        case AttributeStep as          -> AttributeCompiler.compileAttributeStep(as, ctx);
        };
    }

    private static Value unimplemented(String type) {
        return Value.error("UNIMPLEMENTED: %s.", type);
    }

    private CompiledExpression compileKeyStep(KeyStep step, CompilationContext ctx) {
        var base = ExpressionCompiler.compile(step.base(), ctx);
        var key  = step.key();
        var loc  = step.location();

        return switch (base) {
        case ErrorValue e     -> e;
        case Value v          -> applyKeyStep(v, key, loc);
        case PureOperator p   -> new KeyStepPure(p, key, loc);
        case StreamOperator s -> new KeyStepStream(s, key, loc);
        };
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

    static Value applyKeyStep(Value base, String key, SourceLocation loc) {
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

    record KeyStepPure(PureOperator base, String key, SourceLocation location) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return applyKeyStep(base.evaluate(ctx), key, location);
        }

        @Override
        public boolean isDependingOnSubscription() {
            return base.isDependingOnSubscription();
        }
    }

    record KeyStepStream(StreamOperator base, String key, SourceLocation location) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return base.stream()
                    .map(tv -> new TracedValue(applyKeyStep(tv.value(), key, location), tv.contributingAttributes()));
        }
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
            var builder = ArrayValue.builder();
            var size    = arr.size();
            var seen    = new java.util.HashSet<Integer>();
            for (int i = 0; i < size; i++) {
                for (var index : indices) {
                    int normalized = index >= 0 ? index : size + index;
                    if (normalized == i && normalized >= 0 && normalized < size && seen.add(normalized)) {
                        builder.add(arr.get(normalized));
                    }
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
            var seen    = new java.util.HashSet<String>();
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

}
