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
package io.sapl.compiler.expressions;

import java.util.Arrays;
import java.util.Objects;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Subscription;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.ArrayExpression;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.sapl.api.model.StreamOperator.evalChild;

/**
 * Compiles array literal expressions using the PRECOMPILED pattern.
 * Elements that evaluate to UndefinedValue are dropped from the result.
 */
@UtilityClass
public class ArrayCompiler {

    public static CompiledExpression compile(ArrayExpression expr, CompilationContext ctx) {
        val elements = expr.elements();

        if (elements.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        val compiled = new ArrayList<CompiledExpression>(elements.size());
        for (var element : elements) {
            val result = ExpressionCompiler.compile(element, ctx);
            if (result instanceof ErrorValue) {
                return result;
            }
            compiled.add(result);
        }

        return buildFromCompiled(compiled, expr.location(), ctx.lowLatencyMode());
    }

    /**
     * Builds an optimized array operator from pre-compiled elements.
     * Used by ArrayExpression compilation and "each" filter compilation.
     *
     * @param compiled the compiled elements (Value/PureOperator/StreamOperator)
     * @param location source location for error reporting
     * @param lowLatencyMode compile-time flag selecting the eager variant
     * (walk all children, accumulate maximum subscription set, parallel
     * subscription via the trigger loop, single-round convergence) when
     * {@code true}; selects the lazy variant (short-circuit on first
     * {@code null} or {@link ErrorValue} child, minimal subscription set,
     * multi-round convergence) when {@code false}.
     * @return appropriate CompiledExpression based on element types
     */
    public static CompiledExpression buildFromCompiled(List<CompiledExpression> compiled, SourceLocation location,
            boolean lowLatencyMode) {
        val cat = CategorizedExpressions.categorize(compiled);

        if (cat.hasOnlyValues()) {
            return buildArrayFromValues(List.of(cat.values()));
        }
        if (cat.streamCount() == 0) {
            return new AllPureArray(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.totalCount(), location);
        }
        if (cat.hasSingleStream()) {
            if (lowLatencyMode) {
                return new SingleStreamArrayEager(cat.valueIndices(), cat.values(), cat.pureIndices(),
                        cat.pureOperators(), cat.streamIndices()[0], cat.streams()[0], cat.totalCount(), compiled);
            }
            return new SingleStreamArrayLazy(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.streamIndices()[0], cat.streams()[0], cat.totalCount(), compiled);
        }
        if (lowLatencyMode) {
            return new MultiStreamArrayEager(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                    cat.streamIndices(), cat.streams(), cat.totalCount(), compiled);
        }
        return new MultiStreamArrayLazy(cat.valueIndices(), cat.values(), cat.pureIndices(), cat.pureOperators(),
                cat.streamIndices(), cat.streams(), cat.totalCount(), compiled);
    }

    private static Value buildArrayFromValues(List<Value> values) {
        val builder = ArrayValue.builder();
        for (var v : values) {
            if (v instanceof ErrorValue) {
                return v;
            }
            if (!(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        return builder.build();
    }

    static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Categorizes compiled expressions into Value, PureOperator, and StreamOperator
     * strata.
     * Used by ArrayCompiler, ObjectCompiler, and AttributeCompiler.
     */
    record CategorizedExpressions(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalCount) {

        static CategorizedExpressions categorize(List<CompiledExpression> compiled) {
            val streamIndices = new ArrayList<Integer>();
            val streams       = new ArrayList<StreamOperator>();
            val pureIndices   = new ArrayList<Integer>();
            val pureOperators = new ArrayList<PureOperator>();
            val valueIndices  = new ArrayList<Integer>();
            val values        = new ArrayList<Value>();

            for (int i = 0; i < compiled.size(); i++) {
                switch (compiled.get(i)) {
                case Value v          -> {
                    valueIndices.add(i);
                    values.add(v);
                }
                case PureOperator p   -> {
                    pureIndices.add(i);
                    pureOperators.add(p);
                }
                case StreamOperator s -> {
                    streamIndices.add(i);
                    streams.add(s);
                }
                }
            }

            return new CategorizedExpressions(toIntArray(valueIndices), values.toArray(Value[]::new),
                    toIntArray(pureIndices), pureOperators.toArray(PureOperator[]::new), toIntArray(streamIndices),
                    streams.toArray(StreamOperator[]::new), compiled.size());
        }

        int streamCount() {
            return streams.length;
        }

        boolean hasOnlyValues() {
            return pureOperators.length == 0 && streams.length == 0;
        }

        boolean hasSingleStream() {
            return streams.length == 1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), Arrays.hashCode(streamIndices), Arrays.hashCode(streams),
                    totalCount);
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof CategorizedExpressions(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreams, var oTotal)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && Arrays.equals(streamIndices, oStreamIdx) && Arrays.equals(streams, oStreams)
                            && totalCount == oTotal);
        }
    }

    /**
     * Array with all pure elements (values and pure operators, no streams).
     */
    record AllPureArray(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int totalElements,
            SourceLocation location) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(AllPureArray.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val elements = new Value[totalElements];

            for (int i = 0; i < valueIndices.length; i++) {
                elements[valueIndices[i]] = values[i];
            }

            for (int i = 0; i < pureIndices.length; i++) {
                val value = pureOperators[i].evaluate(ctx);
                if (value instanceof ErrorValue) {
                    return value;
                }
                elements[pureIndices[i]] = value;
            }

            val builder = ArrayValue.builder();
            for (var element : elements) {
                if (!(element instanceof UndefinedValue)) {
                    builder.add(element);
                }
            }
            return builder.build();
        }

        @Override
        public boolean isDependingOnSubscription() {
            for (var op : pureOperators) {
                if (op.isDependingOnSubscription()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isRelativeExpression() {
            for (var op : pureOperators) {
                if (op.isRelativeExpression()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long semanticHash() {
            long hash = SemanticHashing.ordered(KIND, Arrays.hashCode(valueIndices), Arrays.hashCode(values),
                    Arrays.hashCode(pureIndices), totalElements);
            for (var po : pureOperators) {
                hash = SemanticHashing.ordered(hash, po.semanticHash());
            }
            return hash;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), totalElements, Objects.hashCode(location));
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof AllPureArray r && Arrays.equals(valueIndices, r.valueIndices)
                    && Arrays.equals(values, r.values) && Arrays.equals(pureIndices, r.pureIndices)
                    && Arrays.equals(pureOperators, r.pureOperators) && totalElements == r.totalElements
                    && Objects.equals(location, r.location));
        }
    }

    /**
     * Array with exactly one stream element. Lazy variant: snapshot
     * {@code evaluate(ctx)} short-circuits on the first {@code null} or
     * {@link ErrorValue} child without subscribing to later children.
     * Selected at compile time when the {@code lowLatencyMode} compiler
     * option is disabled.
     */
    record SingleStreamArrayLazy(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator streamOp,
            int totalElements,
            List<CompiledExpression> compiledElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return streamOp.stream().switchMap(tracedValue -> {
                val streamVal = tracedValue.value();
                if (streamVal instanceof ErrorValue) {
                    return Flux.just(tracedValue);
                }

                return Flux.deferContextual(ctx -> {
                    val evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        val value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, tracedValue.contributingAttributes()));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    elements[streamIndex] = streamVal;

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), tracedValue.contributingAttributes()));
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return assembleArrayLazy(compiledElements, ctx);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), streamIndex, Objects.hashCode(streamOp), totalElements,
                    Objects.hashCode(compiledElements));
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof SingleStreamArrayLazy(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreamOp, var oTotal, var oCompiled)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && streamIndex == oStreamIdx && Objects.equals(streamOp, oStreamOp)
                            && totalElements == oTotal && Objects.equals(compiledElements, oCompiled));
        }
    }

    /**
     * Array with exactly one stream element. Eager variant: snapshot
     * {@code evaluate(ctx)} walks every child to accumulate the maximum
     * subscription set, holds the first {@link ErrorValue}, and returns it
     * after the full walk. Selected at compile time when the
     * {@code lowLatencyMode} compiler option is enabled (default).
     */
    record SingleStreamArrayEager(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int streamIndex,
            StreamOperator streamOp,
            int totalElements,
            List<CompiledExpression> compiledElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            return streamOp.stream().switchMap(tracedValue -> {
                val streamVal = tracedValue.value();
                if (streamVal instanceof ErrorValue) {
                    return Flux.just(tracedValue);
                }

                return Flux.deferContextual(ctx -> {
                    val evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        val value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, tracedValue.contributingAttributes()));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    elements[streamIndex] = streamVal;

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), tracedValue.contributingAttributes()));
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return assembleArrayEager(compiledElements, ctx);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), streamIndex, Objects.hashCode(streamOp), totalElements,
                    Objects.hashCode(compiledElements));
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof SingleStreamArrayEager(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreamOp, var oTotal, var oCompiled)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && streamIndex == oStreamIdx && Objects.equals(streamOp, oStreamOp)
                            && totalElements == oTotal && Objects.equals(compiledElements, oCompiled));
        }
    }

    /**
     * Lazy assembly: evaluates compiled elements left-to-right via
     * {@link StreamOperator#evalChild} accumulating subscriptions, and returns
     * immediately on the first {@code null} (incomplete) or {@link ErrorValue}
     * child. Children past the short-circuit are not evaluated and contribute
     * no subscriptions. Drops {@link UndefinedValue} elements per array literal
     * semantics. Multi-round trigger-loop convergence: each round resolves the
     * leading missing dependency and re-evaluates; later rounds may reveal
     * further missing dependencies further to the right.
     */
    private static ExpressionResult assembleArrayLazy(List<CompiledExpression> elements, EvaluationContext ctx) {
        val subs    = HashSet.<Subscription>newHashSet(elements.size());
        val builder = ArrayValue.builder();
        for (val element : elements) {
            val v = evalChild(element, ctx, subs);
            if (v == null) {
                return new ExpressionResult(null, subs);
            }
            if (v instanceof ErrorValue) {
                return new ExpressionResult(v, subs);
            }
            if (!(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        return new ExpressionResult(builder.build(), subs);
    }

    /**
     * Eager assembly: evaluates every compiled element via
     * {@link StreamOperator#evalChild}, accumulating subscriptions even past
     * any encountered {@link ErrorValue}. Holds the first error and returns it
     * after the full walk completes. Drops {@link UndefinedValue} elements per
     * array literal semantics. {@code null} from a child sets the incomplete
     * flag; on a clean walk with no error, returns the assembled array.
     * Precedence at the end: error > null > built array.
     */
    private static ExpressionResult assembleArrayEager(List<CompiledExpression> elements, EvaluationContext ctx) {
        val     subs       = HashSet.<Subscription>newHashSet(elements.size());
        boolean seenNull   = false;
        Value   firstError = null;
        val     builder    = ArrayValue.builder();
        for (val element : elements) {
            val v = evalChild(element, ctx, subs);
            if (v == null) {
                seenNull = true;
                continue;
            }
            if (v instanceof ErrorValue) {
                if (firstError == null) {
                    firstError = v;
                }
                continue;
            }
            if (!(v instanceof UndefinedValue)) {
                builder.add(v);
            }
        }
        if (firstError != null) {
            return new ExpressionResult(firstError, subs);
        }
        if (seenNull) {
            return new ExpressionResult(null, subs);
        }
        return new ExpressionResult(builder.build(), subs);
    }

    /**
     * Array with multiple stream elements. Lazy variant: snapshot
     * {@code evaluate(ctx)} short-circuits on the first {@code null} or
     * {@link ErrorValue} child without subscribing to later children.
     * Selected at compile time when the {@code lowLatencyMode} compiler
     * option is disabled.
     */
    record MultiStreamArrayLazy(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalElements,
            List<CompiledExpression> compiledElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            List<Flux<TracedValue>> fluxList = new ArrayList<>(streams.length);
            for (var s : streams) {
                fluxList.add(s.stream());
            }

            return Flux.combineLatest(fluxList, arr -> {
                val combinedTraces = new ArrayList<AttributeRecord>();
                val streamValues   = new TracedValue[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    streamValues[i] = (TracedValue) arr[i];
                    combinedTraces.addAll(streamValues[i].contributingAttributes());
                }
                return new CombinedStreams(streamValues, combinedTraces);
            }).switchMap(combined -> {
                for (var tv : combined.values) {
                    if (tv.value() instanceof ErrorValue) {
                        return Flux.just(new TracedValue(tv.value(), combined.traces));
                    }
                }

                return Flux.deferContextual(ctx -> {
                    val evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        val value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, combined.traces));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    for (int i = 0; i < streamIndices.length; i++) {
                        elements[streamIndices[i]] = combined.values[i].value();
                    }

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), combined.traces));
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return assembleArrayLazy(compiledElements, ctx);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), Arrays.hashCode(streamIndices), Arrays.hashCode(streams),
                    totalElements, Objects.hashCode(compiledElements));
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof MultiStreamArrayLazy(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreams, var oTotal, var oCompiled)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && Arrays.equals(streamIndices, oStreamIdx) && Arrays.equals(streams, oStreams)
                            && totalElements == oTotal && Objects.equals(compiledElements, oCompiled));
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {

            @Override
            public int hashCode() {
                return Objects.hash(Arrays.hashCode(values), Objects.hashCode(traces));
            }

            @Override
            public boolean equals(Object o) {
                return this == o || (o instanceof CombinedStreams(var oValues, var oTraces)
                        && Arrays.equals(values, oValues) && Objects.equals(traces, oTraces));
            }
        }
    }

    /**
     * Array with multiple stream elements. Eager variant: snapshot
     * {@code evaluate(ctx)} walks every child to accumulate the maximum
     * subscription set, holds the first {@link ErrorValue}, and returns it
     * after the full walk. Selected at compile time when the
     * {@code lowLatencyMode} compiler option is enabled (default).
     */
    record MultiStreamArrayEager(
            int[] valueIndices,
            Value[] values,
            int[] pureIndices,
            PureOperator[] pureOperators,
            int[] streamIndices,
            StreamOperator[] streams,
            int totalElements,
            List<CompiledExpression> compiledElements) implements StreamOperator {

        @Override
        public Flux<TracedValue> stream() {
            List<Flux<TracedValue>> fluxList = new ArrayList<>(streams.length);
            for (var s : streams) {
                fluxList.add(s.stream());
            }

            return Flux.combineLatest(fluxList, arr -> {
                val combinedTraces = new ArrayList<AttributeRecord>();
                val streamValues   = new TracedValue[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    streamValues[i] = (TracedValue) arr[i];
                    combinedTraces.addAll(streamValues[i].contributingAttributes());
                }
                return new CombinedStreams(streamValues, combinedTraces);
            }).switchMap(combined -> {
                for (var tv : combined.values) {
                    if (tv.value() instanceof ErrorValue) {
                        return Flux.just(new TracedValue(tv.value(), combined.traces));
                    }
                }

                return Flux.deferContextual(ctx -> {
                    val evalCtx  = ctx.get(EvaluationContext.class);
                    val elements = new Value[totalElements];

                    for (int i = 0; i < valueIndices.length; i++) {
                        elements[valueIndices[i]] = values[i];
                    }

                    for (int i = 0; i < pureIndices.length; i++) {
                        val value = pureOperators[i].evaluate(evalCtx);
                        if (value instanceof ErrorValue) {
                            return Flux.just(new TracedValue(value, combined.traces));
                        }
                        elements[pureIndices[i]] = value;
                    }

                    for (int i = 0; i < streamIndices.length; i++) {
                        elements[streamIndices[i]] = combined.values[i].value();
                    }

                    val builder = ArrayValue.builder();
                    for (var element : elements) {
                        if (element != null && !(element instanceof UndefinedValue)) {
                            builder.add(element);
                        }
                    }
                    return Flux.just(new TracedValue(builder.build(), combined.traces));
                });
            });
        }

        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return assembleArrayEager(compiledElements, ctx);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(valueIndices), Arrays.hashCode(values), Arrays.hashCode(pureIndices),
                    Arrays.hashCode(pureOperators), Arrays.hashCode(streamIndices), Arrays.hashCode(streams),
                    totalElements, Objects.hashCode(compiledElements));
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || (o instanceof MultiStreamArrayEager(var oValIdx, var oVals, var oPureIdx, var oPureOps, var oStreamIdx, var oStreams, var oTotal, var oCompiled)
                            && Arrays.equals(valueIndices, oValIdx) && Arrays.equals(values, oVals)
                            && Arrays.equals(pureIndices, oPureIdx) && Arrays.equals(pureOperators, oPureOps)
                            && Arrays.equals(streamIndices, oStreamIdx) && Arrays.equals(streams, oStreams)
                            && totalElements == oTotal && Objects.equals(compiledElements, oCompiled));
        }

        private record CombinedStreams(TracedValue[] values, List<AttributeRecord> traces) {

            @Override
            public int hashCode() {
                return Objects.hash(Arrays.hashCode(values), Objects.hashCode(traces));
            }

            @Override
            public boolean equals(Object o) {
                return this == o || (o instanceof CombinedStreams(var oValues, var oTraces)
                        && Arrays.equals(values, oValues) && Objects.equals(traces, oTraces));
            }
        }
    }

}
