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

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.*;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.ast.SaplDocument;
import io.sapl.ast.Statement;
import io.sapl.attributes.broker.api.TestAttributeBroker;
import io.sapl.compiler.document.*;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.CompiledPolicySet;
import io.sapl.compiler.policyset.PolicySetCompiler;
import io.sapl.compiler.util.Stratum;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.functions.libraries.StringFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.StatementContext;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified test utilities for SAPL expression parsing, compilation, evaluation,
 * and assertions.
 */
@UtilityClass
public class SaplTesting {

    private static final String  DEFAULT_PDP_ID    = "testPdp";
    private static final String  DEFAULT_CONFIG_ID = "testConfig";
    private static final String  DEFAULT_SUB_ID    = "testSubscription";
    private static final Instant REFERENCE         = Instant.parse("2025-01-01T00:00:00Z");

    public static final SourceLocation        TEST_LOCATION           = new SourceLocation("test", "", 0, 0, 1, 1, 1,
            1);
    public static final FunctionBroker        FUNCTION_BROKER;
    public static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

    private static final JsonMapper            MAPPER      = JsonMapper.builder().addModule(new SaplJacksonModule())
            .build();
    private static final StandaloneTransformer TRANSFORMER = new StandaloneTransformer();

    public static final AuthorizationSubscription DEFAULT_SUBSCRIPTION = AuthorizationSubscription
            .of(Value.of("testSubject"), Value.of("testAction"), Value.of("testResource"), Value.of("testEnvironment"));

    static {
        val functionBroker = new DefaultFunctionBroker();
        functionBroker.load(new StandardFunctionLibrary());
        functionBroker.load(new FilterFunctionLibrary());
        functionBroker.load(new TemporalFunctionLibrary());
        functionBroker.load(new StringFunctionLibrary());
        functionBroker.load(new SimpleFunctionLibrary());
        FUNCTION_BROKER = functionBroker;
    }

    public static Expression parseExpression(String expressionSource) {
        val parser = createParser(expressionSource);
        return TRANSFORMER.expression(parser.expression());
    }

    public static Statement parseStatement(String statementSource) {
        val parser = createParser(statementSource);
        return TRANSFORMER.statement(parser.statement());
    }

    public static Document parseDocument(String documentSource) {
        return DocumentCompiler.parseDocument(documentSource);
    }

    public static SaplDocument document(String documentSource) {
        val parsed = DocumentCompiler.parseDocument(documentSource);
        if (!parsed.syntaxErrors().isEmpty()) {
            throw new IllegalArgumentException("Syntax errors(s): " + String.join("; ", parsed.syntaxErrors()));
        }
        if (!parsed.validationErrors().isEmpty()) {
            throw new IllegalArgumentException("Validation errors(s): "
                    + parsed.validationErrors().stream().map(Object::toString).collect(Collectors.joining("; ")));
        }
        return parsed.saplDocument();
    }

    @SneakyThrows(JacksonException.class)
    public static AuthorizationSubscription parseSubscription(String json) {
        return MAPPER.readValue(json, AuthorizationSubscription.class);
    }

    public static Policy parsePolicy(String policySource) {
        val document = DocumentCompiler.parseDocument(policySource);
        val element  = document.sapl().policyElement();
        if (element instanceof PolicyOnlyElementContext policyOnly) {
            return (Policy) TRANSFORMER.visit(policyOnly.policy());
        }
        throw new IllegalArgumentException("Expected a single policy, not a policy set");
    }

