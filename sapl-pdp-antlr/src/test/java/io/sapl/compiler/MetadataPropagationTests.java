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
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for metadata propagation through expression evaluation. Validates that
 * ValueMetadata (secret flags and attribute traces) correctly propagates
 * through operators, functions, attribute finders, containers, and filters.
 */
@DisplayName("Metadata Propagation")
class MetadataPropagationTests {

    private CompilationContext compilationContext;
    private EvaluationContext  evaluationContext;

    @BeforeEach
    void setUp() {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(MetadataTestFunctionLibrary.class);

        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        attributeBroker.loadPolicyInformationPointLibrary(new MetadataTestPip());

        compilationContext = new CompilationContext(functionBroker, attributeBroker);

        val subscription = new AuthorizationSubscription(Value.of("cthulhu"), Value.of("summon"),
                Value.of("nyarlathotep"), Value.UNDEFINED);
        evaluationContext = new EvaluationContext("testConfigurationId", "testSubscriptionId", null, subscription,
                functionBroker, attributeBroker);
    }

    // ========================================================================
    // Attribute Finder Metadata
    // ========================================================================

    @Test
    @DisplayName("attribute result contains attribute record in trace")
    void when_attributeFinderInvoked_then_resultContainsAttributeRecord() {
        val result = evaluate("<meta.cultist [{\"fresh\": true}]>");

        assertThat(result.metadata().attributeTrace()).hasSize(1).first()
                .satisfies(attr -> assertThat(attr.invocation().attributeName()).isEqualTo("meta.cultist"));
    }

    @Test
    @DisplayName("attribute with secret entity inherits secret flag and adds trace")
    void when_entityIsSecret_then_resultIsSecretWithTrace() {
        val secretEntity = Value.of("forbidden_knowledge").withMetadata(ValueMetadata.SECRET_EMPTY);
        val result       = evaluateWithVariable("entity.<meta.echo [{\"fresh\": true}]>", "entity", secretEntity);

        assertThat(result.isSecret()).isTrue();
        assertThat(result.metadata().attributeTrace()).hasSize(1);
    }

    @Test
    @DisplayName("chained attribute steps accumulate traces with correct attribute names")
    void when_chainedAttributeSteps_then_tracesAccumulateWithCorrectNames() {
        val result = evaluate("<meta.cultist [{\"fresh\": true}]>.<meta.echo [{\"fresh\": true}]>");

        assertThat(result.metadata().attributeTrace()).hasSize(2)
                .satisfies(traces -> assertThat(traces.stream().map(r -> r.invocation().attributeName()))
                        .containsExactlyInAnyOrder("meta.cultist", "meta.echo"));
    }

    // ========================================================================
    // Function Metadata Propagation
    // ========================================================================

    @Test
    @DisplayName("function result inherits attribute trace from argument")
    void when_functionWithAttributeArgument_then_resultHasTrace() {
        val result = evaluate("meta.identity(<meta.cultist [{\"fresh\": true}]>)");

        assertThat(result.metadata().attributeTrace()).hasSize(1).first()
                .satisfies(attr -> assertThat(attr.invocation().attributeName()).isEqualTo("meta.cultist"));
    }

    @Test
    @DisplayName("function with multiple attribute arguments merges all traces")
    void when_functionWithMultipleAttributeArguments_then_allTracesMerged() {
        val result = evaluate("meta.combine(<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>)");

        assertThat(result.metadata().attributeTrace()).hasSize(2);
    }

    @ParameterizedTest(name = "nesting depth {0}")
    @ValueSource(ints = { 2, 3, 4 })
    @DisplayName("deeply nested function calls preserve attribute trace")
    void when_deeplyNestedFunctions_then_attributeTracePreserved(int depth) {
        val expression = "meta.identity(".repeat(depth) + "<meta.cultist [{\"fresh\": true}]>" + ")".repeat(depth);
        val result     = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(1).first()
                .satisfies(attr -> assertThat(attr.invocation().attributeName()).isEqualTo("meta.cultist"));
    }

