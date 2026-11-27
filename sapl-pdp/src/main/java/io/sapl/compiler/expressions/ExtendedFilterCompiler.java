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

import io.sapl.api.model.*;
import io.sapl.ast.*;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.util.BoundedRegex;
import io.sapl.compiler.util.DummyEvaluationContextFactory;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static io.sapl.api.model.StreamOperator.evalChild;

@UtilityClass
public class ExtendedFilterCompiler {

    private static final int MAX_RECURSION_DEPTH = 500;

    public static final String     ERROR_CONDITION_NON_BOOLEAN                                     = "Condition must evaluate to boolean, got %s.";
    public static final String     ERROR_MAXIMUM_RECURSION_DEPTH_EXCEEDED                          = "Maximum recursion depth exceeded";
    public static final ErrorValue ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_FUNCTION_ARGUMENTS = Value
            .error("Stream operators not allowed in filter function arguments.");
    public static final String     ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_PATH               = "Stream operators not allowed in filter path.";

    public static CompiledExpression compile(ExtendedFilter ef, CompilationContext ctx) {
        val compiledBase = ExpressionCompiler.compile(ef.base(), ctx);
        if (compiledBase instanceof ErrorValue) {
            return compiledBase;
        }

        val tempSimpleFilter = new SimpleFilter(new RelativeReference(RelativeType.VALUE, ef.location()), ef.name(),
                ef.arguments(), ef.each(), ef.location());
        val compiledFilter   = FilterCompiler.compileSimpleUnfolded(tempSimpleFilter, ctx);
        if (compiledFilter instanceof ErrorValue) {
            return compiledFilter;
        }
        if (compiledFilter instanceof StreamOperator) {
            return ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_FUNCTION_ARGUMENTS;
        }

        val path         = ef.target().elements();
        val pathAnalysis = analyzePath(path, ef.location(), ctx);
        val canFoldPath  = !pathAnalysis.isDependingOnSubscription();

        if (compiledBase instanceof Value vb && compiledFilter instanceof Value vf && canFoldPath) {
            return evaluateValueValue(vb, vf, path, pathAnalysis, ctx);
        }
        if (compiledBase instanceof Value vb && compiledFilter instanceof PureOperator pof
                && !pof.isDependingOnSubscription() && canFoldPath) {
            return evaluateValuePureFold(vb, pof, path, pathAnalysis, ctx);
        }
        if (compiledBase instanceof StreamOperator sb) {
            return new ExtendedFilterStream(sb, compiledFilter, path, pathAnalysis);
        }
        return new ExtendedFilterPure(compiledBase, compiledFilter, path, pathAnalysis);
    }

    private static Value evaluateValueValue(Value base, Value filter, List<PathElement> path, PathAnalysis pathAnalysis,
            CompilationContext ctx) {
        val evalCtx = DummyEvaluationContextFactory.dummyContext(ctx).withRelativeValue(base);
        return navigateAndApply(base, current -> filter, path, pathAnalysis, evalCtx);
    }

    private static CompiledExpression evaluateValuePureFold(Value base, PureOperator filter, List<PathElement> path,
            PathAnalysis pathAnalysis, CompilationContext ctx) {
        val evalCtx = DummyEvaluationContextFactory.dummyContext(ctx).withRelativeValue(base);
        return navigateAndApply(base, current -> filter.evaluate(evalCtx.withRelativeValue(current)), path,
                pathAnalysis, evalCtx);
    }

    interface ExtendedFilterPureOperator extends PureOperator {
        PathAnalysis pathAnalysis();

        @Override
        default boolean isDependingOnSubscription() {
            return true;
        }

        @Override
        default SourceLocation location() {
            return pathAnalysis().filterLocation();
        }
    }

