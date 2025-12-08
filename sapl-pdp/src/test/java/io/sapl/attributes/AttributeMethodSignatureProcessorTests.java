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
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeMethodSignatureProcessorTests {

    private static final String NAMESPACE = "test";

    @Test
    void when_methodIsNotAnnotatedWithAttribute_then_returnsNull() throws Exception {
        var method = TestPIP.class.getMethod("notAnAttribute");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);
        assertThat(result).isNull();
    }

    @Test
    void when_staticMethodWithNoParameters_then_processSuccessfully() throws Exception {
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
    void when_instanceMethodAndPipInstanceIsNull_then_throwsException() throws Exception {
        var method = TestPIP.class.getMethod("instanceMethod");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be static");
    }

    @Test
    void when_instanceMethodWithPipInstance_then_processSuccessfully() throws Exception {
        var pipInstance = new TestPIP();
        var method      = TestPIP.class.getMethod("instanceMethod");
        var result      = AttributeMethodSignatureProcessor.processAttributeMethod(pipInstance, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("instanceMethod");
    }

    @Test
    void when_methodReturnsNonReactiveType_then_throwsException() throws Exception {
        var method = TestPIP.class.getMethod("returnsString");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must return Flux<Value>, Mono<Value>");
    }

    @Test
    void when_methodReturnsFluxOfNonValue_then_throwsException() throws Exception {
        var method = TestPIP.class.getMethod("returnsFluxOfString");

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must return Flux<Value>, Mono<Value>");
    }

    @Test
    void when_methodReturnsMono_then_processSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("returnsMono");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("returnsMono");
    }

    @Test
    void when_methodHasEntityParameter_then_detectsIt() throws Exception {
        var method = TestPIP.class.getMethod("withEntity", Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.isEnvironmentAttribute()).isFalse();
    }

    @Test
    void when_methodHasEnvironmentAttributeAnnotation_then_markedAsEnvironment() throws Exception {
        var method = TestPIP.class.getMethod("environmentAttribute");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.isEnvironmentAttribute()).isTrue();
    }

    @Test
    void when_methodHasVariablesParameter_then_strippedFromSignature() throws Exception {
        var method = TestPIP.class.getMethod("withVariables", Value.class, Map.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).isEmpty();
    }

    @Test
    void when_methodHasValueParameters_then_includedInSignature() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).hasSize(2);
        assertThat(result.varArgsParameterType()).isNull();
    }

    @Test
    void when_methodHasVarArgs_then_detectedInSignature() throws Exception {
        var method = TestPIP.class.getMethod("withVarArgs", Value.class, Value[].class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.parameterTypes()).isEmpty();
        assertThat(result.varArgsParameterType()).isEqualTo(Value.class);
    }

    @Test
    void when_methodHasInvalidParameterType_then_throwsException() throws Exception {
        var method = TestPIP.class.getMethod("invalidParameterType", Value.class, String.class);

        assertThatThrownBy(() -> AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must only have Value");
    }

    @Test
    void when_attributeNameProvidedInAnnotation_then_used() throws Exception {
        var method = TestPIP.class.getMethod("customName");
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        assertThat(result).isNotNull();
        assertThat(result.attributeName()).isEqualTo("custom.name");
    }

    @Test
    void when_invokingAttributeFinderWithCorrectArgs_then_executesSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = createInvocation("withArguments", Value.of(1), Value.of(2));
        var context    = createEvaluationContext(Map.of());

        assertThat(result).isNotNull();
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNext(Value.of(3)).verifyComplete();
    }

    @Test
    void when_invokingAttributeFinderWithWrongArgCount_then_returnsError() throws Exception {
        var method = TestPIP.class.getMethod("withArguments", Value.class, Value.class, Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = createInvocation("withArguments", Value.of(1));
        var context    = createEvaluationContext(Map.of());

        assertThat(result).isNotNull();
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNextMatches(
                        v -> v instanceof ErrorValue e && e.message().contains("requires exactly 2 arguments"))
                .verifyComplete();
    }

    @Test
    void when_invokingAttributeFinderWithVarArgs_then_executesSuccessfully() throws Exception {
        var method = TestPIP.class.getMethod("withVarArgs", Value.class, Value[].class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = createInvocation("withVarArgs", Value.of(1), Value.of(2), Value.of(3));
        var context    = createEvaluationContext(Map.of());

        assertThat(result).isNotNull();
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNext(Value.of(6)).verifyComplete();
    }

    @Test
    void when_attributeThrowsException_then_returnsError() throws Exception {
        var method = TestPIP.class.getMethod("throwsException", Value.class);
        var result = AttributeMethodSignatureProcessor.processAttributeMethod(null, NAMESPACE, method);

        var invocation = createInvocation("throwsException");
        var context    = createEvaluationContext(Map.of());

        assertThat(result).isNotNull();
        StepVerifier
                .create(result.attributeFinder().invoke(invocation)
                        .contextWrite(ctx -> ctx.put(EvaluationContext.class, context)))
                .expectNextMatches(v -> v instanceof ErrorValue e && e.message().contains("execution failed"))
                .verifyComplete();
    }

    private AttributeFinderInvocation createInvocation(String attributeName, Value... args) {
        return new AttributeFinderInvocation("test-config", NAMESPACE + "." + attributeName, Value.UNDEFINED,
                List.of(args), Map.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(100), 3,
                false);
    }

    private EvaluationContext createEvaluationContext(Map<String, Value> variables) {
        return EvaluationContext.of("id", "test-config", "test-subscription", null, variables, null, null);
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
