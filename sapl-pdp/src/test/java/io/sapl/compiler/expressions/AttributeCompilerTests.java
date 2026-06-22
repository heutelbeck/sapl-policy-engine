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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("AttributeCompiler")
class AttributeCompilerTests {

    @DisplayName("Attribute name rejected by the name pattern but accepted by the grammar")
    @ParameterizedTest(name = "<{0}>")
    @ValueSource(strings = { "test_lib.attr", "test.at_tr", "lib$.attr", "^lib.attr" })
    void whenAttributeNameViolatesNamePatternThenCompilesToErrorWithoutThrowing(String attributeName) {
        var source = "<" + attributeName + ">";

        assertThatCode(() -> compileExpression(source)).doesNotThrowAnyException();
        assertThat(compileExpression(source)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenEntityAttributeNameViolatesNamePatternThenEvaluationReturnsErrorWithoutThrowing() {
        var eval = evaluate("subject.<user_lib.role>").withSubject(Value.of("alice"));

        assertThatCode(eval::value).doesNotThrowAnyException();
        assertThat(eval.value()).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenEnvironmentAttributeWithBrokerThenReturnsResultValue() {
        var eval = evaluate("<test.attr>").with("test.attr", Value.of("result"));

        assertThat(eval.value()).isEqualTo(Value.of("result"));
        assertThat(eval.onlyInvocation().attributeName()).isEqualTo("test.attr");
    }

    @Test
    void whenEnvironmentAttributeWithoutBindingThenResultIsNull() {
        // No binding means the attribute has no snapshot value; evaluate returns null
        // (incomplete) and the dependency is recorded in the result.
        var eval = evaluate("<test.attr>");

        assertThat(eval.value()).isNull();
        assertThat(eval.onlyInvocation().attributeName()).isEqualTo("test.attr");
    }

    @Test
    void whenEnvironmentAttributeWithArgumentsThenPassesArguments() {
        var invocation = evaluate("<test.attr(1, \"arg\")>").with("test.attr", Value.of("ok")).onlyInvocation();

        assertThat(invocation.arguments()).containsExactly(Value.of(1), Value.of("arg"));
    }

    @Test
    void whenHeadEnvironmentAttributeThenSnapshotValueReturned() {
        // The head marker (|) flips SubscriptionKey.head; the bound value still
        // resolves because the binding is by attribute name and matches both
        // head=true and head=false keys.
        var eval = evaluate("|<test.attr>").with("test.attr", Value.of(1));

        assertThat(eval.value()).isEqualTo(Value.of(1));
        assertThat(eval.onlySubscriptionKey().head()).isTrue();
    }

    @Test
    void whenEnvironmentAttributeWithOptionsThenPassesOptions() {
        var invocation = evaluate("<test.attr[{initialTimeOutMs: 5000, fresh: true}]>")
                .with("test.attr", Value.of("ok")).onlyInvocation();

        assertThat(invocation.initialTimeOut().toMillis()).isEqualTo(5000);
        assertThat(invocation.fresh()).isTrue();
    }

    @Test
    void whenEnvironmentAttributeWithStreamArgumentThenInnerInvocationsCarryArgValuesAcrossRounds() {
        // Drives <outer.attr(<inner.attr>)> through three rounds. Verifies that
        // the inner attribute value flows into the outer attribute's argument
        // list each round, and that both keys appear in the dependency map
        // every round (the outer's invocation key changes when inner changes).
        var driver = evaluate("<outer.attr(<inner.attr>)>");

        // Round 1: empty snapshot. Inner discovered; outer cannot be invoked yet.
        var r1 = driver.step();
        assertThat(r1.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                .containsExactly("inner.attr");

        // Round 2: bind inner=arg1. Outer now discoverable with arg1 in its arguments.
        driver.with("inner.attr", Value.of("arg1"));
        var r2 = driver.step();
        assertThat(r2.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                .containsExactlyInAnyOrder("inner.attr", "outer.attr");
        var r2Outer = r2.dependencies().keySet().stream()
                .filter(k -> "outer.attr".equals(k.invocation().attributeName())).findFirst().orElseThrow();
        assertThat(r2Outer.invocation().arguments()).containsExactly(Value.of("arg1"));

        // Round 3: bind outer's per-arg1 instance to a result, plus update inner=arg2.
        driver.with("outer.attr", Value.of("result-arg1")).with("inner.attr", Value.of("arg2"));
        var r3 = driver.step();
        // Outer's invocation key now carries arg2; the previous arg1-keyed binding
        // does not match, so result is null until the new outer key is bound.
        var r3Outer = r3.dependencies().keySet().stream()
                .filter(k -> "outer.attr".equals(k.invocation().attributeName())).findFirst().orElseThrow();
        assertThat(r3Outer.invocation().arguments()).containsExactly(Value.of("arg2"));
    }

    @Test
    void whenEnvironmentAttributeWithMixedArgumentsThenStreamArgFlowsIntoOuterInvocation() {
        // Drives <test.attr("fixed", <stream.attr>)> through two rounds. Verifies
        // that pure literal "fixed" and the stream value compose into the outer
        // invocation's argument list as the stream value evolves.
        var driver = evaluate("<test.attr(\"fixed\", <stream.attr>)>");

        // Round 1: stream.attr discovered; test.attr cannot resolve yet.
        var r1 = driver.step();
        assertThat(r1.dependencies().keySet()).extracting(k -> k.invocation().attributeName())
                .containsExactly("stream.attr");

        // Round 2: bind stream=10. Outer test.attr discoverable with arguments [fixed,
        // 10].
        driver.with("stream.attr", Value.of(10));
        var r2          = driver.step();
        var outerWith10 = r2.dependencies().keySet().stream()
                .filter(k -> "test.attr".equals(k.invocation().attributeName())).findFirst().orElseThrow();
        assertThat(outerWith10.invocation().arguments()).containsExactly(Value.of("fixed"), Value.of(10));

        // Round 3: change stream value to 20. Outer's invocation key now carries 20.
        driver.with("stream.attr", Value.of(20));
        var r3          = driver.step();
        var outerWith20 = r3.dependencies().keySet().stream()
                .filter(k -> "test.attr".equals(k.invocation().attributeName())).findFirst().orElseThrow();
        assertThat(outerWith20.invocation().arguments()).containsExactly(Value.of("fixed"), Value.of(20));
    }

    @Test
    void whenAttributeStepWithEntityThenPassesEntity() {
        var invocation = evaluate("subject.<user.role>").withSubject(Value.of("alice"))
                .with("user.role", Value.of("role")).onlyInvocation();

        assertThat(invocation.entity()).isEqualTo(Value.of("alice"));
        assertThat(invocation.attributeName()).isEqualTo("user.role");
    }

    @Test
    void whenAttributeStepWithUndefinedEntityThenReturnsError() {
        var value = evaluate("undefined.<user.role>").with("user.role", Value.of("admin")).value();

        assertThat(value).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) value).message()).contains("Undefined");
    }
}
