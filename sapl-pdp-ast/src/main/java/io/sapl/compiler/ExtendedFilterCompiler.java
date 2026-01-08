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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import io.sapl.api.model.*;
import io.sapl.ast.*;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class ExtendedFilterCompiler {

    private static final int   MAX_RECURSION_DEPTH              = 500;
    public static final String MAXIMUM_RECURSION_DEPTH_EXCEEDED = "Maximum recursion depth exceeded";
    public static final ErrorValue UNIMPLEMENTED = Value.error("unimplemented");

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

        val path         = ef.target().elements();
        val location     = ef.location();
        val pathAnalysis = analyzePath(path, ctx);

        val canFoldPath = !pathAnalysis.isDependingOnSubscription();

        return switch (compiledBase) {
        case Value vb           -> switch (compiledFilter) {
                            case Value vf when canFoldPath                                                 ->
                                evaluateValueValue(vb, vf, path, pathAnalysis, location, ctx);
                            case Value vf                                                                  ->
                                UNIMPLEMENTED; // Value × Value with sub-dep path
                            case PureOperator pof when !pof.isDependingOnSubscription() && canFoldPath     ->
                                evaluateValuePureFold(vb, pof, path, pathAnalysis, location, ctx);
                            case PureOperator pof                                                          ->
                                new ExtendedFilterValuePure(vb, pof, path, pathAnalysis, location);
                            case StreamOperator sof                                                        ->
                                UNIMPLEMENTED;
                            };
        case PureOperator pob   -> switch (compiledFilter) {
                            case Value vf               -> UNIMPLEMENTED;
                            case PureOperator pof       -> UNIMPLEMENTED;
                            case StreamOperator sof     -> UNIMPLEMENTED;
                            };
        case StreamOperator sob -> switch (compiledFilter) {
                            case Value vf               -> UNIMPLEMENTED;
                            case PureOperator pof       -> UNIMPLEMENTED;
                            case StreamOperator sof     -> UNIMPLEMENTED;
                            };
        };
    }

    private static Value evaluateValueValue(Value base, Value filter, List<PathElement> path, PathAnalysis pathAnalysis,
            SourceLocation location, CompilationContext ctx) {
        val evalCtx = new EvaluationContext(null, null, null, null, ctx.getFunctionBroker(), ctx.getAttributeBroker())
                .withRelativeValue(base);
        return navigateAndApply(base, current -> filter, path, pathAnalysis, location, evalCtx);
    }

    record ExtendedFilterValuePure(
            Value base,
            PureOperator filterOperator,
            List<PathElement> path,
            PathAnalysis pathAnalysis,
            SourceLocation location) implements PureOperator {
        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val initialCtx = ctx.withRelativeValue(base);
            return navigateAndApply(base, current -> filterOperator.evaluate(initialCtx.withRelativeValue(current)),
                    path, pathAnalysis, location, initialCtx);
        }
    }

    private static CompiledExpression evaluateValuePureFold(Value base, PureOperator filter, List<PathElement> path,
            PathAnalysis pathAnalysis, SourceLocation location, CompilationContext ctx) {
        val evalCtx = new EvaluationContext(null, null, null, null, ctx.getFunctionBroker(), ctx.getAttributeBroker())
                .withRelativeValue(base);
        return navigateAndApply(base, current -> filter.evaluate(evalCtx.withRelativeValue(current)), path,
                pathAnalysis, location, evalCtx);
    }

    private static Value navigateAndApply(Value current, UnaryOperator<Value> terminal, List<PathElement> path,
            PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (current instanceof ErrorValue) {
            return current;
        }
        if (path.isEmpty()) {
            return terminal.apply(current);
        }
        val head = path.getFirst();
        val tail = path.subList(1, path.size());
        return switch (head) {
        case KeyPath kp                ->
            consumeKey(current, kp.key(), terminal, tail, pathAnalysis, location, evalCtx);
        case IndexPath ip              ->
            consumeIndex(current, ip.index(), terminal, tail, pathAnalysis, location, evalCtx);
        case WildcardPath wp           -> consumeWildcard(current, terminal, tail, pathAnalysis, location, evalCtx);
        case AttributeUnionPath aup    ->
            consumeAttributeUnion(current, aup.keys(), terminal, tail, pathAnalysis, location, evalCtx);
        case IndexUnionPath iup        ->
            consumeIndexUnion(current, iup.indices(), terminal, tail, pathAnalysis, location, evalCtx);
        case SlicePath sp              -> consumeSlice(current, sp, terminal, tail, pathAnalysis, location, evalCtx);
        case RecursiveKeyPath rkp      ->
            consumeRecursiveKey(current, rkp.key(), terminal, tail, pathAnalysis, location, evalCtx, 0);
        case RecursiveIndexPath rip    ->
            consumeRecursiveIndex(current, rip.index(), terminal, tail, pathAnalysis, location, evalCtx, 0);
        case RecursiveWildcardPath rwp ->
            consumeRecursiveWildcard(current, terminal, tail, pathAnalysis, location, evalCtx, 0);
        case ExpressionPath ep         ->
            consumeExpression(current, ep, terminal, tail, pathAnalysis, location, evalCtx);
        case ConditionPath cp          ->
            consumeCondition(current, cp, terminal, tail, pathAnalysis, location, evalCtx);
        };
    }

    private static Value consumeKey(Value current, String key, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        val child = obj.get(key);
        if (child == null) {
            return current;
        }
        val result = navigateAndApply(child, terminal, tail, pathAnalysis, location, evalCtx);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildObjectWithout(obj, key);
        }
        return rebuildObjectWith(obj, key, result);
    }

    private static Value consumeIndex(Value current, int index, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (!(current instanceof ArrayValue arr)) {
            return current;
        }
        int actualIndex = index >= 0 ? index : arr.size() + index;
        if (actualIndex < 0 || actualIndex >= arr.size()) {
            return current;
        }
        val result = navigateAndApply(arr.get(actualIndex), terminal, tail, pathAnalysis, location, evalCtx);
        if (result instanceof ErrorValue) {
            return result;
        }
        if (result instanceof UndefinedValue) {
            return rebuildArrayWithout(arr, actualIndex);
        }
        return rebuildArrayWith(arr, actualIndex, result);
    }

    private static Value consumeWildcard(Value current, UnaryOperator<Value> terminal, List<PathElement> tail,
            PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                val element  = arr.get(i);
                val localCtx = evalCtx.withRelativeValue(element, Value.of(i));
                val result   = navigateAndApply(element, terminal, tail, pathAnalysis, location, localCtx);
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
                val localCtx = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val result   = navigateAndApply(entry.getValue(), terminal, tail, pathAnalysis, location, localCtx);
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
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (!(current instanceof ObjectValue obj)) {
            return current;
        }
        val keySet  = new HashSet<>(keys);
        val builder = ObjectValue.builder();
        for (val entry : obj.entrySet()) {
            if (keySet.contains(entry.getKey())) {
                val localCtx = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val result   = navigateAndApply(entry.getValue(), terminal, tail, pathAnalysis, location, localCtx);
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
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
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
            val element = arr.get(i);
            if (indexSet.contains(i)) {
                val localCtx = evalCtx.withRelativeValue(element, Value.of(i));
                val result   = navigateAndApply(element, terminal, tail, pathAnalysis, location, localCtx);
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

    private static Value consumeSlice(Value current, SlicePath slice, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
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
            val element = arr.get(i);
            if (indexSet.contains(i)) {
                val localCtx = evalCtx.withRelativeValue(element, Value.of(i));
                val result   = navigateAndApply(element, terminal, tail, pathAnalysis, location, localCtx);
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
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx,
            int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                if (entry.getKey().equals(key)) {
                    val result = navigateAndApply(entry.getValue(), terminal, tail, pathAnalysis, location, evalCtx);
                    if (result instanceof ErrorValue)
                        return result;
                    if (!(result instanceof UndefinedValue))
                        builder.put(entry.getKey(), result);
                } else {
                    val recursed = consumeRecursiveKey(entry.getValue(), key, terminal, tail, pathAnalysis, location,
                            evalCtx, depth + 1);
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
                val recursed = consumeRecursiveKey(element, key, terminal, tail, pathAnalysis, location, evalCtx,
                        depth + 1);
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
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx,
            int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            int actualIndex = index >= 0 ? index : arr.size() + index;
            val builder     = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                if (i == actualIndex) {
                    val result = navigateAndApply(arr.get(i), terminal, tail, pathAnalysis, location, evalCtx);
                    if (result instanceof ErrorValue)
                        return result;
                    if (!(result instanceof UndefinedValue))
                        builder.add(result);
                } else {
                    val recursed = consumeRecursiveIndex(arr.get(i), index, terminal, tail, pathAnalysis, location,
                            evalCtx, depth + 1);
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
                val recursed = consumeRecursiveIndex(entry.getValue(), index, terminal, tail, pathAnalysis, location,
                        evalCtx, depth + 1);
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
            PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            return Value.errorAt(location, MAXIMUM_RECURSION_DEPTH_EXCEEDED);
        }
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (val element : arr) {
                val recursed = consumeRecursiveWildcard(element, terminal, tail, pathAnalysis, location, evalCtx,
                        depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                val result = navigateAndApply(recursed, terminal, tail, pathAnalysis, location, evalCtx);
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
                val recursed = consumeRecursiveWildcard(entry.getValue(), terminal, tail, pathAnalysis, location,
                        evalCtx, depth + 1);
                if (recursed instanceof ErrorValue)
                    return recursed;
                val result = navigateAndApply(recursed, terminal, tail, pathAnalysis, location, evalCtx);
                if (result instanceof ErrorValue)
                    return result;
                if (!(result instanceof UndefinedValue))
                    builder.put(entry.getKey(), result);
            }
            return builder.build();
        }
        return current;
    }

    private static Value consumeExpression(Value current, ExpressionPath ep, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        val compiled = pathAnalysis.compiledElements().get(ep);
        Value indexValue;
        switch (compiled) {
            case Value v -> indexValue = v;
            case PureOperator po -> indexValue = po.evaluate(evalCtx);
            default -> {
                return Value.errorAt(ep.location(), "StreamOperator in path expression not supported");
            }
        }
        if (indexValue instanceof ErrorValue) {
            return indexValue;
        }
        if (indexValue instanceof UndefinedValue) {
            return current;
        }
        if (indexValue instanceof NumberValue(BigDecimal number)) {
            return consumeIndex(current, number.intValue(), terminal, tail, pathAnalysis, location, evalCtx);
        }
        if (indexValue instanceof TextValue(String text)) {
            return consumeKey(current, text, terminal, tail, pathAnalysis, location, evalCtx);
        }
        return current;
    }

    private static Value consumeCondition(Value current, ConditionPath cp, UnaryOperator<Value> terminal,
            List<PathElement> tail, PathAnalysis pathAnalysis, SourceLocation location, EvaluationContext evalCtx) {
        if (current instanceof ArrayValue arr) {
            val builder = ArrayValue.builder();
            for (int i = 0; i < arr.size(); i++) {
                val element    = arr.get(i);
                val localCtx   = evalCtx.withRelativeValue(element, Value.of(i));
                val condResult = evaluateCondition(cp, pathAnalysis, localCtx);
                if (condResult instanceof ErrorValue) {
                    return condResult;
                }
                if (condResult instanceof BooleanValue(boolean value) && value) {
                    val result = navigateAndApply(element, terminal, tail, pathAnalysis, location, localCtx);
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
        if (current instanceof ObjectValue obj) {
            val builder = ObjectValue.builder();
            for (val entry : obj.entrySet()) {
                val localCtx   = evalCtx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
                val condResult = evaluateCondition(cp, pathAnalysis, localCtx);
                if (condResult instanceof ErrorValue) {
                    return condResult;
                }
                if (condResult instanceof BooleanValue(boolean value) && value) {
                    val result = navigateAndApply(entry.getValue(), terminal, tail, pathAnalysis, location, localCtx);
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
        return current;
    }

    private static Value evaluateCondition(ConditionPath cp, PathAnalysis pathAnalysis, EvaluationContext evalCtx) {
        val compiled = pathAnalysis.compiledElements().get(cp);
        return switch (compiled) {
            case Value v -> v;
            case PureOperator po -> po.evaluate(evalCtx);
            default -> Value.errorAt(cp.location(), "StreamOperator in path condition not supported");
        };
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

    record PathAnalysis(Map<PathElement, CompiledExpression> compiledElements, boolean isDependingOnSubscription) {
        boolean isStatic() {
            return compiledElements.isEmpty();
        }
    }

    private static PathAnalysis analyzePath(List<PathElement> path, CompilationContext ctx) {
        val compiled             = new HashMap<PathElement, CompiledExpression>();
        var dependsOnSubcription = false;
        for (val element : path) {
            switch (element) {
            case ExpressionPath ep -> {
                val expr = ExpressionCompiler.compile(ep.expression(), ctx);
                if (expr instanceof StreamOperator) {
                    throw new SaplCompilerException("Stream operators not allowed in filter path expressions",
                            ep.location());
                }
                if (expr instanceof PureOperator po && po.isDependingOnSubscription()) {
                    dependsOnSubcription = true;
                }
                compiled.put(ep, expr);
            }
            case ConditionPath cp  -> {
                val cond = ExpressionCompiler.compile(cp.condition(), ctx);
                if (cond instanceof StreamOperator) {
                    throw new SaplCompilerException("Stream operators not allowed in filter path conditions",
                            cp.location());
                }
                if (cond instanceof PureOperator po && po.isDependingOnSubscription()) {
                    dependsOnSubcription = true;
                }
                compiled.put(cp, cond);
            }
            default                -> { /* static element, nothing to compile */ }
            }
        }
        return new PathAnalysis(compiled, dependsOnSubcription);
    }

}
