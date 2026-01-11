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

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Map;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

class FunctionCallCompilerTests {

    @Test
    void when_functionCall_withAllLiteralArgs_then_constantFoldsAtCompileTime() {
        var broker = functionBroker("test.fn", args -> Value.of(args.size()));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn(1, 2, 3)", ctx);

        // Should be constant folded to a Value at compile time
        assertThat(result).isInstanceOf(Value.class).isEqualTo(Value.of(3));
    }

    @Test
    void when_functionCall_withNoArgs_then_constantFolds() {
        var broker = functionBroker("test.fn", args -> Value.of("no-args"));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn()", ctx);

        assertThat(result).isInstanceOf(Value.class).isEqualTo(Value.of("no-args"));
    }

    @Test
    void when_functionCall_withLiteralArgs_then_passesArgumentsCorrectly() {
        var captured = new FunctionInvocation[1];
        var broker   = capturingFunctionBroker(captured, Value.of("ok"));
        var ctx      = compilationContext(broker);
        compileExpression("test.fn(\"hello\", 42, true)", ctx);

        assertThat(captured[0]).isNotNull().satisfies(invocation -> {
            assertThat(invocation.functionName()).isEqualTo("test.fn");
            assertThat(invocation.arguments()).containsExactly(Value.of("hello"), Value.of(42), Value.TRUE);
        });
    }

    @Test
    void when_functionCall_withPureArg_then_returnsPureOperator() {
        var broker  = functionBroker("test.fn", args -> args.getFirst());
        var evalCtx = evaluationContext(broker, Map.of("x", Value.of(100)));
        var result  = evaluateExpression("test.fn(x)", evalCtx);

        // Expression depends on variable, should be PureOperator evaluated at runtime
        assertThat(result).isEqualTo(Value.of(100));
    }

    @Test
    void when_functionCall_withMixedValueAndPure_then_returnsPureOperator() {
        var captured = new ArrayList<FunctionInvocation>();
        var broker   = capturingFunctionBroker(captured, args -> Value.of("result"));
        var evalCtx  = evaluationContext(broker, Map.of("x", Value.of("dynamic")));
        var result   = evaluateExpression("test.fn(\"static\", x, 42)", evalCtx);

        assertThat(result).isEqualTo(Value.of("result"));
        assertThat(captured).hasSize(1).first().extracting(FunctionInvocation::arguments)
                .isEqualTo(java.util.List.of(Value.of("static"), Value.of("dynamic"), Value.of(42)));
    }

    @Test
    void when_functionCall_withPureArg_andErrorFromPure_then_propagatesError() {
        var broker  = functionBroker("test.fn", args -> Value.of("should not reach"));
        var evalCtx = evaluationContext(broker, Map.of("x", Value.error("pure error")));
        var result  = evaluateExpression("test.fn(x)", evalCtx);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("pure error");
    }

    @Test
    void when_functionCall_returnsError_then_propagatesError() {
        var broker  = functionBroker("test.fn", args -> Value.error("function error"));
        var evalCtx = evaluationContext(broker, Map.of());
        var result  = evaluateExpression("test.fn()", evalCtx);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("function error");
    }

