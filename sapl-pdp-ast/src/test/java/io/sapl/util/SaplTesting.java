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
package io.sapl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.ast.SaplDocument;
import io.sapl.ast.Statement;
import io.sapl.compiler.ast.Document;
import io.sapl.compiler.policy.*;
import io.sapl.compiler.ast.SAPLCompiler;
import io.sapl.compiler.ast.AstTransformer;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.policy.PolicyBody;
import io.sapl.compiler.util.Stratum;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.StatementContext;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified test utilities for SAPL expression parsing, compilation, evaluation,
 * and assertions.
 */
@UtilityClass
public class SaplTesting {

    public static final String TEST_TIMESTAMP = "2025-01-01T00:00:00Z";

    public static final SourceLocation TEST_LOCATION = new SourceLocation("test", "", 0, 0, 1, 1, 1, 1);

    public static final FunctionBroker FUNCTION_BROKER;

    public static final AttributeBroker ATTRIBUTE_BROKER;

    public static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new SaplJacksonModule());

    private static final StandaloneTransformer TRANSFORMER = new StandaloneTransformer();

    static {
        var functionBroker = new DefaultFunctionBroker();
        functionBroker.loadStaticFunctionLibrary(StandardFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(StringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SimpleFunctionLibrary.class);
        FUNCTION_BROKER = functionBroker;

        ATTRIBUTE_BROKER = new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                return Flux.just(Value.error("No attribute finder registered for: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static Expression parseExpression(String expressionSource) {
        var parser = createParser(expressionSource);
        return TRANSFORMER.expression(parser.expression());
    }

    public static Statement parseStatement(String statementSource) {
        var parser = createParser(statementSource);
        return TRANSFORMER.statement(parser.statement());
    }

    public static Document parseDocument(String documentSource) {
        return SAPLCompiler.parseDocument(documentSource);
    }

    public static SaplDocument document(String documentSource) {
        return SAPLCompiler.parseDocument(documentSource).saplDocument();
    }

    @SneakyThrows(JsonProcessingException.class)
    public static AuthorizationSubscription parseSubscription(String json) {
        return MAPPER.readValue(json, AuthorizationSubscription.class);
    }

    public static Policy parsePolicy(String policySource) {
        var document = SAPLCompiler.parse(policySource);
        var element  = document.policyElement();
        if (element instanceof PolicyOnlyElementContext policyOnly) {
            return (Policy) TRANSFORMER.visit(policyOnly.policy());
        }
        throw new IllegalArgumentException("Expected a single policy, not a policy set");
    }

    public static PolicyBody compilePolicy(String policySource) {
        return compilePolicy(policySource, compilationContext());
    }

    public static PolicyBody compilePolicy(String policySource, AttributeBroker attrBroker) {
        return compilePolicy(policySource, compilationContext(attrBroker));
    }

    public static PolicyBody compilePolicy(String policySource, CompilationContext ctx) {
        var policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, null, ctx).policyBody();
    }

    public static Flux<PolicyDecision> evaluatePolicy(String subscriptionJson, String policySource) {
        return evaluatePolicy(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static Flux<PolicyDecision> evaluatePolicy(String subscriptionJson, String policySource,
            AttributeBroker attrBroker) {
        return evaluatePolicy(subscriptionJson, policySource, compilationContext(attrBroker), attrBroker);
    }

    public static Flux<PolicyDecision> evaluatePolicy(String subscriptionJson, String policySource,
            CompilationContext compilationCtx, AttributeBroker attrBroker) {
        var subscription  = parseSubscription(subscriptionJson);
        var compiled      = compilePolicy(policySource, compilationCtx);
        var evaluationCtx = evaluationContext(subscription, attrBroker);
        return evaluatePolicyDecisionMaker(compiled, evaluationCtx);
    }

    public static Flux<PolicyDecision> evaluatePolicyDecisionMaker(PolicyBody compiled, EvaluationContext evalCtx) {
        return switch (compiled) {
        case PolicyDecision decision -> Flux.just(decision);
        case PurePolicyBody pure     -> Flux.just(pure.evaluateBody(evalCtx));
        case StreamPolicyBody stream -> stream.stream().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        };
    }

    public static CompiledPolicy compilePolicyFull(String policySource) {
        return compilePolicyFull(policySource, compilationContext());
    }

    public static CompiledPolicy compilePolicyFull(String policySource, AttributeBroker attrBroker) {
        return compilePolicyFull(policySource, compilationContext(attrBroker));
    }

    public static CompiledPolicy compilePolicyFull(String policySource, CompilationContext ctx) {
        var policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, null, ctx);
    }

    public static Flux<DecisionWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource) {
        return evaluatePolicyWithCoverage(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static Flux<DecisionWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource,
            AttributeBroker attrBroker) {
        return evaluatePolicyWithCoverage(subscriptionJson, policySource, compilationContext(attrBroker), attrBroker);
    }

    public static Flux<DecisionWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource,
            CompilationContext compilationCtx, AttributeBroker attrBroker) {
        var subscription  = parseSubscription(subscriptionJson);
        var compiled      = compilePolicyFull(policySource, compilationCtx);
        var evaluationCtx = evaluationContext(subscription, attrBroker);
        return compiled.coverageStream().contextWrite(c -> c.put(EvaluationContext.class, evaluationCtx));
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource) {
        assertCoverageMatchesProduction(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource,
            AttributeBroker attrBroker) {
        // Collect both streams to lists (blocking) to avoid timing issues
        var prodList = evaluatePolicy(subscriptionJson, policySource, attrBroker).collectList()
                .block(Duration.ofSeconds(5));
        var covList  = evaluatePolicyWithCoverage(subscriptionJson, policySource, attrBroker)
                .map(DecisionWithCoverage::decision).collectList().block(Duration.ofSeconds(5));

        assertThat(covList).as("Number of emissions").hasSameSizeAs(prodList);
        for (int i = 0; i < Objects.requireNonNull(prodList).size(); i++) {
            var prod = prodList.get(i);
            var cov  = Objects.requireNonNull(covList).get(i);
            assertThat(decisionsEquivalent(prod, cov)).as("Emission[%d]: production=%s, coverage=%s", i, prod, cov)
                    .isTrue();
        }
    }

    private static boolean decisionsEquivalent(PolicyDecision a, PolicyDecision b) {
        return a.decision() == b.decision() && java.util.Objects.equals(a.obligations(), b.obligations())
                && java.util.Objects.equals(a.advice(), b.advice())
                && java.util.Objects.equals(a.resource(), b.resource());
    }

    public static CompiledExpression compileExpression(String expressionSource) {
        return compileExpression(expressionSource, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static CompiledExpression compileExpression(String expressionSource, AttributeBroker attributeBroker) {
        return compileExpression(expressionSource, FUNCTION_BROKER, attributeBroker);
    }

    public static CompiledExpression compileExpression(String expressionSource, CompilationContext ctx) {
        var expression = parseExpression(expressionSource);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression compileExpression(String expressionSource, FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        var expression = parseExpression(expressionSource);
        var ctx        = new CompilationContext(functionBroker, attributeBroker);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression evaluateExpression(String source) {
        var compiled = compileExpression(source);
        return evaluate(compiled, evaluationContext());
    }

    public static CompiledExpression evaluateExpression(String source, EvaluationContext ctx) {
        var compiled = compileExpression(source, ctx.functionBroker(), ctx.attributeBroker());
        return evaluate(compiled, ctx);
    }

    private static CompiledExpression evaluate(CompiledExpression compiled, EvaluationContext ctx) {
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(ctx);
        default              -> compiled;
        };
    }

    public static EvaluationContext evaluationContext() {
        return new EvaluationContext(null, null, null, null, Map.of(), FUNCTION_BROKER, ATTRIBUTE_BROKER,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(AttributeBroker attributeBroker) {
        return new EvaluationContext(null, null, null, null, Map.of(), FUNCTION_BROKER, attributeBroker,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(AuthorizationSubscription subscription) {
        return new EvaluationContext(null, null, null, subscription, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static EvaluationContext evaluationContext(AuthorizationSubscription subscription,
            AttributeBroker attributeBroker) {
        return new EvaluationContext(null, null, null, subscription, FUNCTION_BROKER, attributeBroker);
    }

    public static EvaluationContext evaluationContext(AttributeBroker attributeBroker, Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, FUNCTION_BROKER, attributeBroker,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(AttributeBroker attributeBroker, Value subject) {
        return new EvaluationContext(null, null, null, null, Map.of("subject", subject), FUNCTION_BROKER,
                attributeBroker, () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(FunctionBroker functionBroker, Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, functionBroker, ATTRIBUTE_BROKER,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker,
            Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, functionBroker, attributeBroker,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext evaluationContext(Map<String, Value> variables) {
        return new EvaluationContext(null, null, null, null, variables, FUNCTION_BROKER, ATTRIBUTE_BROKER,
                () -> TEST_TIMESTAMP);
    }

    public static EvaluationContext subscriptionContext(Value subject, Value action, Value resource,
            Value environment) {
        var subscription = new AuthorizationSubscription(subject, action, resource, environment);
        return new EvaluationContext(null, null, null, subscription, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static EvaluationContext subscriptionContext() {
        return subscriptionContext(Value.of("alice"), Value.of("read"), Value.of("document"), Value.of("production"));
    }

    public static AttributeBroker attributeBroker(String expectedName, Value... values) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName))
                    return Flux.fromArray(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker attributeBroker(Map<String, Value[]> attributes) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var values = attributes.get(invocation.attributeName());
                if (values != null)
                    return Flux.fromArray(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker attributeBroker(Function<AttributeFinderInvocation, Flux<Value>> streamFn) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                return streamFn.apply(invocation);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker trackingBroker(AtomicBoolean subscribed, Value returnValue) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                subscribed.set(true);
                return Flux.just(returnValue);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker errorAttributeBroker(String expectedName, String errorMessage) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                if (invocation.attributeName().equals(expectedName))
                    return Flux.just(Value.error(errorMessage));
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker sequenceBroker(Map<String, List<Value>> attributeSequences) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var values = attributeSequences.get(invocation.attributeName());
                if (values != null)
                    return Flux.fromIterable(values);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker capturingAttributeBroker(AttributeFinderInvocation[] capture, Value returnValue) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                capture[0] = invocation;
                return Flux.just(returnValue);
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static AttributeBroker singleValueAttributeBroker(Map<String, Value> attributeValues) {
        return new AttributeBroker() {
            @Override
            public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
                var value = attributeValues.get(invocation.attributeName());
                if (value != null)
                    return Flux.just(value);
                return Flux.just(Value.error("Unknown attribute: " + invocation.attributeName()));
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static CompilationContext compilationContext() {
        return new CompilationContext(FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static CompilationContext compilationContext(AttributeBroker attrBroker) {
        return new CompilationContext(FUNCTION_BROKER, attrBroker);
    }

    public static CompilationContext compilationContext(FunctionBroker fnBroker) {
        return new CompilationContext(fnBroker, ATTRIBUTE_BROKER);
    }

    public static CompilationContext compilationContext(FunctionBroker fnBroker, AttributeBroker attrBroker) {
        return new CompilationContext(fnBroker, attrBroker);
    }

    public static CompilationContext emptyCompilationContext() {
        return new CompilationContext(null, null);
    }

    public static FunctionBroker functionBroker(String expectedName, Function<List<Value>, Value> fn) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                if (invocation.functionName().equals(expectedName))
                    return fn.apply(invocation.arguments());
                return Value.error("Unknown function: " + invocation.functionName());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static FunctionBroker functionBroker(Map<String, Function<List<Value>, Value>> functions) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                var fn = functions.get(invocation.functionName());
                if (fn == null)
                    return Value.error("Unknown function: " + invocation.functionName());
                return fn.apply(invocation.arguments());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static FunctionBroker capturingFunctionBroker(FunctionInvocation[] capture, Value returnValue) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                capture[0] = invocation;
                return returnValue;
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static FunctionBroker capturingFunctionBroker(List<FunctionInvocation> capture,
            Function<List<Value>, Value> fn) {
        return new FunctionBroker() {
            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                capture.add(invocation);
                return fn.apply(invocation.arguments());
            }

            @Override
            public List<Class<?>> getRegisteredLibraries() {
                return List.of();
            }
        };
    }

    public static Value obj(Object... keysAndValues) {
        var builder = ObjectValue.builder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            builder.put((String) keysAndValues[i], (Value) keysAndValues[i + 1]);
        }
        return builder.build();
    }

    public static ArrayValue array(Value... values) {
        var builder = ArrayValue.builder();
        for (var v : values) {
            builder.add(v);
        }
        return builder.build();
    }

    public static void assertCompilesTo(String source, Value expected) {
        assertThat(compileExpression(source)).isInstanceOf(Value.class).isEqualTo(expected);
    }

    public static void assertCompilesTo(String source, Class<? extends CompiledExpression> expectedType) {
        assertThat(compileExpression(source)).isInstanceOf(expectedType);
    }

    public static void assertCompilesToError(String source, String errorMessageContains) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) compiled).message()).contains(errorMessageContains);
    }

    public static void assertPureEvaluatesTo(String source, Map<String, Value> variables, Value expected) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evaluationContext().withVariables(variables)))
                .isEqualTo(expected);
    }

    public static void assertPureEvaluatesToError(String source, Map<String, Value> variables) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evaluationContext().withVariables(variables)))
                .isInstanceOf(ErrorValue.class);
    }

    public static void assertPureDependsOnSubscription(String source, boolean expected) {
        var compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).isDependingOnSubscription()).isEqualTo(expected);
    }

    public static void assertEvaluatesToError(String source) {
        assertThat(evaluateExpression(source)).isInstanceOf(ErrorValue.class);
    }

    public static void assertEvaluatesToError(String source, String messageFragment) {
        var result = evaluateExpression(source);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(messageFragment);
    }

    public static void assertIsError(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    public static void assertIsErrorContaining(CompiledExpression result, String... fragments) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        var message = ((ErrorValue) result).message().toLowerCase();
        for (var fragment : fragments) {
            assertThat(message).contains(fragment.toLowerCase());
        }
    }

    public static String errorMessage(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        return ((ErrorValue) result).message();
    }

    public static Stratum getStratum(CompiledExpression compiled) {
        return switch (compiled) {
        case ErrorValue e      -> null;
        case PureOperator p    -> p.isDependingOnSubscription() ? Stratum.PURE_SUB : Stratum.PURE_NON_SUB;
        case StreamOperator so -> Stratum.STREAM;
        case Value v           -> Stratum.VALUE;
        default                -> null;
        };
    }

    public static void assertStratumOfCompiledExpression(String expression, Stratum expected) {
        assertStratum(compileExpression(expression), expected);
    }

    public static void assertStratumOfCompiledExpression(String expression, CompilationContext ctx, Stratum expected) {
        assertStratum(compileExpression(expression, ctx), expected);
    }

    public static void assertStratum(CompiledExpression compiled, Stratum expected) {
        switch (expected) {
        case VALUE        -> assertThat(compiled).isInstanceOf(Value.class);
        case PURE_NON_SUB -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isFalse();
        }
        case PURE_SUB     -> {
            assertThat(compiled).isInstanceOf(PureOperator.class);
            assertThat(((PureOperator) compiled).isDependingOnSubscription()).isTrue();
        }
        case STREAM       -> assertThat(compiled).isInstanceOf(StreamOperator.class);
        }
    }

    public static Stratum expectedStratum(Stratum... inputs) {
        int maxLevel = 1;
        for (var s : inputs) {
            if (s.level > maxLevel) {
                maxLevel = s.level;
            }
        }
        if (maxLevel <= 2) {
            return Stratum.VALUE;
        }
        return maxLevel == 3 ? Stratum.PURE_SUB : Stratum.STREAM;
    }

    @SafeVarargs
    public static void verifyStream(StreamOperator op, EvaluationContext ctx, Consumer<TracedValue>... assertions) {
        var                            stream   = op.stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.Step<TracedValue> verifier = StepVerifier.create(stream);
        for (var assertion : assertions) {
            verifier = verifier.assertNext(assertion);
        }
        verifier.verifyComplete();
    }

    public static void verifyStreamEmits(StreamOperator op, EvaluationContext ctx, Value expected) {
        verifyStream(op, ctx, tv -> assertThat(tv.value()).isEqualTo(expected));
    }

    public static void verifyStreamEmits(StreamOperator op, EvaluationContext ctx, Value... expected) {
        @SuppressWarnings("unchecked")
        Consumer<TracedValue>[] assertions = new Consumer[expected.length];
        for (int i = 0; i < expected.length; i++) {
            final var exp = expected[i];
            assertions[i] = tv -> assertThat(tv.value()).isEqualTo(exp);
        }
        verifyStream(op, ctx, assertions);
    }

    public static void verifyStreamEmitsError(StreamOperator op, EvaluationContext ctx, String messageFragment) {
        verifyStream(op, ctx, tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                .extracting(v -> ((ErrorValue) v).message()).asString().contains(messageFragment));
    }

    private static SAPLParser createParser(String source) {
        var charStream  = CharStreams.fromString(source);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        return parser;
    }

    private static class StandaloneTransformer extends AstTransformer {
        StandaloneTransformer() {
            initializeImportMap(Map.of());
        }

        Expression expression(ExpressionContext ctx) {
            return (Expression) visit(ctx);
        }

        Statement statement(StatementContext ctx) {
            return (Statement) visit(ctx);
        }
    }

    public record TestPureOperator(Function<EvaluationContext, Value> evaluator, boolean isDependingOnSubscription)
            implements PureOperator {

        public TestPureOperator(Function<EvaluationContext, Value> evaluator) {
            this(evaluator, false);
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            return evaluator.apply(ctx);
        }

        @Override
        public SourceLocation location() {
            return TEST_LOCATION;
        }
    }

    public record TestStreamOperator(Value... values) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.fromArray(values).map(v -> new TracedValue(v, List.of()));
        }
    }

    public record TestStreamOperatorWithTraced(TracedValue... tracedValues) implements StreamOperator {
        @Override
        public Flux<TracedValue> stream() {
            return Flux.fromArray(tracedValues);
        }
    }
}
