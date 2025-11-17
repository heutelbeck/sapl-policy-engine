/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Value;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.interpreter.InitializationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttributeMethodSignatureProcessorTests {

    private static final String NAMESPACE = "test";

    @Test
    void whenMethodIsNotAnnotatedWithAttributeThenReturnsNull() throws Exception {
        var method = TestPIP.class.getMethod("notAnAttribute");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);
        assertThat(result).isNull();
    }

    @Test
    void whenStaticMethodWithNoParametersThenProcessSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("staticNoParams");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.attributeName()).isEqualTo("staticNoParams");
        assertThat(result.isEnvironmentAttribute()).isFalse();
        assertThat(result.parameterTypes()).isEmpty();
        assertThat(result.varArgsParameterType()).isNull();
    }

    @Test
    void whenInstanceMethodAndPipInstanceIsNullThenThrowsException() throws Exception {
        var method = TestPIP.class.getMethod("instanceMethod");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(InitializationException.class).hasMessageContaining("must be static");
    }

    @Test
    void whenInstanceMethodWithPipInstanceThenProcessSuccessfully() throws Exception {
        var pipInstance = new TestPIP();
        var method      = TestPIP.class.getMethod("instanceMethod");
        var result      = AttributeMethodSignatureProcessor.processAttributeMethod(pipInstance, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("instanceMethod");
    }

    @Test
    void whenMethodReturnsNonReactiveTypeThenThrowsException() throws Exception {
        var method = TestPIP.class.getMethod("returnsString");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(InitializationException.class)
                .hasMessageContaining("must return Flux<Value>, Mono<Value>");
    }

    @Test
    void whenMethodReturnsFluxOfNonValueThenThrowsException() throws Exception {
        var method = TestPIP.class.getMethod("returnsFluxOfString");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(InitializationException.class)
                .hasMessageContaining("must return Flux<Value>, Mono<Value>");
    }

    @Test
    void whenMethodReturnsMonoThenProcessSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("returnsMono");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("returnsMono");
    }

    @Test
    void whenMethodHasEntityParameterThenDetectsIt() throws Exception {
        var method = TestPIP.class.getMethod("withEntity", Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.isEnvironmentAttribute()).isFalse();
    }

    @Test
    void whenMethodHasEnvironmentAttributeAnnotationThenMarkedAsEnvironment() throws Exception {
        var method = TestPIP.class.getMethod("environmentAttribute");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.isEnvironmentAttribute()).isTrue();
    }

    @Test
    void whenMethodHasVariablesParameterThenStrippedFromSignature() throws Exception {
        var method = TestPIP.class.getMethod("withVariables", Value.class, Map.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).isEmpty();
    }

    @Test
    void whenMethodHasValueParametersThenIncludedInSignature() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).hasSize(2);
        assertThat(result.varArgsParameterType()).isNull();
    }

    @Test
    void whenMethodHasVarArgsThenDetectedInSignature() throws Exception {
        var method = TestPIP.class.getMethod("withVarArgs", Value.class, Value[].class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).isEmpty();
        assertThat(result.varArgsParameterType()).isEqualTo(Value.class);
    }

    @Test
    void whenMethodHasInvalidParameterTypeThenThrowsException() throws Exception {
        var method = TestPIP.class.getMethod("invalidParameterType", Value.class, String.class);

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(InitializationException.class).hasMessageContaining("must only have Value");
    }

    @Test
    void whenAttributeNameProvidedInAnnotationThenUsed() throws Exception {
        var method = TestPIP.class.getMethod("customName");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("custom.name");
    }

    @Test
    void whenInvokingAttributeFinderWithCorrectArgsThenExecutesSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = mockInvocation("withArguments", Value.of(1), Value.of(2));
        var context    = mockEvaluationContext(Map.of());

        Assertions.assertNotNull(result);
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNext(Value.of(3)).verifyComplete();
    }

    @Test
    void whenInvokingAttributeFinderWithWrongArgCountThenReturnsError() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = mockInvocation("withArguments", Value.of(1));
        var context    = mockEvaluationContext(Map.of());

        Assertions.assertNotNull(result);
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNextMatches(
                        v -> v instanceof ErrorValue e && e.message().contains("requires exactly 2 arguments"))
                .verifyComplete();
    }

    @Test
    void whenInvokingAttributeFinderWithVarArgsThenExecutesSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("withVarArgs", Value.class, Value[].class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = mockInvocation("withVarArgs", Value.of(1), Value.of(2), Value.of(3));
        var context    = mockEvaluationContext(Map.of());

        Assertions.assertNotNull(result);
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNext(Value.of(6)).verifyComplete();
    }

    @Test
    void whenInvokingAttributeFinderWithVariablesThenAccessesFromContext() throws Exception {
        var method = TestPIP.class.getMethod("withVariables", Value.class, Map.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var variables  = Map.<String, Value>of("key", Value.of("value"));
        var invocation = mockInvocation("withVariables");
        var context    = mockEvaluationContext(variables);

        Assertions.assertNotNull(result);
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNext(Value.of("value")).verifyComplete();
    }

    @Test
    void whenAttributeThrowsExceptionThenReturnsError() throws Exception {
        var method = TestPIP.class.getMethod("throwsException", Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = mockInvocation("throwsException");
        var context    = mockEvaluationContext(Map.of());

        Assertions.assertNotNull(result);
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNextMatches(v -> v instanceof ErrorValue e && e.message().contains("execution failed"))
                .verifyComplete();
    }

    private AttributeFinderInvocation mockInvocation(String attributeName, Value... args) {
        var invocation = mock(AttributeFinderInvocation.class);
        when(invocation.attributeName()).thenReturn(attributeName);
        when(invocation.entity()).thenReturn(Value.UNDEFINED);
        when(invocation.arguments()).thenReturn(List.of(args));
        return invocation;
    }

    private EvaluationContext mockEvaluationContext(Map<String, Value> variables) {
        var context = mock(EvaluationContext.class);
        when(context.variables()).thenReturn(variables);
        return context;
    }

    static class TestPIP {
        public void notAnAttribute() {
        }

        @Attribute
        public static Flux<Value> staticNoParams() {
            return Flux.just(Value.of("static"));
        }

        @Attribute
        public Flux<Value> instanceMethod() {
            return Flux.just(Value.of("instance"));
        }

        @Attribute
        public static String returnsString() {
            return "invalid";
        }

        @Attribute
        public static Flux<String> returnsFluxOfString() {
            return Flux.just("invalid");
        }

        @Attribute
        public static Mono<Value> returnsMono() {
            return Mono.just(Value.of("mono"));
        }

        @Attribute
        public static Flux<Value> withEntity(Value entity) {
            return Flux.just(entity);
        }

        @Attribute
        @EnvironmentAttribute
        public static Flux<Value> environmentAttribute() {
            return Flux.just(Value.of("env"));
        }

        @Attribute
        public static Flux<Value> withVariables(Value entity, Map<String, Value> variables) {
            return Flux.just(variables.get("key"));
        }

        @Attribute
        public static Flux<Value> withArguments(Value entity, Value arg1, Value arg2) {
            return Flux
                    .just(Value.of(((NumberValue) arg1).value().intValue() + ((NumberValue) arg2).value().intValue()));
        }

        @Attribute
        public static Flux<Value> withVarArgs(Value entity, Value... args) {
            int sum = 0;
            for (Value arg : args) {
                sum += ((NumberValue) arg).value().intValue();
            }
            return Flux.just(Value.of(sum));
        }

        @Attribute
        public static Flux<Value> invalidParameterType(Value entity, String invalid) {
            return Flux.just(Value.of("invalid"));
        }

        @Attribute(name = "custom.name")
        public static Flux<Value> customName() {
            return Flux.just(Value.of("custom"));
        }

        @Attribute
        public static Flux<Value> throwsException(Value entity) {
            throw new RuntimeException("test exception");
        }
    }
}
