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
import io.sapl.ast.Expression;
import io.sapl.ast.RelativeReference;
import io.sapl.ast.RelativeType;
import io.sapl.ast.SimpleFilter;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.util.DummyEvaluationContextFactory;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.evalChild;
import static io.sapl.api.model.StreamOperator.mergeDependencies;

@UtilityClass
public class FilterCompiler {

    private static final String ERROR_PURE_FILTER_RECEIVED_STREAM_OPERATOR = "EachPure cannot contain StreamOperator. Indicates an implementation bug.";

    public static CompiledExpression compileSimple(SimpleFilter sf, CompilationContext ctx) {
        return ctx.foldCacheDedupe(compileSimpleUnfolded(sf, ctx));
    }

    public static CompiledExpression compileSimpleUnfolded(SimpleFilter sf, CompilationContext ctx) {
        if (sf.each()) {
            return compileSimpleForEach(sf, ctx);
        } else {
            return compileTrueSimple(sf, ctx);
        }
    }

    private static CompiledExpression compileTrueSimple(SimpleFilter sf, CompilationContext ctx) {
        val arguments = new ArrayList<Expression>();
        arguments.add(sf.base());
        arguments.addAll(sf.arguments());
        return FunctionCallCompiler.compile(sf.name().full(), arguments, sf.location(), ctx);
    }

    private static CompiledExpression compileSimpleForEach(SimpleFilter sf, CompilationContext ctx) {
        val base = ExpressionCompiler.compile(sf.base(), ctx);
        if (base instanceof ErrorValue || base instanceof UndefinedValue) {
            return base;
        }
        val arguments = new ArrayList<Expression>();
        arguments.add(new RelativeReference(RelativeType.VALUE, sf.base().location()));
        val location = sf.location();
        arguments.addAll(sf.arguments());
        val filter = FunctionCallCompiler.compile(sf.name().full(), arguments, location, ctx);

        if (base instanceof Value vb && filter instanceof Value vf) {
            return evaluateEachValueValue(vb, vf);
        }
        if (base instanceof Value vb && filter instanceof PureOperator pof && !pof.isDependingOnSubscription()) {
            return compileEachValuePureFold(vb, pof, ctx);
        }
        if (base instanceof StreamOperator || filter instanceof StreamOperator) {
            return new EachStream(base, filter, location);
        }
        val baseDep   = base instanceof PureOperator pob && pob.isDependingOnSubscription();
        val filterDep = filter instanceof PureOperator pof && pof.isDependingOnSubscription();
        val baseRel   = base instanceof PureOperator pob && pob.isRelativeExpression();
        return new EachPure(base, filter, location, baseDep || filterDep, baseRel);
    }

