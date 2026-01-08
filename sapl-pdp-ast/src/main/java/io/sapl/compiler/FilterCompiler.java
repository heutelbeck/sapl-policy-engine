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
import io.sapl.ast.Expression;
import io.sapl.ast.RelativeReference;
import io.sapl.ast.RelativeType;
import io.sapl.ast.SimpleFilter;
import io.sapl.compiler.operators.SimpleStreamOperator;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

@UtilityClass
public class FilterCompiler {

    public static CompiledExpression compileSimple(SimpleFilter sf, CompilationContext ctx) {
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
        val function = FunctionCallCompiler.compile(sf.name().full(), arguments, location, ctx);
        return switch (base) {
        case Value vb           -> switch (function) {
                            case Value vf                                                  ->
                                evaluateEachValueValue(vb, vf);
                            case PureOperator pof when pof.isDependingOnSubscription()     ->
                                new SimpleFilterEachValuePure(vb, pof, location, true);
                            case PureOperator pof                                          ->
                                compileEachValuePureFold(vb, pof, ctx);
                            case StreamOperator sof                                        ->
                                new SimpleFilterEachValueStream(vb, sof, location);
                            };
        case PureOperator pob   -> switch (function) {
                            case Value vf               ->
                                new SimpleFilterEachPureValue(pob, vf, location, pob.isDependingOnSubscription());
                            case PureOperator pof       -> new SimpleFilterEachPurePure(pob, pof, location,
                                    pob.isDependingOnSubscription() || pof.isDependingOnSubscription());
                            case StreamOperator sof     -> new SimpleFilterEachPureStream(pob, sof, location);
                            };
        case StreamOperator sob -> switch (function) {
                            case Value vf               -> new SimpleFilterEachStreamValue(sob, vf);
                            case PureOperator pof       -> new SimpleFilterEachStreamPure(sob, pof);
                            case StreamOperator sof     -> new SimpleFilterEachStreamStream(sob, sof, location);
                            };
        };
    }

    record SimpleFilterEachValuePure(
            Value base,
            PureOperator filterOperator,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluateEachValuePure(base, filterOperator, ctx);
        }
    }

    record SimpleFilterEachValueStream(Value base, StreamOperator filterOperator, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(contextView -> evaluateEachValueStream(base, filterOperator, location,
                    contextView.get(EvaluationContext.class)));
        }
    }

    record SimpleFilterEachPureValue(
            PureOperator baseOperator,
            Value filterResult,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluateEachValueValue(baseOperator.evaluate(ctx), filterResult);
        }
    }

    record SimpleFilterEachPurePure(
            PureOperator baseOperator,
            PureOperator filterOperator,
            SourceLocation location,
            boolean isDependingOnSubscription) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluateEachValuePure(baseOperator.evaluate(ctx), filterOperator, ctx);
        }
    }

    record SimpleFilterEachPureStream(PureOperator baseOperator, StreamOperator filterOperator, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(contextView -> {
                val evalCtx = contextView.get(EvaluationContext.class);
                val base    = baseOperator.evaluate(evalCtx);
                return evaluateEachValueStream(base, filterOperator, location, evalCtx);
            });
        }
    }

    record SimpleFilterEachStreamValue(StreamOperator baseStream, Value filterValue) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return baseStream.stream().map(vb -> new TracedValue(evaluateEachValueValue(vb.value(), filterValue),
                    vb.contributingAttributes()));
        }
    }

    record SimpleFilterEachStreamPure(StreamOperator baseStream, PureOperator filterOperator)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.deferContextual(contextView -> {
                val evalCtx = contextView.get(EvaluationContext.class);
                return baseStream.stream()
                        .map(vb -> new TracedValue(evaluateEachValuePure(vb.value(), filterOperator, evalCtx),
                                vb.contributingAttributes()));
            });
        }
    }

    record SimpleFilterEachStreamStream(StreamOperator baseStream, StreamOperator filterStream, SourceLocation location)
            implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return baseStream.stream().switchMap(tracedBase -> Flux.deferContextual(contextView -> {
                val evalCtx = contextView.get(EvaluationContext.class);
                return evaluateEachValueStream(tracedBase.value(), filterStream, location, evalCtx)
                        .map(t -> t.with(tracedBase.contributingAttributes()));
            }));
        }
    }

    private static CompiledExpression compileEachValuePureFold(Value vb, PureOperator pof, CompilationContext ctx) {
        val tempEvaluationContext = new EvaluationContext(null, null, null, null, ctx.getFunctionBroker(),
                ctx.getAttributeBroker());
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

    private static Flux<TracedValue> evaluateEachValueStream(Value base, StreamOperator sof, SourceLocation location,
            EvaluationContext evalCtx) {
        if (base instanceof ErrorValue) {
            return Flux.just(TracedValue.of(base));
        }
        if (base instanceof ArrayValue av) {
            return evaluateEachArrayStream(av, sof, location, evalCtx);
        }
        if (base instanceof ObjectValue ov) {
            return evaluateEachObjectStream(ov, sof, location, evalCtx);
        }
        return sof.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx.withRelativeValue(base)));
    }

    private static Flux<TracedValue> evaluateEachArrayStream(ArrayValue av, StreamOperator sof, SourceLocation location,
            EvaluationContext evalCtx) {
        val elements = new ArrayList<CompiledExpression>(av.size());
        for (int i = 0; i < av.size(); i++) {
            val localCtx = evalCtx.withRelativeValue(av.get(i), Value.of(i));
            val element  = new SimpleStreamOperator(
                    sof.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, localCtx)));
            elements.add(element);
        }
        return toTracedStream(ArrayCompiler.buildFromCompiled(elements, location));
    }

    private static Flux<TracedValue> evaluateEachObjectStream(ObjectValue ov, StreamOperator sof,
            SourceLocation location, EvaluationContext evalCtx) {
        val keys     = new ArrayList<String>(ov.size());
        val elements = new ArrayList<CompiledExpression>(ov.size());
        for (val entry : ov.entrySet()) {
            val key      = entry.getKey();
            val value    = entry.getValue();
            val localCtx = evalCtx.withRelativeValue(value, Value.of(key));
            val element  = new SimpleStreamOperator(
                    sof.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, localCtx)));
            keys.add(key);
            elements.add(element);
        }
        return toTracedStream(ObjectCompiler.buildFromCompiled(keys, elements, location));
    }

    private static Flux<TracedValue> toTracedStream(CompiledExpression e) {
        return switch (e) {
        case Value v           -> Flux.just(TracedValue.of(v));
        case PureOperator po   ->
            Flux.deferContextual(contextView -> Flux.just(po.evaluate(contextView.get(EvaluationContext.class))))
                    .map(TracedValue::of);
        case StreamOperator so -> so.stream();
        };
    }

}