    @Test
    @DisplayName("function of function with different attributes accumulates all traces")
    void when_nestedFunctionsWithDifferentAttributes_then_allTracesAccumulated() {
        val result = evaluate(
                "meta.combine(meta.identity(<meta.cultist [{\"fresh\": true}]>), meta.identity(<meta.ritual [{\"fresh\": true}]>))");

        assertThat(result.metadata().attributeTrace()).hasSize(2);
    }

    @Test
    @DisplayName("function preserves secret flag from any argument")
    void when_anyArgumentIsSecret_then_resultIsSecret() {
        val secretValue = Value.of("secret").withMetadata(ValueMetadata.SECRET_EMPTY);
        val normalValue = Value.of("normal");
        val result      = evaluateWithVariables("meta.combine(a, b)", "a", normalValue, "b", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    // ========================================================================
    // Binary Operator Metadata (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0} operator merges operand metadata")
    @CsvSource({ "equality,       '<meta.cultist [{\"fresh\": true}]> == <meta.ritual [{\"fresh\": true}]>',     2",
            "inequality,     '<meta.cultist [{\"fresh\": true}]> != <meta.ritual [{\"fresh\": true}]>',     2",
            "addition,       '<meta.number [{\"fresh\": true}]> + <meta.number [{\"fresh\": true}]>',       2",
            "subtraction,    '<meta.number [{\"fresh\": true}]> - <meta.number [{\"fresh\": true}]>',       2",
            "multiplication, '<meta.number [{\"fresh\": true}]> * <meta.number [{\"fresh\": true}]>',       2",
            "boolean AND,    '<meta.truth [{\"fresh\": true}]> && <meta.truth [{\"fresh\": true}]>',        2",
            "boolean OR,     '<meta.falsehood [{\"fresh\": true}]> || <meta.truth [{\"fresh\": true}]>',    2",
            "less than,      '<meta.number [{\"fresh\": true}]> < <meta.number [{\"fresh\": true}]>',       2",
            "greater than,   '<meta.number [{\"fresh\": true}]> > <meta.number [{\"fresh\": true}]>',       2" })
    @DisplayName("binary operator merges operand metadata")
    void when_binaryOperatorWithAttributeOperands_then_metadataIsMerged(String operator, String expression,
            int expectedTraceSize) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(expectedTraceSize);
    }

    @Test
    @DisplayName("boolean OR with short-circuit preserves only evaluated operand metadata")
    void when_booleanOrShortCircuits_then_onlyEvaluatedOperandMetadataPreserved() {
        val result = evaluate("<meta.truth [{\"fresh\": true}]> || <meta.cultist [{\"fresh\": true}]>");

        assertThat(result.metadata().attributeTrace()).hasSize(1);
    }

    @ParameterizedTest(name = "{0} with secret operand produces secret result")
    @CsvSource({ "addition,   'value + 1'", "comparison, 'value == 42'", "boolean,    'value && true'" })
    @DisplayName("operators propagate secret flag")
    void when_operatorWithSecretOperand_then_resultIsSecret(String operator, String expression) {
        val secretValue = Value.of(42).withMetadata(ValueMetadata.SECRET_EMPTY);
        val result      = evaluateWithVariable(expression, "value", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    // ========================================================================
    // Unary Operator Metadata (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0} preserves operand metadata")
    @CsvSource({ "boolean NOT,  '!<meta.truth [{\"fresh\": true}]>'",
            "unary minus,  '-<meta.number [{\"fresh\": true}]>'" })
    @DisplayName("unary operators preserve operand metadata")
    void when_unaryOperatorOnAttribute_then_metadataPreserved(String operator, String expression) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(1);
    }

    @Test
    @DisplayName("unary operator preserves secret flag")
    void when_unaryOperatorOnSecretValue_then_resultIsSecret() {
        val secretValue = Value.of(42).withMetadata(ValueMetadata.SECRET_EMPTY);
        val result      = evaluateWithVariable("-value", "value", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    // ========================================================================
    // Container Metadata (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0} aggregates element metadata")
    @CsvSource({
            "array,  '[<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>]',                          2",
            "object, '{ \"a\": <meta.cultist [{\"fresh\": true}]>, \"b\": <meta.ritual [{\"fresh\": true}]> }',          2" })
    @DisplayName("containers aggregate element metadata")
    void when_containerWithAttributeElements_then_metadataAggregated(String containerType, String expression,
            int expectedTraceSize) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(expectedTraceSize);
    }

    @ParameterizedTest(name = "{0} with secret element produces secret container")
    @MethodSource("secretContainerTestCases")
    @DisplayName("containers propagate secret flag from elements")
    void when_containerContainsSecretElement_then_containerIsSecret(String containerType, String expression) {
        val secretValue = Value.of("secret").withMetadata(ValueMetadata.SECRET_EMPTY);
        val result      = evaluateWithVariable(expression, "value", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    static Stream<Arguments> secretContainerTestCases() {
        return Stream.of(arguments("array", "[\"public\", value]"),
                arguments("object", "{ \"public\": \"data\", \"secret\": value }"));
    }

    // ========================================================================
    // Step Operator Metadata (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0} step preserves parent metadata")
    @CsvSource({ "key,              '<meta.tome [{\"fresh\": true}]>.title',         1",
            "index,            '<meta.artifacts [{\"fresh\": true}]>[0]',       1",
            "recursive key,    '<meta.tome [{\"fresh\": true}]>..title',        1",
            "wildcard,         '<meta.tome [{\"fresh\": true}]>.*',             1",
            "array slicing,    '<meta.artifacts [{\"fresh\": true}]>[0:2]',     1" })
    @DisplayName("step operators preserve parent metadata")
    void when_stepOperatorOnAttributeResult_then_metadataPreserved(String stepType, String expression,
            int expectedTraceSize) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(expectedTraceSize);
    }

    @ParameterizedTest(name = "{0} step on secret container produces secret result")
    @MethodSource("secretStepTestCases")
    @DisplayName("step operators propagate secret flag from parent")
    void when_stepOnSecretContainer_then_resultIsSecret(String stepType, String expression, Value secretContainer) {
        val result = evaluateWithVariable(expression, "container", secretContainer);

        assertThat(result.isSecret()).isTrue();
    }

    static Stream<Arguments> secretStepTestCases() {
        val secretArray  = ArrayValue.builder().add(Value.of("item0")).add(Value.of("item1")).secret().build();
        val secretObject = ObjectValue.builder().put("key", Value.of("value")).secret().build();
        return Stream.of(arguments("index", "container[0]", secretArray),
                arguments("key", "container.key", secretObject));
    }

    // ========================================================================
    // Filter Expression Metadata (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0} filter preserves secret flag")
    @MethodSource("secretFilterTestCases")
    @DisplayName("filter expressions propagate secret flag")
    void when_filterOnSecretValue_then_resultIsSecret(String filterType, String expression, Value secretValue) {
        val result = evaluateWithVariable(expression, "value", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    static Stream<Arguments> secretFilterTestCases() {
        val secretText        = Value.of("secret_data").withMetadata(ValueMetadata.SECRET_EMPTY);
        val secretArray       = ArrayValue.builder().add(Value.of("a")).add(Value.of("b")).secret().build();
        val secretObject      = ObjectValue.builder().put("name", Value.of("Cthulhu")).secret().build();
        val secretNestedArray = ArrayValue.builder()
                .add(ObjectValue.builder().put("name", Value.of("Nyarlathotep")).build())
                .add(ObjectValue.builder().put("name", Value.of("Azathoth")).build()).secret().build();
        return Stream.of(arguments("simple", "value |- meta.identity", secretText),
                arguments("each on array", "value |- each meta.identity", secretArray),
                arguments("each on object", "value |- each meta.identity", secretObject),
                arguments("key step", "value |- { @.name : meta.identity }", secretObject),
                arguments("nested path", "value |- { each @.name : meta.identity }", secretNestedArray));
    }

    @ParameterizedTest(name = "{0} preserves attribute traces")
    @CsvSource({
            "simple filter,                       '<meta.cultist [{\"fresh\": true}]> |- meta.identity',                         1",
            "each filter with attribute elements,  '[<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>] |- each meta.identity',   2" })
    @DisplayName("filter expressions preserve attribute traces")
    void when_filterOnAttributeResult_then_tracePreserved(String filterType, String expression, int expectedTraceSize) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(expectedTraceSize);
    }

    // ========================================================================
    // Condition Step Metadata (Parameterized)
    // ========================================================================

    @Test
    @DisplayName("condition step on array preserves array metadata")
    void when_conditionStepOnSecretArray_then_resultIsSecret() {
        val secretArray = ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).add(Value.of(3)).secret().build();
        val result      = evaluateWithVariable("arr[?(@ > 1)]", "arr", secretArray);

        assertThat(result.isSecret()).isTrue();
    }

    @Test
    @DisplayName("condition step with attribute preserves traces")
    void when_conditionStepWithAttributeComparison_then_tracesAccumulated() {
        val result = evaluate("<meta.artifacts [{\"fresh\": true}]>[?(@ == \"Silver Key\")]");

        assertThat(result.metadata().attributeTrace()).hasSize(1);
    }

    @Test
    @DisplayName("condition step preserves secret from compared value")
    void when_conditionStepComparesWithSecretValue_then_resultIsSecret() {
        val secretValue = Value.of("target").withMetadata(ValueMetadata.SECRET_EMPTY);
        val array       = ArrayValue.builder().add(Value.of("target")).add(Value.of("other")).build();
        val result      = evaluateWithVariables("arr[?(@ == secret)]", "arr", array, "secret", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    // ========================================================================
    // Error Metadata Propagation
    // ========================================================================

    @Test
    @DisplayName("error from attribute preserves attribute trace")
    void when_attributeReturnsError_then_errorHasAttributeTrace() {
        val result = evaluate("<meta.forbidden [{\"fresh\": true}]>");

        assertThat(result).isInstanceOf(ErrorValue.class)
                .satisfies(r -> assertThat(r.metadata().attributeTrace()).hasSize(1));
    }

    @Test
    @DisplayName("error preserves secret flag from operand")
    void when_errorFromSecretOperand_then_errorIsSecret() {
        val secretValue = Value.of(42).withMetadata(ValueMetadata.SECRET_EMPTY);
        val result      = evaluateWithVariable("value / 0", "value", secretValue);

        assertThat(result).isInstanceOf(ErrorValue.class).satisfies(r -> assertThat(r.isSecret()).isTrue());
    }

    // ========================================================================
    // Deep Nesting - Complex Combinations (Parameterized)
    // ========================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("complexExpressionTestCases")
    @DisplayName("complex expressions accumulate all attribute traces")
    void when_complexExpression_then_allTracesAccumulated(String description, String expression,
            int expectedTraceSize) {
        val result = evaluate(expression);

        assertThat(result.metadata().attributeTrace()).hasSize(expectedTraceSize);
    }

    static Stream<Arguments> complexExpressionTestCases() {
        return Stream.of(
                arguments("function result as attribute entity",
                        "meta.identity(<meta.cultist [{\"fresh\": true}]>).<meta.echo [{\"fresh\": true}]>", 2),
                arguments("nested function with step",
                        "meta.identity(meta.identity(<meta.tome [{\"fresh\": true}]>).title)", 1),
                arguments("attributes in comparison",
                        "meta.combine(<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>) == meta.identity(<meta.tome [{\"fresh\": true}]>.title)",
                        3),
                arguments("attributes in container in function",
                        "meta.identity([<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>])", 2),
                arguments("attributes combined and filtered",
                        "[<meta.cultist [{\"fresh\": true}]>, <meta.ritual [{\"fresh\": true}]>] |- each meta.identity",
                        2));
    }

    @Test
    @DisplayName("secret propagates through multiple layers of nesting")
    void when_secretThroughMultipleLayers_then_secretPreserved() {
        val secretValue = Value.of("secret").withMetadata(ValueMetadata.SECRET_EMPTY);
        val result      = evaluateWithVariable("meta.identity([value, \"public\"])[0]", "value", secretValue);

        assertThat(result.isSecret()).isTrue();
    }

    // ========================================================================
    // Test Infrastructure
    // ========================================================================

    private Value evaluate(String expression) {
        val compiledExpression = compileExpression(expression);
        return evaluateCompiled(compiledExpression);
    }

    private Value evaluateWithVariable(String expression, String variableName, Value variableValue) {
        val compiledExpression  = compileExpression(expression);
        val contextWithVariable = evaluationContext.with(variableName, variableValue);
        return evaluateCompiledWithContext(compiledExpression, contextWithVariable);
    }

    private Value evaluateWithVariables(String expression, String var1, Value val1, String var2, Value val2) {
        val compiledExpression   = compileExpression(expression);
        val contextWithVariables = evaluationContext.with(var1, val1).with(var2, val2);
        return evaluateCompiledWithContext(compiledExpression, contextWithVariables);
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

    private Value evaluateCompiled(CompiledExpression expression) {
        return evaluateCompiledWithContext(expression, evaluationContext);
    }

    private Value evaluateCompiledWithContext(CompiledExpression expression, EvaluationContext context) {
        return switch (expression) {
        case Value value                       -> value;
        case PureExpression pureExpression     -> pureExpression.evaluate(context);
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, context)).blockFirst();
        };
    }

    // ========================================================================
    // Test PIP - Provides attributes with predictable metadata behavior
    // ========================================================================

    @PolicyInformationPoint(name = "meta")
    public static class MetadataTestPip {

        @EnvironmentAttribute
        public Flux<Value> cultist() {
            return Flux.just(Value.of("Randolph Carter"));
        }

        @EnvironmentAttribute
        public Flux<Value> ritual() {
            return Flux.just(Value.of("The Terrible Old Man"));
        }

        @EnvironmentAttribute
        public Flux<Value> number() {
            return Flux.just(Value.of(42));
        }

        @EnvironmentAttribute
        public Flux<Value> truth() {
            return Flux.just(Value.TRUE);
        }

        @EnvironmentAttribute
        public Flux<Value> falsehood() {
            return Flux.just(Value.FALSE);
        }

        @EnvironmentAttribute
        public Flux<Value> tome() {
            return Flux.just(ObjectValue.builder().put("title", Value.of("Necronomicon"))
                    .put("author", Value.of("Abdul Alhazred")).build());
        }

        @EnvironmentAttribute
        public Flux<Value> artifacts() {
            return Flux.just(ArrayValue.builder().add(Value.of("Silver Key")).add(Value.of("Shining Trapezohedron"))
                    .add(Value.of("Jade Amulet")).build());
        }

        @EnvironmentAttribute
        public Flux<Value> secretRitual() {
            return Flux.just(Value.of("forbidden_incantation").asSecret());
        }

        @EnvironmentAttribute
        public Flux<Value> forbidden() {
            return Flux.just(Value.error("Access to forbidden knowledge denied."));
        }

        @Attribute
        public Flux<Value> echo(Value entity) {
            return Flux.just(entity);
        }
    }

    // ========================================================================
    // Test Function Library - Functions that preserve/transform metadata
    // ========================================================================

    @FunctionLibrary(name = "meta", description = "Metadata test functions")
    public static class MetadataTestFunctionLibrary {

        @Function(docs = "Returns the input unchanged")
        public static Value identity(Value input) {
            return input;
        }

        @Function(docs = "Combines two values into an array")
        public static Value combine(Value first, Value second) {
            return ArrayValue.builder().add(first).add(second).build();
        }
    }
}