    private static SAPLParser createParser(String source) {
        val charStream  = CharStreams.fromString(source);
        val lexer       = new SAPLLexer(charStream);
        val tokenStream = new CommonTokenStream(lexer);
        val parser      = new SAPLParser(tokenStream);
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

    public static CompilationContext compilationContext() {
        return new CompilationContext(FUNCTION_BROKER);
    }

    public static CompilationContext compilationContext(FunctionBroker fnBroker) {
        return new CompilationContext(fnBroker);
    }

    public static CompilationContext compilationContext(ObjectValue variables) {
        val data = new PdpData(variables, Value.EMPTY_OBJECT);
        return new CompilationContext(data, FUNCTION_BROKER);
    }

    public static CompilationContext compilationContext(ObjectValue variables, FunctionBroker fnBroker) {
        val data = new PdpData(variables, Value.EMPTY_OBJECT);
        return new CompilationContext(data, fnBroker);
    }

    public static CompilationContext compilationContextWithSecrets(ObjectValue variables, ObjectValue secrets) {
        val data = new PdpData(variables, secrets);
        return new CompilationContext(data, FUNCTION_BROKER);
    }

    public static EvaluationContext evaluationContext() {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                FUNCTION_BROKER);
    }

    public static EvaluationContext evaluationContext(AuthorizationSubscription subscription) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription, FUNCTION_BROKER);
    }

    public static EvaluationContext evaluationContext(FunctionBroker functionBroker) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                functionBroker);
    }

    public static EvaluationContext subscriptionContext(String subscriptionJson) {
        val subscription = parseSubscription(subscriptionJson);
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription, FUNCTION_BROKER);
    }

    public static EvaluationContext subscriptionContext() {
        return subscriptionContext("""
                {
                    "subject": "alice",
                    "action": "read",
                    "resource": "document",
                    "environment": "production"
                }
                """);
    }

    public static FunctionBroker functionBroker(String expectedName, Function<List<Value>, Value> fn) {
        return new FunctionBroker() {
            @Override
            public void load(Object libraryInstance) {
                /* no-op for test broker */
            }

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
            public void load(Object libraryInstance) {
                /* no-op for test broker */
            }

            @Override
            public Value evaluateFunction(FunctionInvocation invocation) {
                val fn = functions.get(invocation.functionName());
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
            public void load(Object libraryInstance) {
                /* no-op for test broker */
            }

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
            public void load(Object libraryInstance) {
                /* no-op for test broker */
            }

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

    public static CompiledExpression compileExpression(String expressionSource) {
        return compileExpression(expressionSource, FUNCTION_BROKER);
    }

    public static CompiledExpression compileExpression(String expressionSource, CompilationContext ctx) {
        val expression = parseExpression(expressionSource);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression compileExpression(String expressionSource, FunctionBroker functionBroker) {
        val expression = parseExpression(expressionSource);
        val ctx        = new CompilationContext(functionBroker);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression evaluateExpression(String source) {
        val compiled = compileExpression(source);
        return evaluateCompiled(compiled, evaluationContext());
    }

    public static CompiledExpression evaluateExpression(String source, EvaluationContext ctx) {
        val compiled = compileExpression(source, ctx.functionBroker());
        return evaluateCompiled(compiled, ctx);
    }

    public static CompiledExpression evaluateExpression(String source, FunctionBroker fnBroker,
            Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars, fnBroker);
        val compiled = compileExpression(source, ctx);
        return evaluateCompiled(compiled, evaluationContext(fnBroker));
    }

    private static CompiledExpression evaluateCompiled(CompiledExpression compiled, EvaluationContext ctx) {
        return switch (compiled) {
        case Value v         -> v;
        case PureOperator op -> op.evaluate(ctx);
        default              -> compiled;
        };
    }

    public static ObjectValue toObjectValue(Map<String, Value> map) {
        val builder = ObjectValue.builder();
        map.forEach(builder::put);
        return builder.build();
    }

    public static Voter compilePolicy(String policySource) {
        return compilePolicy(policySource, compilationContext());
    }

    public static Voter compilePolicy(String policySource, CompilationContext ctx) {
        val policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, ctx).applicabilityAndVote();
    }

    public static CompiledPolicy compilePolicyFull(String policySource) {
        return compilePolicyFull(policySource, compilationContext());
    }

    public static CompiledPolicy compilePolicyFull(String policySource, CompilationContext ctx) {
        val policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, ctx);
    }

    public static CompiledPolicySet compilePolicySet(String source) {
        return compilePolicySet(source, compilationContext());
    }

    public static CompiledPolicySet compilePolicySet(String source, CompilationContext ctx) {
        val parsed = DocumentCompiler.parseDocument(source);
        if (!parsed.syntaxErrors().isEmpty()) {
            val errors = String.join("; ", parsed.syntaxErrors());
            throw new IllegalArgumentException("Syntax errors(s) in policy set: " + errors);
        }
        if (!parsed.validationErrors().isEmpty()) {
            val errors = parsed.validationErrors().stream().map(Object::toString).collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Validation error(s) in policy set: " + errors);
        }
        val document = parsed.saplDocument();
        if (!(document instanceof io.sapl.ast.PolicySet policySet)) {
            throw new IllegalArgumentException("Expected a policy set document, got: "
                    + (document == null ? "null" : document.getClass().getSimpleName()) + " | type: " + parsed.type()
                    + " | errorMessage: " + parsed.errorMessage());
        }
        return PolicySetCompiler.compilePolicySet(policySet, ctx);
    }

    /**
     * Evaluates a policy set's production-side voter against {@code ctx}
     * via {@link Voter#evaluate(EvaluationContext)} and returns the
     * resulting {@link Vote}. The returned vote may be {@code null} when
     * the voter has unbound dependencies in this snapshot. Callers that
     * need multi-round streaming evaluation should drive the voter via a
     * {@code TestAttributeBroker}-backed {@code VTVoterEvaluator} instead.
     */
    public static Vote evaluatePolicySet(CompiledPolicySet compiled, EvaluationContext ctx) {
        return compiled.applicabilityAndVote().evaluate(ctx).vote();
    }

    /**
     * Evaluates a policy set's coverage voter against {@code ctx} via
     * {@link CoverageVoter#evaluate(EvaluationContext)} and projects the
     * snapshot result into the legacy {@link VoteWithCoverage} shape.
     * The {@code vote()} component may be {@code null} when the snapshot
     * is incomplete. Multi-round streaming tests should use
     * {@code VTCoverageEvaluator} instead.
     */
    public static VoteWithCoverage evaluatePolicySetWithCoverage(CompiledPolicySet compiled, EvaluationContext ctx) {
        val r = compiled.coverageVoter().evaluate(ctx);
        return new VoteWithCoverage(r.voteResult().vote(), r.coverage());
    }

    public static Vote evaluatePolicySetWithPathEquivalenceCheck(CompiledPolicySet compiled, EvaluationContext ctx) {
        val productionVote = evaluatePolicySet(compiled, ctx);
        val coverageVote   = evaluatePolicySetWithCoverage(compiled, ctx).vote();
        assertThat(productionVote.authorizationDecision().decision())
                .as("Production and coverage paths must produce same decision")
                .isEqualTo(coverageVote.authorizationDecision().decision());
        return productionVote;
    }

    /**
     * Drives a streaming policy set through both the production-side
     * {@link VoterEvaluator} and the coverage-side
     * {@link CoverageEvaluator} against a
     * {@link TestAttributeBroker} pre-armed with
     * the supplied attribute initial values, then asserts both produce
     * the expected decision and agree with each other.
     */
    public static void assertStreamPathEquivalence(CompiledPolicySet compiled, Map<String, Value> attributes,
            EvaluationContext ctx, Decision expectedDecision) {
        try (val broker = new TestAttributeBroker()) {
            for (val entry : attributes.entrySet()) {
                broker.register(entry.getKey(), entry.getValue());
            }
            try (val stream = VoterEvaluator.evaluate(compiled.applicabilityAndVote(), ctx, broker);
                    val cov = CoverageEvaluator.evaluate(compiled.coverageVoter(), ctx, broker)) {
                val productionVote = stream.awaitNext();
                val coverageVote   = cov.awaitNext().voteResult().vote();
                assertThat(productionVote).isNotNull();
                assertThat(productionVote.authorizationDecision().decision()).as("Production path decision")
                        .isEqualTo(expectedDecision);
                assertThat(productionVote.authorizationDecision().decision())
                        .as("Production and coverage paths must produce same decision")
                        .isEqualTo(coverageVote.authorizationDecision().decision());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting evaluator emissions", e);
        }
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource) {
        assertCoverageMatchesProduction(subscriptionJson, policySource, Map.of());
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource,
            String attributeName, Value... values) {
        assertCoverageMatchesProduction(subscriptionJson, policySource, Map.of(attributeName, List.of(values)));
    }

    /**
     * Drives the production voter ({@code applicabilityAndVote}) and the
     * coverage voter through the same {@link TestAttributeBroker}, asserting
     * that both produce equivalent emissions per round. Round 0 fires when
     * the gate opens with primed values. Subsequent rounds publish the
     * next value for each attribute (sequences are consumed in order).
     */
    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource,
            Map<String, List<Value>> attributeSequences) {
        val compiled     = compilePolicyFull(policySource);
        val subscription = parseSubscription(subscriptionJson);
        val baseCtx      = evaluationContext(subscription);
        // At least one round so the initial decision is always compared, even when
        // every supplied sequence is empty (max()==0 would otherwise bypass orElse).
        val rounds = Math.max(1, attributeSequences.values().stream().mapToInt(List::size).max().orElse(1));

        try (val broker = new TestAttributeBroker()) {
            for (val entry : attributeSequences.entrySet()) {
                val seq = entry.getValue();
                if (seq.isEmpty()) {
                    broker.register(entry.getKey());
                } else {
                    broker.register(entry.getKey(), seq.get(0));
                }
            }
            try (val production = VoterEvaluator.evaluate(compiled.applicabilityAndVote(), baseCtx, broker);
                    val coverage = CoverageEvaluator.evaluate(compiled.coverageVoter(), baseCtx, broker)) {
                for (int i = 0; i < rounds; i++) {
                    if (i > 0) {
                        for (val entry : attributeSequences.entrySet()) {
                            val seq = entry.getValue();
                            if (i < seq.size()) {
                                broker.publishByName(entry.getKey(), seq.get(i));
                            }
                        }
                    }
                    val prod = production.awaitNext();
                    val cov  = coverage.awaitNext().voteResult().vote();
                    assertThat(decisionsEquivalent(prod, cov))
                            .as("Emission[%d]: production=%s, coverage=%s", i, prod, cov).isTrue();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting evaluator emissions", e);
        }
    }

    private static boolean decisionsEquivalent(Vote a, Vote b) {
        val authzA = a.authorizationDecision();
        val authzB = b.authorizationDecision();
        return authzA.decision() == authzB.decision() && Objects.equals(authzA.obligations(), authzB.obligations())
                && Objects.equals(authzA.advice(), authzB.advice())
                && Objects.equals(authzA.resource(), authzB.resource());
    }

    public static Value obj(Object... keysAndValues) {
        val builder = ObjectValue.builder();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            builder.put((String) keysAndValues[i], (Value) keysAndValues[i + 1]);
        }
        return builder.build();
    }

    public static ArrayValue array(Value... values) {
        val builder = ArrayValue.builder();
        for (val v : values) {
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
        val compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) compiled).message()).contains(errorMessageContains);
    }

    public static void assertEvaluatesTo(String source, Map<String, Value> variables, Value expected) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        val result   = evaluateCompiled(compiled, evaluationContext());
        assertThat(result).isEqualTo(expected);
    }

    public static void assertEvaluatesToError(String source, Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        val result   = evaluateCompiled(compiled, evaluationContext());
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    public static void assertPureEvaluatesToWithSubject(String source, Value subject, Value expected) {
        val subscription = AuthorizationSubscription.of(subject, Value.NULL, Value.NULL, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evalCtx)).isEqualTo(expected);
    }

    public static void assertPureEvaluatesToWithResource(String source, Value resource, Value expected) {
        val subscription = AuthorizationSubscription.of(Value.NULL, Value.NULL, resource, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evalCtx)).isEqualTo(expected);
    }

    public static void assertPureEvaluatesToErrorWithResource(String source, Value resource) {
        val subscription = AuthorizationSubscription.of(Value.NULL, Value.NULL, resource, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evalCtx)).isInstanceOf(ErrorValue.class);
    }

    public static CompiledExpression evaluateWithResource(String source, Value resource) {
        val subscription = AuthorizationSubscription.of(Value.NULL, Value.NULL, resource, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        return evaluateCompiled(compiled, evalCtx);
    }

    public static void assertPureDependsOnSubscription(String source, boolean expected) {
        val compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).isDependingOnSubscription()).isEqualTo(expected);
    }

    public static void assertIsErrorContaining(CompiledExpression result, String... fragments) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        val message = ((ErrorValue) result).message().toLowerCase();
        for (val fragment : fragments) {
            assertThat(message).contains(fragment.toLowerCase());
        }
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

    // TEST CONTEXT (bridges old variable-at-eval pattern to new compile-time
    // pattern)

    public record TestContext(FunctionBroker functionBroker, ObjectValue variables) {

        public TestContext(FunctionBroker functionBroker) {
            this(functionBroker, Value.EMPTY_OBJECT);
        }

        public CompilationContext compilationContext() {
            val data = new PdpData(variables, Value.EMPTY_OBJECT);
            return new CompilationContext(data, functionBroker);
        }

        public EvaluationContext evaluationContext() {
            return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                    functionBroker);
        }
    }

    public static TestContext testContext(Map<String, Value> variables) {
        return new TestContext(FUNCTION_BROKER, toObjectValue(variables));
    }

    public static TestContext testContext(FunctionBroker fnBroker, Map<String, Value> variables) {
        return new TestContext(fnBroker, toObjectValue(variables));
    }

    public static CompiledExpression evaluateExpression(String source, TestContext ctx) {
        val compiled = compileExpression(source, ctx.compilationContext());
        return evaluateCompiled(compiled, ctx.evaluationContext());
    }

    public record TestPureOperator(Function<EvaluationContext, Value> evaluator, boolean isDependingOnSubscription)
            implements PureOperator {
        private static final long KIND = SemanticHashing.kindHash(TestPureOperator.class);

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

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, System.identityHashCode(evaluator));
        }
    }

    public record TestStreamOperator(Value value) implements StreamOperator {
        @Override
        public ExpressionResult evaluate(EvaluationContext ctx) {
            return new ExpressionResult(value, Map.of());
        }
    }

    /**
     * Entry point for evaluating an expression with attribute values bound.
     * Compiles the source, discovers attribute dependencies, binds the
     * declared attribute values into a snapshot, and re-evaluates against
     * the bound snapshot.
     */
    public static Evaluation evaluate(String saplExpression) {
        return new Evaluation(saplExpression);
    }

    public static class Evaluation {
        private final String              source;
        private final Map<String, Value>  bindings       = new LinkedHashMap<>();
        private AuthorizationSubscription subscription   = DEFAULT_SUBSCRIPTION;
        private FunctionBroker            functionBroker = FUNCTION_BROKER;
        private ObjectValue               variables      = Value.EMPTY_OBJECT;

        private @Nullable CompiledExpression compiled;
        private final Set<SubscriptionKey>   knownKeys = new LinkedHashSet<>();

        private Evaluation(String source) {
            this.source = source;
        }

        /** Bind every attribute named {@code name} to {@code value}. */
        public Evaluation with(String name, Value value) {
            bindings.put(name, value);
            return this;
        }

        /** Bind a batch of attributes by name. */
        public Evaluation with(Map<String, Value> attributes) {
            bindings.putAll(attributes);
            return this;
        }

        /** Override the authorization subscription. */
        public Evaluation withSubscription(AuthorizationSubscription sub) {
            this.subscription = sub;
            return this;
        }

        /** Override the subject in the authorization subscription. */
        public Evaluation withSubject(Value subject) {
            this.subscription = AuthorizationSubscription.of(subject, subscription.action(), subscription.resource(),
                    subscription.environment());
            return this;
        }

        /** Use a custom function broker for compile and evaluate. */
        public Evaluation withFunctionBroker(FunctionBroker broker) {
            this.functionBroker = broker;
            return this;
        }

        /** Bind PDP-level variables visible during compilation. */
        public Evaluation withVariables(Map<String, Value> vars) {
            this.variables = toObjectValue(vars);
            return this;
        }

        /** The computed value. {@code null} if a needed attribute was not bound. */
        public Value value() {
            return result().result();
        }

        /**
         * Two-step convenience: a discovery round followed by a bind-and-evaluate
         * round. Equivalent to calling {@link #step()} twice. Returns the second
         * round's result. For expressions whose lazy operators discover
         * dependencies incrementally across more than one round, returns
         * {@code null} and the test should drive {@link #step()} explicitly.
         */
        public ExpressionResult result() {
            step();
            return step();
        }

        /**
         * Performs one evaluation round. Builds a snapshot from every key
         * discovered in any prior round, matched by attribute name against the
         * current bindings, evaluates once, then folds this round's
         * dependencies into the accumulator for the next call.
         * <p>
         * The accumulator across calls lets tests drive multi-round scenarios
         * by interleaving {@link #with(String, Value)} and {@code step()}. The
         * returned result's dependency map reflects exactly what the
         * expression touched in this round, including lazy short-circuit
         * shrinkage and lazy re-subscribe growth.
         */
        public ExpressionResult step() {
            if (compiled == null) {
                val ctx = compilationContext(variables, functionBroker);
                compiled = compileExpression(source, ctx);
            }
            val baseCtx = EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription,
                    functionBroker);
            return switch (compiled) {
            case Value v          -> new ExpressionResult(v, Map.of());
            case PureOperator p   -> new ExpressionResult(p.evaluate(baseCtx), Map.of());
            case StreamOperator s -> stepStream(s, baseCtx);
            };
        }

        private ExpressionResult stepStream(StreamOperator stream, EvaluationContext baseCtx) {
            val now      = REFERENCE;
            val snapshot = new HashMap<SubscriptionKey, AttributeSnapshot>();
            for (val key : knownKeys) {
                val bound = bindings.get(key.invocation().attributeName());
                if (bound != null) {
                    snapshot.put(key, new AttributeSnapshot(bound, now));
                }
            }
            val result = stream.evaluate(baseCtx.withSnapshot(snapshot));
            knownKeys.addAll(result.dependencies().keySet());
            return result;
        }

        /** Asserts there is exactly one dependency and returns its key. */
        public SubscriptionKey onlySubscriptionKey() {
            val keys = result().dependencies().keySet();
            if (keys.size() != 1) {
                throw new AssertionError("Expected exactly one subscription key, got " + keys.size() + ": " + keys);
            }
            return keys.iterator().next();
        }

        /** Asserts there is exactly one dependency and returns its invocation. */
        public AttributeFinderInvocation onlyInvocation() {
            return onlySubscriptionKey().invocation();
        }

        /** All attribute invocations the evaluation observed (insertion order). */
        public List<AttributeFinderInvocation> invocations() {
            return result().dependencies().keySet().stream().map(SubscriptionKey::invocation).toList();
        }
    }
}
