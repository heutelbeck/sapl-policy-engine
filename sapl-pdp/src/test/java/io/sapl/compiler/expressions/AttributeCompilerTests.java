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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.util.SaplTesting.evaluate;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AttributeCompiler")
class AttributeCompilerTests {

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
    void whenEnvironmentAttributeWithStreamArgumentThenCombinesLatest() {
        var broker = new AttributeBroker() {
                       @Override
                       public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                           if ("inner.attr".equals(invocation.attributeName()))
                               return Flux.just(Value.of("arg1"), Value.of("arg2"));
                           if ("outer.attr".equals(invocation.attributeName())) {
                               var arg = ((io.sapl.api.model.TextValue) invocation.arguments().getFirst()).value();
                               return Flux.just(Value.of("result-" + arg));
                           }
                           return Flux.just(Value.error("Unknown"));
                       }

                       @Override
                       public List<Class<?>> getRegisteredLibraries() {
                           return List.of();
                       }
                   };
        var ctx    = evaluationContext(broker);
        var result = evaluateExpression("<outer.attr(<inner.attr>)>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            // First emission from inner produces first call to outer
            assertThat(tv.value()).isEqualTo(Value.of("result-arg1"));
            // Should have records from both inner and outer attribute
            assertThat(tv.contributingAttributes()).hasSize(2);
        }).assertNext(tv -> {
            // Second emission from inner produces second call to outer
            assertThat(tv.value()).isEqualTo(Value.of("result-arg2"));
            assertThat(tv.contributingAttributes()).hasSize(2);
        }).verifyComplete();
    }

    @Test
    void whenEnvironmentAttributeWithMixedArgumentsThenCombinesCorrectly() {
        var capturedInvocations = new ArrayList<AttributeFinderInvocation>();
        var broker              = new AttributeBroker() {
                                    @Override
                                    public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                                        capturedInvocations.add(invocation);
                                        if ("stream.attr".equals(invocation.attributeName()))
                                            return Flux.just(Value.of(10), Value.of(20));
                                        if ("test.attr".equals(invocation.attributeName()))
                                            return Flux.just(Value.of("ok"));
                                        return Flux.just(Value.error("Unknown"));
                                    }

                                    @Override
                                    public List<Class<?>> getRegisteredLibraries() {
                                        return List.of();
                                    }
                                };
        var ctx                 = evaluationContext(broker);
        var result              = evaluateExpression("<test.attr(\"fixed\", <stream.attr>)>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            // Verify mixed arguments: pure "fixed" and stream value 10
            var outerInvocation = capturedInvocations.stream().filter(i -> "test.attr".equals(i.attributeName()))
                    .findFirst().orElseThrow();
            assertThat(outerInvocation.arguments()).containsExactly(Value.of("fixed"), Value.of(10));
        }).assertNext(tv -> {
            // Second stream value
            var outerInvocations = capturedInvocations.stream().filter(i -> "test.attr".equals(i.attributeName()))
                    .toList();
            assertThat(outerInvocations.get(1).arguments()).containsExactly(Value.of("fixed"), Value.of(20));
        }).verifyComplete();
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