    /**
     * Pure-stratum extended filter. Both {@code base} and {@code filter} are
     * {@link Value} or {@link PureOperator}; the {@link StreamOperator} branch
     * is unreachable by construction (filter compilation rejects streams) and
     * folds to an {@link ErrorValue} defensively.
     */
    record ExtendedFilterPure(
            CompiledExpression base,
            CompiledExpression filter,
            List<PathElement> path,
            PathAnalysis pathAnalysis) implements ExtendedFilterPureOperator {
        private static final long KIND = SemanticHashing.kindHash(ExtendedFilterPure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val baseValue = switch (base) {
            case Value v                -> v;
            case PureOperator p         -> p.evaluate(ctx);
            case StreamOperator ignored -> Value.errorAt(pathAnalysis.filterLocation(),
                    ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_FUNCTION_ARGUMENTS.message());
            };
            if (baseValue instanceof ErrorValue) {
                return baseValue;
            }
            val initialCtx = ctx.withRelativeValue(baseValue);
            return navigateAndApply(baseValue, terminalFor(filter, initialCtx), path, pathAnalysis, initialCtx);
        }

        @Override
        public boolean isRelativeExpression() {
            return base instanceof PureOperator p && p.isRelativeExpression();
        }

        @Override
        public long semanticHash() {
            val baseHash   = base instanceof Value v ? SemanticHashing.valueHash(v)
                    : ((PureOperator) base).semanticHash();
            val filterHash = filter instanceof Value v ? SemanticHashing.valueHash(v)
                    : ((PureOperator) filter).semanticHash();
            return SemanticHashing.ordered(KIND, baseHash, filterHash, path.hashCode(),
                    pathAnalysis.compiledElements().hashCode());
        }
    }

