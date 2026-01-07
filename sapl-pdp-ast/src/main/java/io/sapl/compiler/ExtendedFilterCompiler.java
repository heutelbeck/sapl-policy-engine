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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.UnaryOperator;

import io.sapl.api.model.*;
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class ExtendedFilterCompiler {

    private static final int   MAX_RECURSION_DEPTH              = 500;
    public static final String MAXIMUM_RECURSION_DEPTH_EXCEEDED = "Maximum recursion depth exceeded";

    public static CompiledExpression compile(ExtendedFilter ef, CompilationContext ctx) {
        val compiledBase = ExpressionCompiler.compile(ef.base(), ctx);
        if (compiledBase instanceof ErrorValue) {
            return compiledBase;
        }

        val tempSimpleFilter = new SimpleFilter(new RelativeReference(RelativeType.VALUE, ef.location()), ef.name(),
                ef.arguments(), ef.each(), ef.location());
        val compiledFilter   = FilterCompiler.compileSimple(tempSimpleFilter, ctx);
        if (compiledFilter instanceof ErrorValue) {
            return compiledFilter;
        }

        val path     = ef.target().elements();
        val location = ef.location();

        return switch (compiledBase) {
        case Value vb           -> switch (compiledFilter) {
                            case Value vf                                                  ->
                                evaluateValueValue(vb, vf, path, location);
                            case PureOperator pof when pof.isDependingOnSubscription()     ->
                                new ExtendedFilterValuePure(vb, pof, path, location);
                            case PureOperator pof                                          ->
                                evaluateValuePureFold(vb, pof, path, location, ctx);
                            case StreamOperator sof                                        ->
                                Value.error("unimplemented");
                            };
        case PureOperator pob   -> switch (compiledFilter) {
                            case Value vf               -> Value.error("unimplemented");
                            case PureOperator pof       -> Value.error("unimplemented");
                            case StreamOperator sof     -> Value.error("unimplemented");
                            };
        case StreamOperator sob -> switch (compiledFilter) {
                            case Value vf               -> Value.error("unimplemented");
                            case PureOperator pof       -> Value.error("unimplemented");
                            case StreamOperator sof     -> Value.error("unimplemented");
                            };
        };
    }

    private static Value evaluateValueValue(Value base, Value filter, List<PathElement> path, SourceLocation location) {
        return navigateAndApply(base, current -> filter, path, location);
    }

    record ExtendedFilterValuePure(
            Value base,
            PureOperator filterOperator,
            List<PathElement> path,
            SourceLocation location) implements PureOperator {
        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return navigateAndApply(base, current -> filterOperator.evaluate(ctx.withRelativeValue(current)), path,
                    location);
        }
    }

    private static CompiledExpression evaluateValuePureFold(Value base, PureOperator filter, List<PathElement> path,
            SourceLocation location, CompilationContext ctx) {
        val tempCtx = new EvaluationContext(null, null, null, null, ctx.getFunctionBroker(), ctx.getAttributeBroker());
        return navigateAndApply(base, current -> filter.evaluate(tempCtx.withRelativeValue(current)), path, location);
    }

    private static Value navigateAndApply(Value current, UnaryOperator<Value> terminal, List<PathElement> path,
            SourceLocation location) {
        if (current instanceof ErrorValue) {
            return current;
        }
        if (path.isEmpty()) {
            return terminal.apply(current);
        }
        val head = path.getFirst();
        val tail = path.subList(1, path.size());
        return switch (head) {
        case KeyPath kp                -> consumeKey(current, kp.key(), terminal, tail, location);
        case IndexPath ip              -> consumeIndex(current, ip.index(), terminal, tail, location);
        case WildcardPath wp           -> consumeWildcard(current, terminal, tail, location);
        case AttributeUnionPath aup    -> consumeAttributeUnion(current, aup.keys(), terminal, tail, location);
        case IndexUnionPath iup        -> consumeIndexUnion(current, iup.indices(), terminal, tail, location);
        case SlicePath sp              -> consumeSlice(current, sp, terminal, tail, location);
        case RecursiveKeyPath rkp      -> consumeRecursiveKey(current, rkp.key(), terminal, tail, location, 0);
        case RecursiveIndexPath rip    -> consumeRecursiveIndex(current, rip.index(), terminal, tail, location, 0);
        case RecursiveWildcardPath rwp -> consumeRecursiveWildcard(current, terminal, tail, location, 0);
        case ExpressionPath ep         -> current;
        case ConditionPath cp          -> current;
        };
    }

    private static Value consumeKey(Value current, String key, UnaryOperator<Value> terminal, List<PathElement> tail,
            SourceLocation location) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        val child = obj.get(key);
        if (child == null) {
            return current;
        }
        val result = navigateAndApply(child, terminal, tail, location);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildObjectWithout(obj, key);
        }
        return rebuildObjectWith(obj, key, result);
    }

    private static Value consumeIndex(Value current, int index, UnaryOperator<Value> terminal, List<PathElement> tail,
            SourceLocation location) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        int actualIndex = index >= 0 ? index : arr.size() + index;
        if (actualIndex < 0 || actualIndex >= arr.size()) {
            return current;
        }
        val result = navigateAndApply(arr.get(actualIndex), terminal, tail, location);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildArrayWithout(arr, actualIndex);
        }
        return rebuildArrayWith(arr, actualIndex, result);
    }

    private static Value consumeWildcard(Value current, UnaryOperator<Value> terminal, List<PathElement> tail,
            SourceLocation location) {
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (Value value : arr) {
                val result = navigateAndApply(value, terminal, tail, location);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.add(result);
                }
            }
            return builder.build();
        }
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                val result = navigateAndApply(entry.getValue(), terminal, tail, location);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.put(entry.getKey(), result);
                }
            }
            return builder.build();
        }
        return current;
    }

    private static Value consumeAttributeUnion(Value current, List<String> keys, UnaryOperator<Value> terminal,
            List<PathElement> tail, SourceLocation location) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        val keySet  = new HashSet<>(keys);
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            if (keySet.contains(entry.getKey())) {
                val result = navigateAndApply(entry.getValue(), terminal, tail, location);
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

    private static Value consumeIndexUnion(Value current, List<Integer> indices, UnaryOperator<Value> terminal,
            List<PathElement> tail, SourceLocation location) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        val indexSet = new HashSet<Integer>();
        for (val idx : indices) {
            int actual = idx >= 0 ? idx : arr.size() + idx;
            if (actual >= 0 && actual < arr.size()) {
                indexSet.add(actual);
            }
        }
        val builder = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            if (indexSet.contains(i)) {
                val result = navigateAndApply(arr.get(i), terminal, tail, location);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.add(result);
                }
            } else {
                builder.add(arr.get(i));
            }
        }
        return builder.build();
    }

    private static Value consumeSlice(Value current, SlicePath slice, UnaryOperator<Value> terminal,
            List<PathElement> tail, SourceLocation location) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        val indices = computeSliceIndices(arr.size(), slice.from(), slice.to(), slice.step());
        if (indices.isEmpty()) {
            return current;
        }
        val indexSet = new HashSet<>(indices);
        val builder  = ArrayValue.builder();
        for (int i = 0; i < arr.size(); i++) {
            if (indexSet.contains(i)) {
                val result = navigateAndApply(arr.get(i), terminal, tail, location);
                if (result instanceof ErrorValue) {
                    return result;
                }
                if (!(result instanceof UndefinedValue)) {
                    builder.add(result);
                }
            } else {
                builder.add(arr.get(i));
            }
        }
        return builder.build();
    }

    private static List<Integer> computeSliceIndices(int size, Integer from, Integer to, Integer step) {
        int stepVal = step != null ? step : 1;
        if (stepVal == 0) {
            return List.of();
        }
        int fromVal = normalizeSliceIndex(from, size, stepVal > 0 ? 0 : size - 1);
        int toVal   = normalizeSliceIndex(to, size, stepVal > 0 ? size : -size - 1);
        val indices = new ArrayList<Integer>();
        if (stepVal > 0) {
            for (int i = fromVal; i < toVal && i < size; i += stepVal) {
                if (i >= 0)
                    indices.add(i);
            }
        } else {
            for (int i = fromVal; i > toVal && i >= 0; i += stepVal) {
                if (i < size)
                    indices.add(i);
            }
        }
        return indices;
    }

    private static int normalizeSliceIndex(Integer index, int size, int defaultVal) {
        if (index == null)
            return defaultVal;
        if (index < 0)
            return Math.max(0, size + index);
        return Math.min(index, size);
    }

    private static Value consumeRecursiveKey(Value current, String key, UnaryOperator<Value> terminal,
            List<PathElement> tail, SourceLocation location, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                if (entry.getKey().equals(key)) {
                    val result = navigateAndApply(entry.getValue(), terminal, tail, location);
                    if (result instanceof ErrorValue)
                        return result;
                    if (!(result instanceof UndefinedValue))
                        builder.put(entry.getKey(), result);
                } else {
                    val recursed = consumeRecursiveKey(entry.getValue(), key, terminal, tail, location, depth + 1);
                    if (recursed instanceof ErrorValue)
                        return recursed;
                    if (!(recursed instanceof UndefinedValue))
                        builder.put(entry.getKey(), recursed);
                }
            }
            return builder.build();
        }
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (val element : arr) {
                val recursed = consumeRecursiveKey(element, key, terminal, tail, location, depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                if (!(recursed instanceof UndefinedValue))
                    builder.add(recursed);
            }
            return builder.build();
        }
        return current;
    }

    private static Value consumeRecursiveIndex(Value current, int index, UnaryOperator<Value> terminal,
            List<PathElement> tail, SourceLocation location, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            int actualIndex = index >= 0 ? index : arr.size() + index;
            val builder     = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                if (i == actualIndex) {
                    val result = navigateAndApply(arr.get(i), terminal, tail, location);
                    if (result instanceof ErrorValue)
                        return result;
                    if (!(result instanceof UndefinedValue))
                        builder.add(result);
                } else {
                    val recursed = consumeRecursiveIndex(arr.get(i), index, terminal, tail, location, depth + 1);
                    if (recursed instanceof ErrorValue)
                        return recursed;
                    if (!(recursed instanceof UndefinedValue))
                        builder.add(recursed);
                }
            }
            return builder.build();
        }
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                val recursed = consumeRecursiveIndex(entry.getValue(), index, terminal, tail, location, depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                if (!(recursed instanceof UndefinedValue))
                    builder.put(entry.getKey(), recursed);
            }
            return builder.build();
        }
        return current;
    }

    private static Value consumeRecursiveWildcard(Value current, UnaryOperator<Value> terminal, List<PathElement> tail,
            SourceLocation location, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (val element : arr) {
                val recursed = consumeRecursiveWildcard(element, terminal, tail, location, depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                val result = navigateAndApply(recursed, terminal, tail, location);
                if (result instanceof ErrorValue)
                    return result;
                if (!(result instanceof UndefinedValue))
                    builder.add(result);
            }
            return builder.build();
        }
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                val recursed = consumeRecursiveWildcard(entry.getValue(), terminal, tail, location, depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                val result = navigateAndApply(recursed, terminal, tail, location);
                if (result instanceof ErrorValue)
                    return result;
                if (!(result instanceof UndefinedValue))
                    builder.put(entry.getKey(), result);
            }
            return builder.build();
        }
        return current;
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
            if (!entry.getKey().equals(key))
                builder.put(entry.getKey(), entry.getValue());
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
            if (i != index)
                builder.add(original.get(i));
        }
        return builder.build();
    }

}