    @Test
    void when_functionCall_withStreamArg_then_returnsStreamOperator() {
        var attrBroker = attributeBroker("stream.attr", Value.of(1), Value.of(2), Value.of(3));
        var fnBroker   = functionBroker("test.fn", args -> {
                           var num = ((NumberValue) args.getFirst()).value().intValue();
                           return Value.of(num * 10);
                       });
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<stream.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.of(10)))
                .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.of(20)))
                .assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.of(30))).verifyComplete();
    }

    @Test
    void when_functionCall_withMixedStaticAndStreamArgs_then_combinesCorrectly() {
        var attrBroker = attributeBroker("num.attr", Value.of(5), Value.of(10));
        var captured   = new ArrayList<FunctionInvocation>();
        var fnBroker   = capturingFunctionBroker(captured, args -> Value.of("ok"));
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(\"prefix\", <num.attr>, \"suffix\")", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream).assertNext(tv -> {
            assertThat(captured.get(0).arguments()).containsExactly(Value.of("prefix"), Value.of(5),
                    Value.of("suffix"));
        }).assertNext(tv -> {
            assertThat(captured.get(1).arguments()).containsExactly(Value.of("prefix"), Value.of(10),
                    Value.of("suffix"));
        }).verifyComplete();
    }

    @Test
    void when_functionCall_withStreamArgError_then_propagatesError() {
        var attrBroker = attributeBroker("err.attr", Value.error("stream error"));
        var fnBroker   = functionBroker("test.fn", args -> Value.of("should not reach"));
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<err.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream)
                .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                        .extracting(v -> ((ErrorValue) v).message()).asString().contains("stream error"))
                .verifyComplete();
    }

    @Test
    void when_functionCall_withMultipleStreamArgs_then_combinesLatest() {
        var attrBroker = singleValueAttributeBroker(Map.of("a.attr", Value.of("A1"), "b.attr", Value.of("B1")));
        var captured   = new ArrayList<FunctionInvocation>();
        var fnBroker   = capturingFunctionBroker(captured, args -> {
                           var a = ((TextValue) args.get(0)).value();
                           var b = ((TextValue) args.get(1)).value();
                           return Value.of(a + "-" + b);
                       });
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<a.attr>, <b.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        // combineLatest: emits when both have values
        StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.of("A1-B1")))
                .verifyComplete();
    }

    @Test
    void when_functionCall_withMultipleStreams_andOneError_then_propagatesError() {
        var attrBroker = singleValueAttributeBroker(
                Map.of("ok.attr", Value.of("ok"), "err.attr", Value.error("bad stream")));
        var fnBroker   = functionBroker("test.fn", args -> Value.of("should not reach"));
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<ok.attr>, <err.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream)
                .assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                        .extracting(v -> ((ErrorValue) v).message()).asString().contains("bad stream"))
                .verifyComplete();
    }

    @Test
    void when_functionCall_withUndefinedArg_then_filtersOutUndefined() {
        var captured = new ArrayList<FunctionInvocation>();
        var broker   = capturingFunctionBroker(captured, args -> Value.of(args.size()));
        var evalCtx  = evaluationContext(broker, Map.of("x", Value.UNDEFINED));
        var result   = evaluateExpression("test.fn(1, x, 3)", evalCtx);

        // Undefined should be filtered out, leaving 2 arguments
        assertThat(result).isEqualTo(Value.of(2));
        assertThat(captured).hasSize(1).first().extracting(FunctionInvocation::arguments)
                .isEqualTo(java.util.List.of(Value.of(1), Value.of(3)));
    }

    @Test
    void when_allPureFunction_withVariableArg_then_dependsOnSubscription() {
        var broker = functionBroker("test.fn", args -> Value.of("ok"));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn(x)", ctx);

        // Variable reference may be a subscription element, so depends on subscription
        assertThat(result).isInstanceOf(PureOperator.class)
                .extracting(r -> ((PureOperator) r).isDependingOnSubscription()).isEqualTo(true);
    }

    @Test
    void when_noArgsFunction_then_notDependingOnSubscription() {
        // No-args function with broker gets constant-folded to a Value, so we need
        // to test without a broker to get a NoArgsFunction. But that returns
        // ErrorValue.
        // So we test AllPureFunction with only literal args indirectly via the constant
        // folding.
        var broker = functionBroker("test.fn", args -> Value.of("result"));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn()", ctx);

        // Constant folded to Value at compile time
        assertThat(result).isInstanceOf(Value.class).isEqualTo(Value.of("result"));
    }

    @Test
    void when_functionCall_withMultipleStreams_then_mergesAllContributingAttributes() {
        var attrBroker = singleValueAttributeBroker(Map.of("a.attr", Value.of("A"), "b.attr", Value.of("B")));
        var fnBroker   = functionBroker("test.fn", args -> Value.of("result"));
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<a.attr>, <b.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream).assertNext(tv -> {
            // Should have traces from both a.attr and b.attr
            assertThat(tv.contributingAttributes()).hasSize(2).extracting(r -> r.invocation().attributeName())
                    .containsExactlyInAnyOrder("a.attr", "b.attr");
        }).verifyComplete();
    }

    @Test
    void when_functionCall_withSingleStream_then_preservesContributingAttributes() {
        var attrBroker = attributeBroker("test.attr", Value.of("value"));
        var fnBroker   = functionBroker("test.fn", args -> Value.of("result"));
        var evalCtx    = evaluationContext(fnBroker, attrBroker, Map.of());
        var result     = evaluateExpression("test.fn(<test.attr>)", evalCtx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        StepVerifier.create(stream).assertNext(tv -> assertThat(tv.contributingAttributes()).hasSize(1).first()
                .extracting(r -> r.invocation().attributeName()).isEqualTo("test.attr")).verifyComplete();
    }

}
