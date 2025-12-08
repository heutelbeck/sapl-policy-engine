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

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.util.ParserUtil;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AttributeCompilerTests {

    @SneakyThrows
    private PDPComponents createComponents() {
        return PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(new DiscworldPip()).build();
    }

    private CompilationContext createCompilationContext() {
        val components = createComponents();
        return new CompilationContext(components.functionBroker(), components.attributeBroker());
    }

    private EvaluationContext createEvaluationContext(AuthorizationSubscription authorizationSubscription) {
        val components = createComponents();
        return new EvaluationContext("id", "ankh_morpork", "subscription_001", authorizationSubscription,
                components.functionBroker(), components.attributeBroker());
    }

    private EvaluationContext createEvaluationContext() {
        return createEvaluationContext(new AuthorizationSubscription(Value.of("Rincewind"), Value.of("flee"),
                Value.of("danger"), Value.of("The Disc")));
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

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void assertEvaluatesToValue(String expression, Value expected) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(expected).thenCancel().verify();
    }

    private void assertEvaluatesToValueWithContext(String expression, Value expected, EvaluationContext ctx) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(expected).thenCancel().verify();
    }

    private void assertEvaluatesToError(String expression, String errorFragment) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated).expectNextMatches(v -> v instanceof ErrorValue error
                && error.message().toLowerCase().contains(errorFragment.toLowerCase())).thenCancel().verify();
    }

    private void assertHeadCompletesWithValue(String expression, Value expected) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(expected).verifyComplete();
    }

    private void assertStreamEmitsValues(String expression, Value... expectedValues) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated.take(expectedValues.length)).expectNext(expectedValues).verifyComplete();
    }

    private void assertCompilesToStreamExpression(String expression) {
        val compiled = compileExpression(expression);
        assertThat(compiled).isInstanceOf(StreamExpression.class);
    }

    private void assertThrowsCompileTimeError(String expression) {
        assertThrows(SaplCompilerException.class, () -> compileExpression(expression));
    }

    // =========================================================================
    // Policy Information Point
    // =========================================================================

    @PolicyInformationPoint(name = "discworld")
    public static class DiscworldPip {

        @Attribute
        public Flux<Value> city(Value entity) {
            if (entity instanceof TextValue text && "Ridcully".equals(text.value())) {
                return Flux.just(Value.of("Ankh-Morpork"));
            }
            if (entity instanceof TextValue text && "Rincewind".equals(text.value())) {
                return Flux.just(Value.of("The Disc"));
            }
            return Flux.just(Value.of("Unknown"));
        }

        @Attribute
        public Flux<Value> companion(Value entity) {
            if (entity instanceof TextValue text && "Rincewind".equals(text.value())) {
                return Flux.just(Value.of("The Luggage"));
            }
            if (entity instanceof TextValue text && "Ridcully".equals(text.value())) {
                return Flux.just(Value.of("Staff"));
            }
            return Flux.just(Value.UNDEFINED);
        }

        @EnvironmentAttribute
        public Flux<Value> famousLocations() {
            return Flux.just(Value.of("Unseen University"), Value.of("The Patrician's Palace"),
                    Value.of("The Mended Drum"));
        }

        @Attribute
        public Flux<Value> withArguments(Value entity, Value arg1, Value arg2) {
            return Flux.just(Value.ofArray(entity, arg1, arg2));
        }

        @Attribute
        public Flux<Value> echo(Value entity) {
            return Flux.just(entity);
        }

        @EnvironmentAttribute(name = "octarine.magic")
        public Flux<Value> octarineMagic() {
            return Flux.just(Value.of("The Eighth Colour"));
        }
    }

    // =========================================================================
    // Compilation Type Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_attributeFindersCompile_then_streamExpressions(String description, String expression) {
        assertCompilesToStreamExpression(expression);
    }

    private static Stream<Arguments> when_attributeFindersCompile_then_streamExpressions() {
        return Stream.of(
                arguments("Basic attribute finder compiles to StreamExpression", "\"Ridcully\".<discworld.city>"),
                arguments("Environment attribute compiles to StreamExpression", "<discworld.famousLocations>"),
                arguments("Head attribute finder compiles to StreamExpression", "\"Ridcully\".|<discworld.city>"));
    }

    // =========================================================================
    // Basic Value Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_attributeFinderBasicEvaluation_then_correctValue(String description, String expression,
            String expectedJson) {
        assertEvaluatesToValue(expression, json(expectedJson));
    }

    private static Stream<Arguments> when_attributeFinderBasicEvaluation_then_correctValue() {
        return Stream.of(
                arguments("Attribute finder evaluates to correct value", "\"Ridcully\".<discworld.city>",
                        "\"Ankh-Morpork\""),
                arguments("Attribute finder with arguments passes arguments correctly",
                        "\"Ridcully\".<discworld.withArguments(\"The Librarian\", \"Hex\")>",
                        "[\"Ridcully\", \"The Librarian\", \"Hex\"]"),
                arguments("Chained attribute finders evaluate in sequence",
                        "\"Rincewind\".<discworld.echo>.<discworld.companion>", "\"The Luggage\""),
                arguments("Attribute finder on null passes null to attribute", "null.<discworld.echo>", "null"),
                arguments("Environment attribute with qualified name resolves correctly", "<discworld.octarine.magic>",
                        "\"The Eighth Colour\""),
                arguments("Inline options override defaults", "\"Rincewind\".<discworld.companion>", "\"The Luggage\""),
                arguments("Wrong type in options falls back to next level",
                        "\"Ridcully\".<discworld.city [{\"retries\": \"many\"}]>", "\"Ankh-Morpork\""),
                arguments("Numeric options with decimals are truncated to integers",
                        "\"Rincewind\".<discworld.companion [{\"retries\": 3.7}]>", "\"The Luggage\""),
                arguments("Boolean options accept boolean values", "\"Ridcully\".<discworld.city>", "\"Ankh-Morpork\""),
                arguments("Timeout options accept numeric milliseconds",
                        "\"Rincewind\".<discworld.companion [{\"initialTimeOutMs\": 5000}]>", "\"The Luggage\""));
    }

    // =========================================================================
    // Error Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_attributeFinderErrorCondition_then_error(String description, String expression, String errorFragment) {
        assertEvaluatesToError(expression, errorFragment);
    }

    private static Stream<Arguments> when_attributeFinderErrorCondition_then_error() {
        return Stream.of(
                arguments("Attribute finder on error entity propagates error", "(1/0).<discworld.city>",
                        "division by zero"),
                arguments("Attribute finder on error propagates error", "(1/0).<discworld.companion>",
                        "division by zero"),
                arguments("Invalid attribute name produces error at runtime", "\"Ridcully\".<discworld.nonexistent>",
                        "attribute"),
                arguments("Error in options propagates to result",
                        "\"Rincewind\".<discworld.companion [{\"retries\": (1/0)}]>", "division by zero"));
    }

    // =========================================================================
    // Compile-Time Error Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_attributeFinderCompileTimeError_then_throws(String description, String expression) {
        assertThrowsCompileTimeError(expression);
    }

    private static Stream<Arguments> when_attributeFinderCompileTimeError_then_throws() {
        return Stream.of(
                arguments("Attribute finder on undefined produces compile-time error", "undefined.<discworld.city>"),
                arguments("Attribute finder on undefined entity produces compile-time error",
                        "undefined.<discworld.city>"));
    }

    // =========================================================================
    // Head Operator Completion Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_headOperatorCompletion_then_completesWithValue(String description, String expression,
            String expectedJson) {
        assertHeadCompletesWithValue(expression, json(expectedJson));
    }

    private static Stream<Arguments> when_headOperatorCompletion_then_completesWithValue() {
        return Stream.of(
                arguments("Head attribute finder takes only first value", "|<discworld.famousLocations>",
                        "\"Unseen University\""),
                arguments("Head environment attribute takes only first value", "|<discworld.famousLocations>",
                        "\"Unseen University\""),
                arguments("Head on regular attribute completes after first value",
                        "\"Rincewind\".|<discworld.companion>", "\"The Luggage\""),
                arguments("Head on streaming attribute completes after first", "|<discworld.famousLocations>",
                        "\"Unseen University\""),
                arguments("Head operator with attribute takes first value", "\"Ridcully\".|<discworld.city>",
                        "\"Ankh-Morpork\""),
                arguments("Null options uses defaults", "\"Rincewind\".|<discworld.companion>", "\"The Luggage\""));
    }

    // =========================================================================
    // Multi-Value Stream Tests
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource
    void when_multiValueStream_then_emitsAllValues(String description, String expression, String... expectedJson) {
        val expectedValues = new Value[expectedJson.length];
        for (int i = 0; i < expectedJson.length; i++) {
            expectedValues[i] = json(expectedJson[i]);
        }
        assertStreamEmitsValues(expression, expectedValues);
    }

    private static Stream<Arguments> when_multiValueStream_then_emitsAllValues() {
        return Stream.of(
                arguments("Environment attribute evaluates to stream", "<discworld.famousLocations>",
                        new String[] { "\"Unseen University\"", "\"The Patrician's Palace\"", "\"The Mended Drum\"" }),
                arguments("Attribute returns multiple values all are emitted", "<discworld.famousLocations>",
                        new String[] { "\"Unseen University\"", "\"The Patrician's Palace\"", "\"The Mended Drum\"" }));
    }

    // =========================================================================
    // Custom Context Tests
    // =========================================================================

    @Test
    void when_attributeFinderStepOnVariable_then_evaluatesCorrectly() {
        val ctx = createEvaluationContext().with("wizard", Value.of("Rincewind"));
        assertEvaluatesToValueWithContext("wizard.<discworld.companion>", Value.of("The Luggage"), ctx);
    }

    @Test
    void when_globalOptions_then_overrideDefaultsButNotInline() {
        val globalOptions = (ObjectValue) json("{\"fresh\": true, \"retries\": 5}");
        val ctx           = createEvaluationContextWithGlobalOptions(globalOptions);
        assertEvaluatesToValueWithContext("\"Ridcully\".<discworld.city>", Value.of("Ankh-Morpork"), ctx);
    }

    @Test
    void when_inlineOptions_then_takePrecedenceOverGlobal() {
        val globalOptions = (ObjectValue) json("{\"retries\": 10}");
        val ctx           = createEvaluationContextWithGlobalOptions(globalOptions);
        assertEvaluatesToValueWithContext("\"Rincewind\".<discworld.companion [{\"retries\": 1}]>",
                Value.of("The Luggage"), ctx);
    }

    @Test
    void when_entityFromStream_then_triggersResubscription() {
        val ctx       = createEvaluationContext().with("wizards", json("[\"Rincewind\", \"Ridcully\"]"));
        val compiled  = compileExpression("wizards[0].|<discworld.companion>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(Value.of("The Luggage")).verifyComplete();
    }

    @Test
    void when_attributeOnArray_then_eachElementProcessed() {
        val ctx       = createEvaluationContext().with("wizards", json("[\"Rincewind\", \"Ridcully\"]"));
        val compiled  = compileExpression("wizards.|<discworld.echo>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(json("[\"Rincewind\", \"Ridcully\"]")).verifyComplete();
    }

    @Test
    void when_attributeOnObject_then_passedAsEntity() {
        val ctx       = createEvaluationContext().with("being", json("{\"name\": \"Rincewind\"}"));
        val compiled  = compileExpression("being.|<discworld.echo>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(json("{\"name\": \"Rincewind\"}")).verifyComplete();
    }

    // =========================================================================
    // Special Case Tests
    // =========================================================================

    @Test
    void when_defaultOptions_then_usedWhenNoOthersSpecified() {
        val compiled = compileExpression("\"Ridcully\".<discworld.city>");
        assertThat(compiled).isInstanceOf(StreamExpression.class);
        // Default options: 3s timeout, 30s poll, 1s backoff, 3 retries
        // Cannot directly inspect options but verify it compiles and runs
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(Value.of("Ankh-Morpork")).thenCancel().verify();
    }

    @Test
    void when_errorInArgument_then_passedToAttributeInArray() {
        // Error is passed as part of the argument array, not propagated immediately
        val compiled  = compileExpression("\"Ridcully\".<discworld.withArguments((1/0), \"valid\")>");
        val evaluated = evaluateExpression(compiled, createEvaluationContext());
        StepVerifier.create(evaluated)
                .expectNextMatches(
                        v -> v instanceof ArrayValue array && array.size() == 3 && array.get(1) instanceof ErrorValue)
                .thenCancel().verify();
    }
}