    /**
     * Pure-stratum each-filter. Both {@code base} and {@code filter} are
     * {@link Value} or {@link PureOperator}; the {@link StreamOperator}
     * branch in element dispatch is unreachable by construction and folds
     * to an {@link ErrorValue} defensively.
     */
    record EachPure(
            CompiledExpression base,
            CompiledExpression filter,
            SourceLocation location,
            boolean isDependingOnSubscription,
            boolean isRelativeExpression) implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(EachPure.class);

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val baseValue = switch (base) {
            case Value v                -> v;
            case PureOperator p         -> p.evaluate(ctx);
            case StreamOperator ignored -> Value.error(ERROR_PURE_FILTER_RECEIVED_STREAM_OPERATOR);
            };
            if (baseValue instanceof ErrorValue) {
                return baseValue;
            }
            return switch (filter) {
            case Value vf               -> evaluateEachValueValue(baseValue, vf);
            case PureOperator pf        -> evaluateEachValuePure(baseValue, pf, ctx);
            case StreamOperator ignored -> Value.error(ERROR_PURE_FILTER_RECEIVED_STREAM_OPERATOR);
            };
        }

        @Override
        public long semanticHash() {
            val baseHash   = base instanceof Value v ? SemanticHashing.valueHash(v)
                    : ((PureOperator) base).semanticHash();
            val filterHash = filter instanceof Value v ? SemanticHashing.valueHash(v)
                    : ((PureOperator) filter).semanticHash();
            return SemanticHashing.ordered(KIND, baseHash, filterHash);
        }
    }

    /**
     * Stream-stratum each-filter. At least one of {@code base} or
     * {@code filter} is a {@link StreamOperator}; {@link #evaluate}
     * walks every child via {@link StreamOperator#evalChild} to
     * accumulate the maximum subscription set, holds the first
     * {@link ErrorValue} from any per-element evaluation, returns it
     * after the full walk. {@code null} from a child sets the
     * incomplete flag. {@link UndefinedValue} elements are dropped per
     * filter semantics.
     */
    record EachStream(CompiledExpression base, CompiledExpression filter, SourceLocation location)
            implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            val deps      = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            val baseValue = evalChild(base, ctx, deps);
            if (baseValue == null || baseValue instanceof ErrorValue) {
                return new ExpressionResult(baseValue, deps);
            }
            return switch (filter) {
            case Value vf          -> new ExpressionResult(evaluateEachValueValue(baseValue, vf), deps);
            case PureOperator pf   -> new ExpressionResult(evaluateEachValuePure(baseValue, pf, ctx), deps);
            case StreamOperator sf -> evaluateEachStreamFilter(baseValue, sf, ctx, deps);
            };
        }
    }

    /**
     * Per-element evaluation of a stream filter against a base value.
     * Dispatches on base shape (Array, Object, scalar, ErrorValue), walks
     * every element to accumulate the maximum subscription set, holds the
     * first {@link ErrorValue} from any per-element evaluation, returns it
     * after the full walk. {@code null} from a per-element evaluation sets
     * the incomplete flag. {@link UndefinedValue} elements are dropped per
     * filter semantics. Precedence at the end:
     * error &gt; null &gt; assembled result.
     */
    private static ExpressionResult evaluateEachStreamFilter(Value base, StreamOperator filterOperator,
            EvaluationContext ctx, Map<SubscriptionKey, List<Occurrence>> deps) {
        if (base instanceof ErrorValue) {
            return new ExpressionResult(base, deps);
        }
        if (base instanceof ArrayValue av) {
            return evaluateEachStreamFilterArray(av, filterOperator, ctx, deps);
        }
        if (base instanceof ObjectValue ov) {
            return evaluateEachStreamFilterObject(ov, filterOperator, ctx, deps);
        }
        val r = filterOperator.evaluate(ctx.withRelativeValue(base));
        mergeDependencies(deps, r.dependencies());
        return new ExpressionResult(r.result(), deps);
    }

    private static ExpressionResult evaluateEachStreamFilterArray(ArrayValue av, StreamOperator filterOperator,
            EvaluationContext ctx, Map<SubscriptionKey, List<Occurrence>> deps) {
        val     builder    = new ArrayValue.Builder();
        boolean seenNull   = false;
        Value   firstError = null;
        for (int i = 0; i < av.size(); i++) {
            val perElementCtx = ctx.withRelativeValue(av.get(i), Value.of(i));
            val r             = filterOperator.evaluate(perElementCtx);
            mergeDependencies(deps, r.dependencies());
            val v = r.result();
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
            return new ExpressionResult(firstError, deps);
        }
        if (seenNull) {
            return new ExpressionResult(null, deps);
        }
        return new ExpressionResult(builder.build(), deps);
    }

    private static ExpressionResult evaluateEachStreamFilterObject(ObjectValue ov, StreamOperator filterOperator,
            EvaluationContext ctx, Map<SubscriptionKey, List<Occurrence>> deps) {
        val     builder    = new ObjectValue.Builder();
        boolean seenNull   = false;
        Value   firstError = null;
        for (val entry : ov.entrySet()) {
            val perElementCtx = ctx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
            val r             = filterOperator.evaluate(perElementCtx);
            mergeDependencies(deps, r.dependencies());
            val v = r.result();
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
                builder.put(entry.getKey(), v);
            }
        }
        if (firstError != null) {
            return new ExpressionResult(firstError, deps);
        }
        if (seenNull) {
            return new ExpressionResult(null, deps);
        }
        return new ExpressionResult(builder.build(), deps);
    }

    private static CompiledExpression compileEachValuePureFold(Value vb, PureOperator pof, CompilationContext ctx) {
        val tempEvaluationContext = DummyEvaluationContextFactory.dummyContext(ctx);
        return evaluateEachValuePure(vb, pof, tempEvaluationContext);
    }

    private static Value evaluateEachValueValue(Value vb, Value vf) {
        if (vf instanceof ErrorValue) {
            return vf;
        }
        if (vb instanceof ArrayValue av) {
            if (vf instanceof UndefinedValue) {
                return Value.EMPTY_ARRAY;
            }
            val builder = new ArrayValue.Builder();
            for (int i = 0; i < av.size(); i++) {
                builder.add(vf);
            }
            return builder.build();
        }
        if (vb instanceof ObjectValue ov) {
            if (vf instanceof UndefinedValue) {
                return Value.EMPTY_OBJECT;
            }
            val builder = new ObjectValue.Builder();
            for (val entry : ov.entrySet()) {
                builder.put(entry.getKey(), vf);
            }
            return builder.build();
        }
        return vf;
    }

    private static Value evaluateEachValuePure(Value vb, PureOperator pof, EvaluationContext ctx) {
        if (vb instanceof ArrayValue av) {
            return evaluateEachArrayPure(av, pof, ctx);
        }
        if (vb instanceof ObjectValue ov) {
            return evaluateEachObjectPure(ov, pof, ctx);
        }
        val localCtx = ctx.withRelativeValue(vb);
        return pof.evaluate(localCtx);
    }

    private static Value evaluateEachArrayPure(ArrayValue av, PureOperator pof, EvaluationContext ctx) {
        val builder = new ArrayValue.Builder();
        for (int i = 0; i < av.size(); i++) {
            val localCtx    = ctx.withRelativeValue(av.get(i), Value.of(i));
            val replacement = pof.evaluate(localCtx);
            if (replacement instanceof ErrorValue) {
                return replacement;
            }
            if (!(replacement instanceof UndefinedValue)) {
                builder.add(replacement);
            }
        }
        return builder.build();
    }

    private static Value evaluateEachObjectPure(ObjectValue ov, PureOperator pof, EvaluationContext ctx) {
        val builder = new ObjectValue.Builder();
        for (val entry : ov.entrySet()) {
            val localCtx    = ctx.withRelativeValue(entry.getValue(), Value.of(entry.getKey()));
            val replacement = pof.evaluate(localCtx);
            if (replacement instanceof ErrorValue) {
                return replacement;
            }
            if (!(replacement instanceof UndefinedValue)) {
                builder.put(entry.getKey(), replacement);
            }
        }
        return builder.build();
    }

}
