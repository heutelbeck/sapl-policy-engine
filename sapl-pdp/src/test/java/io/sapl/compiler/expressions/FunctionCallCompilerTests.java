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

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FunctionCallCompiler")
class FunctionCallCompilerTests {

    @Test
    void whenFunctionCallWithAllLiteralArgsThenConstantFoldsAtCompileTime() {
        var broker = functionBroker("test.fn", args -> Value.of(args.size()));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn(1, 2, 3)", ctx);

        // Should be constant folded to a Value at compile time
        assertThat(result).isInstanceOf(Value.class).isEqualTo(Value.of(3));
    }

    @Test
    void whenFunctionCallWithNoArgsThenConstantFolds() {
        var broker = functionBroker("test.fn", args -> Value.of("no-args"));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn()", ctx);

        assertThat(result).isInstanceOf(Value.class).isEqualTo(Value.of("no-args"));
    }

    @Test
    void whenFunctionCallWithLiteralArgsThenPassesArgumentsCorrectly() {
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
    void whenFunctionCallWithPureArgThenReturnsPureOperator() {
        var broker = functionBroker("test.fn", args -> args.getFirst());
        var ctx    = testContext(broker, Map.of("x", Value.of(100)));
        var result = evaluateExpression("test.fn(x)", ctx);

        // Expression depends on variable, should be PureOperator evaluated at runtime
        assertThat(result).isEqualTo(Value.of(100));
    }

    @Test
    void whenFunctionCallWithMixedValueAndPureThenReturnsPureOperator() {
        var captured = new ArrayList<FunctionInvocation>();
        var broker   = capturingFunctionBroker(captured, args -> Value.of("result"));
        var ctx      = testContext(broker, Map.of("x", Value.of("dynamic")));
        var result   = evaluateExpression("test.fn(\"static\", x, 42)", ctx);

        assertThat(result).isEqualTo(Value.of("result"));
        assertThat(captured).hasSize(1).first().extracting(FunctionInvocation::arguments)
                .isEqualTo(List.of(Value.of("static"), Value.of("dynamic"), Value.of(42)));
    }

    @Test
    void whenFunctionCallWithPureArgAndErrorFromPureThenPropagatesError() {
        var broker = functionBroker("test.fn", args -> Value.of("should not reach"));
        var ctx    = testContext(broker, Map.of("x", Value.error("pure errors")));
        var result = evaluateExpression("test.fn(x)", ctx);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("pure errors");
    }

    @Test
    void whenFunctionCallReturnsErrorThenPropagatesError() {
        var broker = functionBroker("test.fn", args -> Value.error("function errors"));
        var ctx    = testContext(broker, Map.of());
        var result = evaluateExpression("test.fn()", ctx);

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("function errors");
    }

    @Test
    void whenFunctionCallWithStreamArgThenComputesPerStreamValueAcrossRounds() {
        // Drives test.fn(<stream.attr>) where the function multiplies its arg by 10.
        // Each round binds a new value for stream.attr. The function is invoked
        // synchronously and the new value flows out per round.
        var fnBroker = functionBroker("test.fn",
                args -> Value.of(((NumberValue) args.getFirst()).value().intValue() * 10));
        var driver   = evaluate("test.fn(<stream.attr>)").withFunctionBroker(fnBroker);

        // Round 1: discovery only.
        driver.step();

        driver.with("stream.attr", Value.of(1));
        assertThat(driver.step().result()).isEqualTo(Value.of(10));

        driver.with("stream.attr", Value.of(2));
        assertThat(driver.step().result()).isEqualTo(Value.of(20));

        driver.with("stream.attr", Value.of(3));
        assertThat(driver.step().result()).isEqualTo(Value.of(30));
    }

    @Test
    void whenFunctionCallWithMixedStaticAndStreamArgsThenStreamValueFlowsIntoArguments() {
        // Drives test.fn("prefix", <num.attr>, "suffix"); a captured-args function
        // broker records each invocation. Verifies that as num.attr's bound value
        // changes, the function receives the new arg list each round.
        var captured = new ArrayList<FunctionInvocation>();
        var fnBroker = capturingFunctionBroker(captured, args -> Value.of("ok"));
        var driver   = evaluate("test.fn(\"prefix\", <num.attr>, \"suffix\")").withFunctionBroker(fnBroker);

        // Round 1: discovery only.
        driver.step();

        driver.with("num.attr", Value.of(5));
        driver.step();
        assertThat(captured.getLast().arguments()).containsExactly(Value.of("prefix"), Value.of(5), Value.of("suffix"));

        driver.with("num.attr", Value.of(10));
        driver.step();
        assertThat(captured.getLast().arguments()).containsExactly(Value.of("prefix"), Value.of(10),
                Value.of("suffix"));
    }

    @Test
    void whenFunctionCallWithStreamArgErrorThenPropagatesError() {
        var fnBroker = functionBroker("test.fn", args -> Value.of("should not reach"));
        var value    = evaluate("test.fn(<err.attr>)").withFunctionBroker(fnBroker)
                .with("err.attr", Value.error("stream errors")).value();

        assertThat(value).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("stream errors");
    }

    @Test
    void whenFunctionCallWithMultipleStreamArgsThenCombinesLatest() {
        var fnBroker = functionBroker("test.fn", args -> {
                         var a = ((TextValue) args.get(0)).value();
                         var b = ((TextValue) args.get(1)).value();
                         return Value.of(a + "-" + b);
                     });
        var value    = evaluate("test.fn(<a.attr>, <b.attr>)").withFunctionBroker(fnBroker)
                .with("a.attr", Value.of("A1")).with("b.attr", Value.of("B1")).value();

        assertThat(value).isEqualTo(Value.of("A1-B1"));
    }

    @Test
    void whenFunctionCallWithMultipleStreamsAndOneErrorThenPropagatesError() {
        var fnBroker = functionBroker("test.fn", args -> Value.of("should not reach"));
        var value    = evaluate("test.fn(<ok.attr>, <err.attr>)").withFunctionBroker(fnBroker)
                .with("ok.attr", Value.of("ok")).with("err.attr", Value.error("bad stream")).value();

        assertThat(value).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                .contains("bad stream");
    }

    @Test
    void whenFunctionCallWithUndefinedArgThenPassesAllArgsThrough() {
        var captured = new ArrayList<FunctionInvocation>();
        var broker   = capturingFunctionBroker(captured, args -> Value.of(args.size()));
        var ctx      = testContext(broker, Map.of("x", Value.UNDEFINED));
        var result   = evaluateExpression("test.fn(1, x, 3)", ctx);

        // Compiler no longer drops undefined args. Function library is
        // responsible for handling undefined inputs (matching attribute
        // PIP behavior).
        assertThat(result).isEqualTo(Value.of(3));
        assertThat(captured).hasSize(1).first().extracting(FunctionInvocation::arguments)
                .isEqualTo(List.of(Value.of(1), Value.UNDEFINED, Value.of(3)));
    }

    @Test
    void whenAllPureFunctionWithSubscriptionArgThenDependsOnSubscription() {
        var broker = functionBroker("test.fn", args -> Value.of("ok"));
        var ctx    = compilationContext(broker);
        var result = compileExpression("test.fn(subject)", ctx);

        // Subscription element reference means depends on subscription
        assertThat(result).isInstanceOf(PureOperator.class)
                .extracting(r -> ((PureOperator) r).isDependingOnSubscription()).isEqualTo(true);
    }

    @Test
    void whenNoArgsFunctionThenNotDependingOnSubscription() {
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
    void whenFunctionCallWithMultipleStreamsThenMergesAllDependencies() {
        var fnBroker = functionBroker("test.fn", args -> Value.of("result"));
        var eval     = evaluate("test.fn(<a.attr>, <b.attr>)").withFunctionBroker(fnBroker)
                .with("a.attr", Value.of("A")).with("b.attr", Value.of("B"));

        assertThat(eval.value()).isEqualTo(Value.of("result"));
        assertThat(eval.invocations()).extracting(AttributeFinderInvocation::attributeName)
                .containsExactlyInAnyOrder("a.attr", "b.attr");
    }

    @Test
    void whenFunctionNameIsSyntacticallyInvalidThenCompilerExceptionIsRaisedBeforeEvaluation() {
        // A name with characters the function-name validator rejects must be
        // caught at compile time, never propagate an IllegalArgumentException
        // from the eval-path invocation constructor.
        var                    ctx         = compilationContext();
        var                    invalidName = "bad_name.fn";
        final ThrowingCallable compile     = () -> FunctionCallCompiler.compile(invalidName, List.of(), TEST_LOCATION,
                ctx);

        assertThatThrownBy(compile).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void whenFunctionCallWithSingleStreamThenPreservesDependency() {
        var fnBroker = functionBroker("test.fn", args -> Value.of("result"));
        var eval     = evaluate("test.fn(<test.attr>)").withFunctionBroker(fnBroker).with("test.attr",
                Value.of("value"));

        assertThat(eval.value()).isEqualTo(Value.of("result"));
        assertThat(eval.onlyInvocation().attributeName()).isEqualTo("test.attr");
    }

}
