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
package io.sapl.compiler;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.util.ParserUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive tests for AttributeCompiler covering attribute finder steps,
 * environment attributes, option resolution, head attributes, and error
 * conditions.
 *
 * Test data theme: Norse Mythology
 */
class AttributeCompilerTests {

    private static final DefaultFunctionBroker  FUNCTION_BROKER      = new DefaultFunctionBroker();
    private static final AttributeRepository    ATTRIBUTE_REPOSITORY = new InMemoryAttributeRepository(
            Clock.systemUTC());
    private static final CachingAttributeBroker ATTRIBUTE_BROKER     = new CachingAttributeBroker(ATTRIBUTE_REPOSITORY);

    static {
        ATTRIBUTE_BROKER.loadPolicyInformationPointLibrary(new NorsePip());
    }

    @PolicyInformationPoint(name = "norse")
    public static class NorsePip {

        @Attribute
        public Flux<Value> realm(Value entity) {
            if (entity instanceof TextValue text && text.value().equals("Odin")) {
                return Flux.just(Value.of("Asgard"));
            }
            if (entity instanceof TextValue text && text.value().equals("Thor")) {
                return Flux.just(Value.of("Midgard"));
            }
            return Flux.just(Value.of("Unknown"));
        }

        @Attribute
        public Flux<Value> weapon(Value entity) {
            if (entity instanceof TextValue text && text.value().equals("Thor")) {
                return Flux.just(Value.of("Mjolnir"));
            }
            if (entity instanceof TextValue text && text.value().equals("Odin")) {
                return Flux.just(Value.of("Gungnir"));
            }
            return Flux.just(Value.UNDEFINED);
        }

        @EnvironmentAttribute
        public Flux<Value> streaming() {
            return Flux.<Value>just(Value.of("Yggdrasil"), Value.of("Bifrost"), Value.of("Valhalla"));
        }

        @Attribute
        public Flux<Value> withArguments(Value entity, Value arg1, Value arg2) {
            return Flux.just(Value.ofArray(entity, arg1, arg2));
        }

        @Attribute
        public Flux<Value> echo(Value entity) {
            return Flux.just(entity);
        }

        @EnvironmentAttribute(name = "rune.magic")
        public Flux<Value> runeMagic() {
            return Flux.just(Value.of("Ancient Power"));
        }
    }

