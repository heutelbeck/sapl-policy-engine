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
import io.sapl.api.pdp.AuthorizationSubscription;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static io.sapl.util.SaplTesting.ATTRIBUTE_BROKER;
import static io.sapl.util.SaplTesting.attributeBroker;
import static io.sapl.util.SaplTesting.capturingAttributeBroker;
import static io.sapl.util.SaplTesting.evaluateExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

class AttributeCompilerTests {

    @Test
    void when_environmentAttribute_withBroker_then_returnsStreamWithTrace() {
        var broker = attributeBroker("test.attr", Value.of("result"));
        var ctx    = evaluationContext(broker);
        var result = evaluateExpression("<test.attr>", ctx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            assertThat(tv.value()).isEqualTo(Value.of("result"));
            assertThat(tv.contributingAttributes()).hasSize(1);
            var attributeRecord = tv.contributingAttributes().getFirst();
            assertThat(attributeRecord.invocation().attributeName()).isEqualTo("test.attr");
            assertThat(attributeRecord.attributeValue()).isEqualTo(Value.of("result"));
            assertThat(attributeRecord.retrievedAt()).isNotNull();
        }).verifyComplete();
    }

    @Test
    void when_environmentAttribute_withErrorBroker_then_returnsErrorWithTrace() {
        // When using a broker that returns errors, the errors is returned with a trace
        var ctx    = evaluationContext(ATTRIBUTE_BROKER);
        var result = evaluateExpression("<test.attr>", ctx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message()).asString()
                    .contains("No attribute finder registered for");
            // Trace is recorded even for errors
            assertThat(tv.contributingAttributes()).hasSize(1);
        }).verifyComplete();
    }

    @Test
    void when_environmentAttribute_withArguments_then_passesArguments() {
        var capturedInvocation = new AttributeFinderInvocation[1];
        var broker             = capturingAttributeBroker(capturedInvocation, Value.of("ok"));
        var ctx                = evaluationContext(broker);
        var result             = evaluateExpression("<test.attr(1, \"arg\")>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).expectNextCount(1).verifyComplete();

        assertThat(capturedInvocation[0]).isNotNull().extracting(AttributeFinderInvocation::arguments)
                .isEqualTo(java.util.List.of(Value.of(1), Value.of("arg")));
    }

    @Test
    void when_headEnvironmentAttribute_then_takesOnlyFirst() {
        var broker = attributeBroker("test.attr", Value.of(1), Value.of(2), Value.of(3));
        var ctx    = evaluationContext(broker);
        var result = evaluateExpression("|<test.attr>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isEqualTo(Value.of(1))).verifyComplete();
    }

    @Test
    void when_environmentAttribute_withOptions_then_passesOptions() {
        var capturedInvocation = new AttributeFinderInvocation[1];
        var broker             = capturingAttributeBroker(capturedInvocation, Value.of("ok"));
        var ctx                = evaluationContext(broker);
        var result             = evaluateExpression("<test.attr[{initialTimeOutMs: 5000, fresh: true}]>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).expectNextCount(1).verifyComplete();

        assertThat(capturedInvocation[0]).isNotNull().satisfies(invocation -> {
            assertThat(invocation.initialTimeOut().toMillis()).isEqualTo(5000);
            assertThat(invocation.fresh()).isTrue();
        });
    }

    @Test
    void when_environmentAttribute_withStreamArgument_then_combinesLatest() {
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
    void when_environmentAttribute_withMixedArguments_then_combinesCorrectly() {
        var capturedInvocations = new java.util.ArrayList<AttributeFinderInvocation>();
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
    void when_attributeStep_withEntity_then_passesEntity() {
        var capturedInvocation = new AttributeFinderInvocation[1];
        var broker             = capturingAttributeBroker(capturedInvocation, Value.of("role"));
        var subscription       = AuthorizationSubscription.of(Value.of("alice"), Value.of("action"),
                Value.of("resource"), Value.of("env"));
        var ctx                = evaluationContext(subscription, broker);
        var result             = evaluateExpression("subject.<user.role>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).expectNextCount(1).verifyComplete();

        assertThat(capturedInvocation[0]).isNotNull().satisfies(invocation -> {
            assertThat(invocation.entity()).isEqualTo(Value.of("alice"));
            assertThat(invocation.attributeName()).isEqualTo("user.role");
        });
    }

    @Test
    void when_attributeStep_withUndefinedEntity_then_returnsError() {
        var broker = attributeBroker("user.role", Value.of("admin"));
        var ctx    = evaluationContext(broker);
        var result = evaluateExpression("undefined.<user.role>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                .extracting(v -> ((ErrorValue) v).message()).asString().contains("Undefined")).verifyComplete();
    }

}