    /**
     * Stream-stratum extended filter. {@code base} is a {@link StreamOperator}.
     * {@code filter} is {@link Value} or {@link PureOperator}; the
     * {@link StreamOperator} branch is unreachable by construction (filter
     * compilation rejects streams) and folds to an {@link ErrorValue}
     * defensively.
     */
    record ExtendedFilterStream(
            StreamOperator baseStream,
            CompiledExpression filter,
            List<PathElement> path,
            PathAnalysis pathAnalysis) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(1);
            val v    = evalChild(baseStream, ctx, deps);
            if (v == null || v instanceof ErrorValue) {
                return new ExpressionResult(v, deps);
            }
            val initialCtx = ctx.withRelativeValue(v);
            val result     = navigateAndApply(v, terminalFor(filter, initialCtx), path, pathAnalysis, initialCtx);
            return new ExpressionResult(result, deps);
        }
    }

    private static UnaryOperator<Value> terminalFor(CompiledExpression filter, EvaluationContext initialCtx) {
        return switch (filter) {
        case Value vf               -> current -> vf;
        case PureOperator pf        -> current -> pf.evaluate(initialCtx.withRelativeValue(current));
        case StreamOperator ignored -> current -> ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_FUNCTION_ARGUMENTS;
        };
    }

    private static Value navigateAndApply(Value current, UnaryOperator<Value> terminal, List<PathElement> path,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        // Shared regex budget across the whole filter so per-element matches cannot
        // amplify cost with size.
        return BoundedRegex.runWithSharedMatchBudget(() -> navigate(current, terminal, path, pathAnalysis, evalCtx));
    }

    private static Value navigate(Value current, UnaryOperator<Value> terminal, List<PathElement> path,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (current instanceof ErrorValue) {
            return current;
        }
        if (path.isEmpty()) {
            return terminal.apply(current);
        }
        val head = path.getFirst();
        val tail = path.subList(1, path.size());
        return switch (head) {
        case KeyPath kp                    -> navigateKey(current, kp.key(), terminal, tail, pathAnalysis, evalCtx);
        case IndexPath ip                  -> navigateIndex(current, ip.index(), terminal, tail, pathAnalysis, evalCtx);
        case WildcardPath ignored          -> navigateWildcard(current, terminal, tail, pathAnalysis, evalCtx);
        case AttributeUnionPath aup        ->
            navigateAttributeUnion(current, aup.keys(), terminal, tail, pathAnalysis, evalCtx);
        case IndexUnionPath iup            ->
            navigateIndexUnion(current, iup.indices(), terminal, tail, pathAnalysis, evalCtx);
        case SlicePath sp                  -> navigateSlice(current, sp, terminal, tail, pathAnalysis, evalCtx);
        case RecursiveKeyPath rkp          ->
            navigateRecursiveKey(current, rkp.key(), terminal, tail, pathAnalysis, evalCtx, 0);
        case RecursiveIndexPath rip        ->
            navigateRecursiveIndex(current, rip.index(), terminal, tail, pathAnalysis, evalCtx, 0);
        case RecursiveWildcardPath ignored ->
            navigateRecursiveWildcard(current, terminal, tail, pathAnalysis, evalCtx, 0);
        case ExpressionPath ep             -> navigateExpression(current, ep, terminal, tail, pathAnalysis, evalCtx);
        case ConditionPath cp              -> navigateCondition(current, cp, terminal, tail, pathAnalysis, evalCtx);
        };
    }

    private static Value navigateKey(Value current, String key, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        val child = obj.get(key);
        if (child == null) {
            return current;
        }
        val result = navigateAndApply(child, terminal, tail, pathAnalysis, evalCtx);
        return replaceObjectKey(obj, key, result);
    }

    private static Value navigateIndex(Value current, int index, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        int actualIndex = normalizeArrayIndex(index, arr.size());
        if (actualIndex < 0 || actualIndex >= arr.size()) {
            return current;
        }
        val result = navigateAndApply(arr.get(actualIndex), terminal, tail, pathAnalysis, evalCtx);
        return replaceArrayIndex(arr, actualIndex, result);
    }

    private static Value navigateWildcard(Value current, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (current instanceof ArrayValue arr) {
            return transformAllArrayElements(arr, evalCtx,
                    (element, ctx) -> navigateAndApply(element, terminal, tail, pathAnalysis, ctx));
        }
        if (current instanceof ObjectValue obj) {
            return transformAllObjectEntries(obj, evalCtx,
                    (value, ctx) -> navigateAndApply(value, terminal, tail, pathAnalysis, ctx));
        }
        return current;
    }

    private static Value navigateAttributeUnion(Value current, List<String> keys, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        return transformSelectedObjectEntries(obj, new HashSet<>(keys), evalCtx,
                (value, ctx) -> navigateAndApply(value, terminal, tail, pathAnalysis, ctx));
    }

    private static Value navigateIndexUnion(Value current, List<Integer> indices, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        val indexSet = normalizeIndexSet(indices, arr.size());
        return transformSelectedArrayElements(arr, indexSet, evalCtx,
                (element, ctx) -> navigateAndApply(element, terminal, tail, pathAnalysis, ctx));
    }

    private static Value navigateSlice(Value current, SlicePath slice, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        val indexSet = computeSliceIndexSet(arr.size(), slice.from(), slice.to(), slice.step());
        if (indexSet.isEmpty()) {
            return current;
        }
        return transformSelectedArrayElements(arr, indexSet, evalCtx,
                (element, ctx) -> navigateAndApply(element, terminal, tail, pathAnalysis, ctx));
    }

    private static Value navigateExpression(Value current, ExpressionPath ep, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        val indexValue = evaluateCompiledPathElement(ep, pathAnalysis, evalCtx);
        if (indexValue instanceof ErrorValue || indexValue instanceof UndefinedValue) {
            return indexValue instanceof ErrorValue ? indexValue : current;
        }
        if (indexValue instanceof NumberValue(BigDecimal number)) {
            final int index;
            try {
                index = number.intValueExact();
            } catch (ArithmeticException ignored) {
                // A fractional or out-of-int-range subscript addresses no element. Leave it
                // untouched.
                return current;
            }
            return navigateIndex(current, index, terminal, tail, pathAnalysis, evalCtx);
        }
        if (indexValue instanceof TextValue(String text)) {
            return navigateKey(current, text, terminal, tail, pathAnalysis, evalCtx);
        }
        return current;
    }

    private static Value navigateCondition(Value current, ConditionPath cp, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        if (current instanceof ArrayValue arr) {
            return filterArrayByCondition(arr, cp, pathAnalysis, evalCtx,
                    (element, ctx) -> navigateAndApply(element, terminal, tail, pathAnalysis, ctx));
        }
        if (current instanceof ObjectValue obj) {
            return filterObjectByCondition(obj, cp, pathAnalysis, evalCtx,
                    (value, ctx) -> navigateAndApply(value, terminal, tail, pathAnalysis, ctx));
        }
        return current;
    }

    private static Value navigateRecursiveKey(Value current, String key, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(pathAnalysis.filterLocation(), ERROR_MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ObjectValue obj) {
            return recursiveKeyInObject(obj, key, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        if (current instanceof ArrayValue arr) {
            return recursiveKeyInArray(arr, key, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        return current;
    }

    private static Value recursiveKeyInObject(ObjectValue obj, String key, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            Value result;
            if (entry.getKey().equals(key)) {
                result = navigateAndApply(entry.getValue(), terminal, tail, pathAnalysis, evalCtx);
            } else {
                result = navigateRecursiveKey(entry.getValue(), key, terminal, tail, pathAnalysis, evalCtx, depth + 1);
            }
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.put(entry.getKey(), result);
            }
        }
        return builder.build();
    }

    private static Value recursiveKeyInArray(ArrayValue arr, String key, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        val builder = ArrayValue.builder();
        for (val element : arr) {
            val result = navigateRecursiveKey(element, key, terminal, tail, pathAnalysis, evalCtx, depth + 1);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
            }
        }
        return builder.build();
    }

    private static Value navigateRecursiveIndex(Value current, int index, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(pathAnalysis.filterLocation(), ERROR_MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            return recursiveIndexInArray(arr, index, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        if (current instanceof ObjectValue obj) {
            return recursiveIndexInObject(obj, index, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        return current;
    }

    private static Value recursiveIndexInArray(ArrayValue arr, int index, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        int actualIndex = normalizeArrayIndex(index, arr.size());
        val builder     = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            Value result;
            if (i == actualIndex) {
                result = navigateAndApply(arr.get(i), terminal, tail, pathAnalysis, evalCtx);
            } else {
                result = navigateRecursiveIndex(arr.get(i), index, terminal, tail, pathAnalysis, evalCtx, depth + 1);
            }
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
            }
        }
        return builder.build();
    }

    private static Value recursiveIndexInObject(ObjectValue obj, int index, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            val result = navigateRecursiveIndex(entry.getValue(), index, terminal, tail, pathAnalysis, evalCtx,
                    depth + 1);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.put(entry.getKey(), result);
            }
        }
        return builder.build();
    }

    private static Value navigateRecursiveWildcard(Value current, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(pathAnalysis.filterLocation(), ERROR_MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            return recursiveWildcardInArray(arr, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        if (current instanceof ObjectValue obj) {
            return recursiveWildcardInObject(obj, terminal, tail, pathAnalysis, evalCtx, depth);
        }
        return current;
    }

    private static Value recursiveWildcardInArray(ArrayValue arr, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        val builder = ArrayValue.builder();
        for (val element : arr) {
            val recursed = navigateRecursiveWildcard(element, terminal, tail, pathAnalysis, evalCtx, depth + 1);
            if (recursed instanceof ErrorValue) {
                return recursed;
            }
            val result = navigateAndApply(recursed, terminal, tail, pathAnalysis, evalCtx);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
            }
        }
        return builder.build();
    }

    private static Value recursiveWildcardInObject(ObjectValue obj, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, EvaluationContext evalCtx, int depth) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            val recursed = navigateRecursiveWildcard(entry.getValue(), terminal, tail, pathAnalysis, evalCtx,
                    depth + 1);
            if (recursed instanceof ErrorValue) {
                return recursed;
            }
            val result = navigateAndApply(recursed, terminal, tail, pathAnalysis, evalCtx);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.put(entry.getKey(), result);
            }
        }
        return builder.build();
    }

    private static Value transformAllArrayElements(ArrayValue arr, EvaluationContext evalCtx,
            BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            val element  = arr.get(i);
            val localCtx = evalCtx.withRelativeValue(element, Value.of(i));
            val result   = transform.apply(element, localCtx);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.add(result);
            }
        }
        return builder.build();
    }

    private static Value transformAllObjectEntries(ObjectValue obj, EvaluationContext evalCtx,
            BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            val localCtx = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
            val result   = transform.apply(entry.getValue(), localCtx);
            if (result instanceof ErrorValue) {
                return result;
            }
            if (!(result instanceof UndefinedValue)) {
                builder.put(entry.getKey(), result);
            }
        }
        return builder.build();
    }

    private static Value transformSelectedArrayElements(ArrayValue arr, Set<Integer> selectedIndices,
            EvaluationContext evalCtx, BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            val element = arr.get(i);
            if (selectedIndices.contains(i)) {
                val localCtx = evalCtx.withRelativeValue(element, Value.of(i));
                val result   = transform.apply(element, localCtx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.add(result);
                }
            } else {
                builder.add(element);
            }
        }
        return builder.build();
    }

    private static Value transformSelectedObjectEntries(ObjectValue obj, Set<String> selectedKeys,
            EvaluationContext evalCtx, BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            if (selectedKeys.contains(entry.getKey())) {
                val localCtx = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val result   = transform.apply(entry.getValue(), localCtx);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), result);
                }
            } else {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private static Value filterArrayByCondition(ArrayValue arr, ConditionPath cp, PathAnalysis pathAnalysis,
            EvaluationContext evalCtx, BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            val element    = arr.get(i);
            val localCtx   = evalCtx.withRelativeValue(element, Value.of(i));
            val condResult = evaluateCompiledPathElement(cp, pathAnalysis, localCtx);
            if (condResult instanceof ErrorValue) {
                return condResult;
            }
            if (condResult instanceof BooleanValue(boolean matches)) {
                if (matches) {
                    val result = transform.apply(element, localCtx);
                    if (result instanceof ErrorValue) {
                        return result;
                    }
                    if (!(result instanceof UndefinedValue)) {
                        builder.add(result);
                    }
                } else {
                    builder.add(element);
                }
            } else {
                return Value.errorAt(pathAnalysis.filterLocation(), ERROR_CONDITION_NON_BOOLEAN,
                        condResult.getClass().getSimpleName());
            }
        }
        return builder.build();
    }

    private static Value filterObjectByCondition(ObjectValue obj, ConditionPath cp, PathAnalysis pathAnalysis,
            EvaluationContext evalCtx, BiFunction<Value, EvaluationContext, Value> transform) {
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            val localCtx   = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
            val condResult = evaluateCompiledPathElement(cp, pathAnalysis, localCtx);
            if (condResult instanceof ErrorValue) {
                return condResult;
            }
            if (condResult instanceof BooleanValue(boolean matches)) {
                if (matches) {
                    val result = transform.apply(entry.getValue(), localCtx);
                    if (result instanceof ErrorValue) {
                        return result;
                    }
                    if (!(result instanceof UndefinedValue)) {
                        builder.put(entry.getKey(), result);
                    }
                } else {
                    builder.put(entry.getKey(), entry.getValue());
                }
            } else {
                return Value.errorAt(pathAnalysis.filterLocation(), ERROR_CONDITION_NON_BOOLEAN,
                        condResult.getClass().getSimpleName());
            }
        }
        return builder.build();
    }

    private static Value evaluateCompiledPathElement(PathElement element, PathAnalysis pathAnalysis,
            EvaluationContext evalCtx) {
        val compiled = pathAnalysis.compiledElements().get(element);
        if (compiled instanceof Value v) {
            return v;
        }
        if (compiled instanceof PureOperator po) {
            return po.evaluate(evalCtx);
        }
        return Value.errorAt(element.location(), ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_PATH);
    }

    private static Value replaceObjectKey(ObjectValue obj, String key, Value result) {
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildObjectWithout(obj, key);
        }
        return rebuildObjectWith(obj, key, result);
    }

    private static Value replaceArrayIndex(ArrayValue arr, int index, Value result) {
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildArrayWithout(arr, index);
        }
        return rebuildArrayWith(arr, index, result);
    }

    private static ObjectValue rebuildObjectWith(ObjectValue original, String key, Value newValue) {
        val builder = ObjectValue.builder();
        for (val entry : original.entrySet()) {
            builder.put(entry.getKey(), entry.getKey().equals(key) ? newValue : entry.getValue());
        }
        return builder.build();
    }

    private static ObjectValue rebuildObjectWithout(ObjectValue original, String key) {
        val builder = ObjectValue.builder();
        for (val entry : original.entrySet()) {
            if (!entry.getKey().equals(key)) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private static ArrayValue rebuildArrayWith(ArrayValue original, int index, Value newValue) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < original.size(); i++) {
            builder.add(i == index ? newValue : original.get(i));
        }
        return builder.build();
    }

    private static ArrayValue rebuildArrayWithout(ArrayValue original, int index) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < original.size(); i++) {
            if (i != index) {
                builder.add(original.get(i));
            }
        }
        return builder.build();
    }

    private static int normalizeArrayIndex(int index, int size) {
        return index >= 0 ? index : size + index;
    }

    private static Set<Integer> normalizeIndexSet(List<Integer> indices, int size) {
        val indexSet = new HashSet<Integer>();
        for (val idx : indices) {
            int actual = normalizeArrayIndex(idx, size);
            if (actual >= 0 && actual < size) {
                indexSet.add(actual);
            }
        }
        return indexSet;
    }

    private static Set<Integer> computeSliceIndexSet(int size, Integer from, Integer to, Integer step) {
        int stepVal = step != null ? step : 1;
        if (stepVal == 0 || size == 0) {
            return Set.of();
        }
        boolean forward  = stepVal > 0;
        int     fromVal  = normalizeSliceBound(from, size, forward ? 0 : size - 1);
        int     toVal    = normalizeSliceBound(to, size, forward ? size : -1);
        val     indexSet = new HashSet<Integer>();
        for (int i = fromVal; forward ? i < toVal : i > toVal; i += stepVal) {
            indexSet.add(i);
        }
        return indexSet;
    }

    private static int normalizeSliceBound(Integer bound, int size, int defaultVal) {
        if (bound == null) {
            return defaultVal;
        }
        if (bound < 0) {
            return Math.max(0, size + bound);
        }
        return Math.min(bound, size);
    }

    record PathAnalysis(
            Map<PathElement, CompiledExpression> compiledElements,
            boolean isDependingOnSubscription,
            SourceLocation filterLocation) {}

    private static PathAnalysis analyzePath(List<PathElement> path, SourceLocation filterLocation,
            CompilationContext ctx) {
        val compiled              = new HashMap<PathElement, CompiledExpression>();
        var dependsOnSubscription = false;
        for (val element : path) {
            switch (element) {
            case ExpressionPath ep -> {
                val expr = compilePathExpr(ep.expression(), ep.location(), ctx);
                dependsOnSubscription |= expr instanceof PureOperator po && po.isDependingOnSubscription();
                compiled.put(ep, expr);
            }
            case ConditionPath cp  -> {
                val cond = compilePathExpr(cp.condition(), cp.location(), ctx);
                dependsOnSubscription |= cond instanceof PureOperator po && po.isDependingOnSubscription();
                compiled.put(cp, cond);
            }
            default                -> { /* static element, nothing to compile */ }
            }
        }
        return new PathAnalysis(compiled, dependsOnSubscription, filterLocation);
    }

    private static CompiledExpression compilePathExpr(Expression expr, SourceLocation location,
            CompilationContext ctx) {
        val compiled = ExpressionCompiler.compile(expr, ctx);
        if (compiled instanceof StreamOperator) {
            throw new SaplCompilerException(ERROR_STREAM_OPERATORS_NOT_ALLOWED_IN_FILTER_PATH, location);
        }
        return compiled;
    }

}
