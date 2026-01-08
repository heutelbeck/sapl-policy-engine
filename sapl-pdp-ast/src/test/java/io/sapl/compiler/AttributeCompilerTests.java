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

import static io.sapl.util.ExpressionTestUtil.evaluateExpression;
import static io.sapl.util.TestBrokers.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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
            var record = tv.contributingAttributes().getFirst();
            assertThat(record.invocation().attributeName()).isEqualTo("test.attr");
            assertThat(record.attributeValue()).isEqualTo(Value.of("result"));
            assertThat(record.retrievedAt()).isNotNull();
        }).verifyComplete();
    }

    @Test
    void when_environmentAttribute_withErrorBroker_then_returnsErrorWithTrace() {
        // When using a broker that returns errors, the error is returned with a trace
        var ctx    = evaluationContext(ERROR_ATTRIBUTE_BROKER);
        var result = evaluateExpression("<test.attr>", ctx);

        assertThat(result).isInstanceOf(StreamOperator.class);
        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) tv.value()).message()).contains("No attribute finder registered for");
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

        assertThat(capturedInvocation[0]).isNotNull();
        assertThat(capturedInvocation[0].arguments()).containsExactly(Value.of(1), Value.of("arg"));
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

        assertThat(capturedInvocation[0]).isNotNull();
        assertThat(capturedInvocation[0].initialTimeOut().toMillis()).isEqualTo(5000);
        assertThat(capturedInvocation[0].fresh()).isTrue();
    }

    @Test
    void when_environmentAttribute_withStreamArgument_then_combinesLatest() {
        var capturedInvocations = new java.util.ArrayList<AttributeFinderInvocation>();
        var broker              = new AttributeBroker() {
                                    @Override
                                    public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                                        capturedInvocations.add(invocation);
                                        if (invocation.attributeName().equals("inner.attr"))
                                            return Flux.just(Value.of("arg1"), Value.of("arg2"));
                                        if (invocation.attributeName().equals("outer.attr")) {
                                            var arg = ((io.sapl.api.model.TextValue) invocation.arguments().getFirst())
                                                    .value();
                                            return Flux.just(Value.of("result-" + arg));
                                        }
                                        return Flux.just(Value.error("Unknown"));
                                    }

                                    @Override
                                    public List<Class<?>> getRegisteredLibraries() {
                                        return List.of();
                                    }
                                };
        var ctx                 = evaluationContext(broker);
        var result              = evaluateExpression("<outer.attr(<inner.attr>)>", ctx);

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
                                        if (invocation.attributeName().equals("stream.attr"))
                                            return Flux.just(Value.of(10), Value.of(20));
                                        if (invocation.attributeName().equals("test.attr"))
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
            var outerInvocation = capturedInvocations.stream().filter(i -> i.attributeName().equals("test.attr"))
                    .findFirst().orElseThrow();
            assertThat(outerInvocation.arguments()).containsExactly(Value.of("fixed"), Value.of(10));
        }).assertNext(tv -> {
            // Second stream value
            var outerInvocations = capturedInvocations.stream().filter(i -> i.attributeName().equals("test.attr"))
                    .toList();
            assertThat(outerInvocations.get(1).arguments()).containsExactly(Value.of("fixed"), Value.of(20));
        }).verifyComplete();
    }

    @Test
    void when_attributeStep_withEntity_then_passesEntity() {
        var capturedInvocation = new AttributeFinderInvocation[1];
        var broker             = capturingAttributeBroker(capturedInvocation, Value.of("role"));
        var ctx                = evaluationContext(broker, Value.of("alice"));
        var result             = evaluateExpression("subject.<user.role>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).expectNextCount(1).verifyComplete();

        assertThat(capturedInvocation[0]).isNotNull();
        assertThat(capturedInvocation[0].entity()).isEqualTo(Value.of("alice"));
        assertThat(capturedInvocation[0].attributeName()).isEqualTo("user.role");
    }

    @Test
    void when_attributeStep_withUndefinedEntity_then_returnsError() {
        var broker = attributeBroker("user.role", Value.of("admin"));
        var ctx    = evaluationContext(broker);
        var result = evaluateExpression("undefined.<user.role>", ctx);

        var stream = ((StreamOperator) result).stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.create(stream).assertNext(tv -> {
            assertThat(tv.value()).isInstanceOf(ErrorValue.class);
            assertThat(((ErrorValue) tv.value()).message()).contains("Undefined");
        }).verifyComplete();
    }

}