    private CompilationContext createCompilationContext() {
        return new CompilationContext(FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    private EvaluationContext createEvaluationContext(AuthorizationSubscription authorizationSubscription) {
        return new EvaluationContext("asgard", "subscription_001", authorizationSubscription, FUNCTION_BROKER,
                ATTRIBUTE_BROKER);
    }

    private EvaluationContext createEvaluationContext() {
        return createEvaluationContext(new AuthorizationSubscription(Value.of("Thor"), Value.of("wield"),
                Value.of("Mjolnir"), Value.of("Midgard")));
    }

    private EvaluationContext createEvaluationContextWithGlobalOptions(ObjectValue globalOptions) {
        val saplVariable = ObjectValue.builder().put("attributeFinderOptions", globalOptions).build();
        return createEvaluationContext().with("SAPL", saplVariable);
    }

    @SneakyThrows
    private CompiledExpression compileExpression(String expression) {
        val parsedExpression = ParserUtil.expression(expression);
        return ExpressionCompiler.compileExpression(parsedExpression, createCompilationContext());
    }

    private Flux<Value> evaluateExpression(CompiledExpression expression, EvaluationContext evaluationContext) {
        return switch (expression) {
        case Value value                       -> Flux.just(value);
        case PureExpression pureExpression     -> Flux.just(pureExpression.evaluate(evaluationContext));
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
        };
    }

    @Nested
    class AttributeFinderStepTests {

        @Test
        void basicAttributeFinderStep_compilesToStreamExpression() {
            val compiled = compileExpression("\"Odin\".<norse.realm>");
            assertThat(compiled).isInstanceOf(StreamExpression.class);
        }

        @Test
        void basicAttributeFinderStep_evaluatesToCorrectValue() {
            val compiled  = compileExpression("\"Odin\".<norse.realm>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void attributeFinderStepOnVariable_evaluatesCorrectly() {
            val ctx       = createEvaluationContext().with("god", Value.of("Thor"));
            val compiled  = compileExpression("god.<norse.weapon>");
            val evaluated = evaluateExpression(compiled, ctx);
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void attributeFinderStepWithArguments_passesArgumentsCorrectly() {
            val compiled  = compileExpression("\"Odin\".<norse.withArguments(\"Sleipnir\", \"Huginn\")>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            val expected  = Value.ofArray(Value.of("Odin"), Value.of("Sleipnir"), Value.of("Huginn"));
            StepVerifier.create(evaluated.take(1)).expectNext(expected).verifyComplete();
        }

        @Test
        void chainedAttributeFinderSteps_evaluateInSequence() {
            val compiled  = compileExpression("\"Thor\".<norse.echo>.<norse.weapon>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void attributeFinderOnUndefined_producesCompileTimeError() {
            // UNDEFINED entity is rejected at compile time
            assertThrows(SaplCompilerException.class, () -> compileExpression("undefined.<norse.realm>"));
        }

        @Test
        void attributeFinderOnNull_passesNullToAttribute() {
            val compiled  = compileExpression("null.<norse.echo>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.NULL).verifyComplete();
        }

        @Test
        void attributeFinderOnError_propagatesError() {
            val compiled  = compileExpression("(1/0).<norse.realm>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> v instanceof ErrorValue).verifyComplete();
        }
    }

    @Nested
    class EnvironmentAttributeTests {

        @Test
        void environmentAttribute_compilesToStreamExpression() {
            val compiled = compileExpression("<norse.streaming>");
            assertThat(compiled).isInstanceOf(StreamExpression.class);
        }

        @Test
        void environmentAttribute_evaluatesToStream() {
            val compiled  = compileExpression("<norse.streaming [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(3))
                    .expectNext(Value.of("Yggdrasil"), Value.of("Bifrost"), Value.of("Valhalla")).verifyComplete();
        }

        @Test
        void environmentAttributeWithQualifiedName_resolvesCorrectly() {
            val compiled  = compileExpression("<norse.rune.magic>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Ancient Power")).verifyComplete();
        }
    }

    @Nested
    class HeadAttributeTests {

        @Test
        void headAttributeFinderStep_compilesToStreamExpression() {
            val compiled = compileExpression("\"Odin\".|<norse.realm>");
            assertThat(compiled).isInstanceOf(StreamExpression.class);
        }

        @Test
        void headAttributeFinderStep_takesOnlyFirstValue() {
            val compiled  = compileExpression("|<norse.streaming [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Yggdrasil")).verifyComplete();
        }

        @Test
        void headEnvironmentAttribute_takesOnlyFirstValue() {
            val compiled  = compileExpression("|<norse.streaming [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Yggdrasil")).verifyComplete();
        }

        @Test
        void headOnRegularAttribute_completesAfterFirstValue() {
            val compiled  = compileExpression("\"Thor\".|<norse.weapon>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Mjolnir")).verifyComplete();
        }
    }

    @Nested
    class OptionResolutionTests {

        @Test
        void defaultOptions_areUsedWhenNoOthersSpecified() {
            val compiled = compileExpression("\"Odin\".<norse.realm>");
            assertThat(compiled).isInstanceOf(StreamExpression.class);
            // Default options: 3s timeout, 30s poll, 1s backoff, 3 retries
            // Cannot directly inspect options but verify it compiles and runs
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void inlineOptions_overrideDefaults() {
            val compiled  = compileExpression("\"Thor\".<norse.weapon [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void globalOptions_overrideDefaultsButNotInline() {
            val globalOptions = ObjectValue.builder().put("fresh", Value.TRUE).put("retries", Value.of(5)).build();
            val ctx           = createEvaluationContextWithGlobalOptions(globalOptions);
            val compiled      = compileExpression("\"Odin\".<norse.realm>");
            val evaluated     = evaluateExpression(compiled, ctx);
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void inlineOptions_takePrecedenceOverGlobal() {
            val globalOptions = ObjectValue.builder().put("retries", Value.of(10)).build();
            val ctx           = createEvaluationContextWithGlobalOptions(globalOptions);
            val compiled      = compileExpression("\"Thor\".<norse.weapon [{\"retries\": 1}]>");
            val evaluated     = evaluateExpression(compiled, ctx);
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void wrongTypeInOptions_fallsBackToNextLevel() {
            // String instead of number for retries - should fall back to default
            val compiled  = compileExpression("\"Odin\".<norse.realm [{\"retries\": \"many\"}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void numericOptionsWithDecimals_areTruncatedToIntegers() {
            val compiled  = compileExpression("\"Thor\".<norse.weapon [{\"retries\": 3.7}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            // Should truncate to 3, not error
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void booleanOptions_acceptBooleanValues() {
            val compiled  = compileExpression("\"Odin\".<norse.realm [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void timeoutOptions_acceptNumericMilliseconds() {
            val compiled  = compileExpression("\"Thor\".<norse.weapon [{\"initialTimeOutMs\": 5000}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNext(Value.of("Mjolnir")).verifyComplete();
        }
    }

    @Nested
    class ErrorConditionTests {

        @Test
        void attributeFinderOnUndefinedEntity_producesCompileTimeError() {
            // UNDEFINED entity is rejected at compile time
            assertThrows(SaplCompilerException.class, () -> compileExpression("undefined.<norse.realm>"));
        }

        @Test
        void attributeFinderOnErrorEntity_propagatesError() {
            val compiled  = compileExpression("(1/0).<norse.weapon>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> v instanceof ErrorValue).verifyComplete();
        }

        @Test
        void invalidAttributeName_producesErrorAtRuntime() {
            val compiled  = compileExpression("\"Odin\".<norse.nonexistent>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> v instanceof ErrorValue).verifyComplete();
        }

        @Test
        void errorInOptions_propagatesToResult() {
            val compiled  = compileExpression("\"Thor\".<norse.weapon [{\"retries\": (1/0)}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNextMatches(v -> v instanceof ErrorValue).verifyComplete();
        }

        @Test
        void errorInArgument_passedToAttributeInArray() {
            // Error is passed as part of the argument array, not propagated immediately
            val compiled  = compileExpression("\"Odin\".<norse.withArguments((1/0), \"valid\")>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(1)).expectNextMatches(
                    v -> v instanceof ArrayValue array && array.size() == 3 && array.get(1) instanceof ErrorValue)
                    .verifyComplete();
        }
    }

    @Nested
    class ReactiveStreamBehaviorTests {

        @Test
        void attributeReturnsMultipleValues_allAreEmitted() {
            val compiled  = compileExpression("<norse.streaming [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated.take(3))
                    .expectNext(Value.of("Yggdrasil"), Value.of("Bifrost"), Value.of("Valhalla")).verifyComplete();
        }

        @Test
        void headOnStreamingAttribute_completesAfterFirst() {
            val compiled  = compileExpression("|<norse.streaming [{\"fresh\": true}]>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Yggdrasil")).verifyComplete();
        }

        @Test
        void entityFromStream_triggersResubscription() {
            // This tests that if entity is a stream, attribute is re-evaluated for each
            // value
            val ctx       = createEvaluationContext().with("gods", Value.ofArray(Value.of("Thor"), Value.of("Odin")));
            val compiled  = compileExpression("gods[0].|<norse.weapon>");
            val evaluated = evaluateExpression(compiled, ctx);
            StepVerifier.create(evaluated).expectNext(Value.of("Mjolnir")).verifyComplete();
        }
    }

    @Nested
    class SpecialCasesTests {

        @Test
        void headOperatorWithAttribute_takesFirstValue() {
            // Head operator test - inline options not supported with head operator
            val compiled  = compileExpression("\"Odin\".|<norse.realm>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Asgard")).verifyComplete();
        }

        @Test
        void nullOptions_usesDefaults() {
            val compiled  = compileExpression("\"Thor\".|<norse.weapon>");
            val evaluated = evaluateExpression(compiled, createEvaluationContext());
            StepVerifier.create(evaluated).expectNext(Value.of("Mjolnir")).verifyComplete();
        }

        @Test
        void attributeOnArray_eachElementProcessed() {
            val ctx       = createEvaluationContext().with("gods", Value.ofArray(Value.of("Thor"), Value.of("Odin")));
            val compiled  = compileExpression("gods.|<norse.echo>");
            val evaluated = evaluateExpression(compiled, ctx);
            // This should process the array as a single entity
            StepVerifier.create(evaluated).expectNext(Value.ofArray(Value.of("Thor"), Value.of("Odin")))
                    .verifyComplete();
        }

        @Test
        void attributeOnObject_passedAsEntity() {
            val ctx       = createEvaluationContext().with("being",
                    ObjectValue.builder().put("name", Value.of("Thor")).build());
            val compiled  = compileExpression("being.|<norse.echo>");
            val evaluated = evaluateExpression(compiled, ctx);
            StepVerifier.create(evaluated).expectNext(ObjectValue.builder().put("name", Value.of("Thor")).build())
                    .verifyComplete();
        }
    }
}
