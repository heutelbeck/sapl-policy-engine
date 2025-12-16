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

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AttributeCompilerTests {

    private static CompilationContext compilationContext;
    private static EvaluationContext  evaluationContext;

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

    @BeforeAll
    static void setupContext() {
        val functionBroker  = new DefaultFunctionBroker();
        val repository      = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker = new CachingAttributeBroker(repository);
        attributeBroker.loadPolicyInformationPointLibrary(new DiscworldPip());
        compilationContext = new CompilationContext(functionBroker, attributeBroker);
        evaluationContext  = new EvaluationContext("id", "ankh_morpork", null, null, functionBroker, attributeBroker);
    }

    private CompiledExpression compileExpression(String expression) {
        val charStream       = CharStreams.fromString("policy \"test\" permit " + expression);
        val lexer            = new SAPLLexer(charStream);
        val tokenStream      = new CommonTokenStream(lexer);
        val parser           = new SAPLParser(tokenStream);
        val sapl             = parser.sapl();
        val policyElement    = (PolicyOnlyElementContext) sapl.policyElement();
        val targetExpression = policyElement.policy().targetExpression;
        return ExpressionCompiler.compileExpression(targetExpression, compilationContext);
    }

    private Flux<Value> evaluateExpression(CompiledExpression expression, EvaluationContext context) {
        return switch (expression) {
        case Value value                       -> Flux.just(value);
        case PureExpression pureExpression     -> Flux.just(pureExpression.evaluate(context));
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, context));
        };
    }

    private void assertEvaluatesToValue(String expression, Value expected) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated).expectNext(expected).thenCancel().verify();
    }

    private void assertEvaluatesToError(String expression, String errorFragment) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated).expectNextMatches(v -> v instanceof ErrorValue error
                && error.message().toLowerCase().contains(errorFragment.toLowerCase())).thenCancel().verify();
    }

    private void assertHeadCompletesWithValue(String expression, Value expected) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated).expectNext(expected).verifyComplete();
    }

    private void assertStreamEmitsValues(String expression, Value... expectedValues) {
        val compiled  = compileExpression(expression);
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated.take(expectedValues.length)).expectNext(expectedValues).verifyComplete();
    }

    private void assertCompilesToStreamExpression(String expression) {
        val compiled = compileExpression(expression);
        assertThat(compiled).isInstanceOf(StreamExpression.class);
    }

    private void assertThrowsCompileTimeError(String expression) {
        assertThatThrownBy(() -> compileExpression(expression)).isInstanceOf(SaplCompilerException.class);
    }

    // =========================================================================
    // Compilation Type Tests
    // =========================================================================

    @ParameterizedTest(name = "{0}")
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

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_attributeFinderBasicEvaluation_then_correctValue(String description, String expression, Value expected) {
        assertEvaluatesToValue(expression, expected);
    }

    private static Stream<Arguments> when_attributeFinderBasicEvaluation_then_correctValue() {
        return Stream.of(
                arguments("Attribute finder evaluates to correct value", "\"Ridcully\".<discworld.city>",
                        Value.of("Ankh-Morpork")),
                arguments("Attribute finder with arguments passes arguments correctly",
                        "\"Ridcully\".<discworld.withArguments(\"The Librarian\", \"Hex\")>",
                        Value.ofArray(Value.of("Ridcully"), Value.of("The Librarian"), Value.of("Hex"))),
                arguments("Chained attribute finders evaluate in sequence",
                        "\"Rincewind\".<discworld.echo>.<discworld.companion>", Value.of("The Luggage")),
                arguments("Attribute finder on null passes null to attribute", "null.<discworld.echo>", Value.NULL),
                arguments("Environment attribute with qualified name resolves correctly", "<discworld.octarine.magic>",
                        Value.of("The Eighth Colour")));
    }

    // =========================================================================
    // Error Tests
    // =========================================================================

    @ParameterizedTest(name = "{0}")
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
                        "attribute"));
    }

    // =========================================================================
    // Compile-Time Error Tests
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_attributeFinderCompileTimeError_then_throws(String description, String expression) {
        assertThrowsCompileTimeError(expression);
    }

    private static Stream<Arguments> when_attributeFinderCompileTimeError_then_throws() {
        return Stream.of(
                arguments("Attribute finder on undefined produces compile-time error", "undefined.<discworld.city>"));
    }

    // =========================================================================
    // Head Operator Completion Tests
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_headOperatorCompletion_then_completesWithValue(String description, String expression, Value expected) {
        assertHeadCompletesWithValue(expression, expected);
    }

    private static Stream<Arguments> when_headOperatorCompletion_then_completesWithValue() {
        return Stream.of(
                arguments("Head attribute finder with fresh gets first value",
                        "|<discworld.famousLocations [{\"fresh\": true}]>", Value.of("Unseen University")),
                arguments("Head on regular attribute completes after first value",
                        "\"Rincewind\".|<discworld.companion>", Value.of("The Luggage")),
                arguments("Head operator with attribute takes first value", "\"Ridcully\".|<discworld.city>",
                        Value.of("Ankh-Morpork")));
    }

    // =========================================================================
    // Multi-Value Stream Tests
    // =========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_multiValueStream_then_emitsAllValues(String description, String expression, Value[] expectedValues) {
        assertStreamEmitsValues(expression, expectedValues);
    }

    private static Stream<Arguments> when_multiValueStream_then_emitsAllValues() {
        return Stream.of(arguments("Environment attribute evaluates to stream",
                "<discworld.famousLocations [{\"fresh\": true}]>", new Value[] { Value.of("Unseen University"),
                        Value.of("The Patrician's Palace"), Value.of("The Mended Drum") }));
    }

    // =========================================================================
    // Custom Context Tests
    // =========================================================================

    @Test
    void when_attributeFinderStepOnVariable_then_evaluatesCorrectly() {
        val ctx       = evaluationContext.with("wizard", Value.of("Rincewind"));
        val compiled  = compileExpression("wizard.<discworld.companion>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(Value.of("The Luggage")).thenCancel().verify();
    }

    @Test
    void when_globalOptions_then_overrideDefaultsButNotInline() {
        val globalOptions = ObjectValue.builder().put("fresh", Value.TRUE).put("retries", Value.of(5)).build();
        val saplVariable  = ObjectValue.builder().put("attributeFinderOptions", globalOptions).build();
        val ctx           = evaluationContext.with("SAPL", saplVariable);
        val compiled      = compileExpression("\"Ridcully\".<discworld.city>");
        val evaluated     = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(Value.of("Ankh-Morpork")).thenCancel().verify();
    }

    @Test
    void when_entityFromStream_then_triggersResubscription() {
        val wizards   = Value.ofArray(Value.of("Rincewind"), Value.of("Ridcully"));
        val ctx       = evaluationContext.with("wizards", wizards);
        val compiled  = compileExpression("wizards[0].|<discworld.companion>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(Value.of("The Luggage")).verifyComplete();
    }

    @Test
    void when_attributeOnArray_then_eachElementProcessed() {
        val wizards   = Value.ofArray(Value.of("Rincewind"), Value.of("Ridcully"));
        val ctx       = evaluationContext.with("wizards", wizards);
        val compiled  = compileExpression("wizards.|<discworld.echo>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(wizards).verifyComplete();
    }

    @Test
    void when_attributeOnObject_then_passedAsEntity() {
        val being     = ObjectValue.builder().put("name", Value.of("Rincewind")).build();
        val ctx       = evaluationContext.with("being", being);
        val compiled  = compileExpression("being.|<discworld.echo>");
        val evaluated = evaluateExpression(compiled, ctx);
        StepVerifier.create(evaluated).expectNext(being).verifyComplete();
    }

    @Test
    void when_defaultOptions_then_usedWhenNoOthersSpecified() {
        val compiled = compileExpression("\"Ridcully\".<discworld.city>");
        assertThat(compiled).isInstanceOf(StreamExpression.class);
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated).expectNext(Value.of("Ankh-Morpork")).thenCancel().verify();
    }

    @Test
    void when_errorInArgument_then_passedToAttributeInArray() {
        val compiled  = compileExpression("\"Ridcully\".<discworld.withArguments((1/0), \"valid\")>");
        val evaluated = evaluateExpression(compiled, evaluationContext);
        StepVerifier.create(evaluated)
                .expectNextMatches(
                        v -> v instanceof ArrayValue array && array.size() == 3 && array.get(1) instanceof ErrorValue)
                .thenCancel().verify();
    }
}
