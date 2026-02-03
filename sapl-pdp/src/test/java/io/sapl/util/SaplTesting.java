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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PdpData;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.ast.SaplDocument;
import io.sapl.ast.Statement;
import io.sapl.compiler.document.AstTransformer;
import io.sapl.compiler.document.Document;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.policy.CompiledPolicy;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified test utilities for SAPL expression parsing, compilation, evaluation,
 * and assertions.
 */
@UtilityClass
public class SaplTesting {

    private static final String DEFAULT_PDP_ID    = "testPdp";
    private static final String DEFAULT_CONFIG_ID = "testConfig";
    private static final String DEFAULT_SUB_ID    = "testSubscription";

    public static final SourceLocation        TEST_LOCATION           = new SourceLocation("test", "", 0, 0, 1, 1, 1,
            1);
    public static final FunctionBroker        FUNCTION_BROKER;
    public static final AttributeBroker       ATTRIBUTE_BROKER;
    public static final DefaultFunctionBroker DEFAULT_FUNCTION_BROKER = new DefaultFunctionBroker();

    private static final JsonMapper            MAPPER      = JsonMapper.builder().addModule(new SaplJacksonModule())
            .build();
    private static final StandaloneTransformer TRANSFORMER = new StandaloneTransformer();

    public static final AuthorizationSubscription DEFAULT_SUBSCRIPTION = AuthorizationSubscription
            .of(Value.of("testSubject"), Value.of("testAction"), Value.of("testResource"), Value.of("testEnvironment"));

    static {
        val functionBroker = new DefaultFunctionBroker();
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

    // ========================================================================
    // PARSING UTILITIES
    // ========================================================================

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

    // ========================================================================
    // CONTEXT FACTORIES
    // ========================================================================

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

    public static CompilationContext compilationContext(ObjectValue variables) {
        val data = new PdpData(variables, Value.EMPTY_OBJECT);
        return new CompilationContext(data, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static CompilationContext compilationContext(ObjectValue variables, AttributeBroker attrBroker) {
        val data = new PdpData(variables, Value.EMPTY_OBJECT);
        return new CompilationContext(data, FUNCTION_BROKER, attrBroker);
    }

    public static CompilationContext compilationContext(ObjectValue variables, FunctionBroker fnBroker,
            AttributeBroker attrBroker) {
        val data = new PdpData(variables, Value.EMPTY_OBJECT);
        return new CompilationContext(data, fnBroker, attrBroker);
    }

    public static CompilationContext compilationContextWithSecrets(ObjectValue variables, ObjectValue secrets) {
        val data = new PdpData(variables, secrets);
        return new CompilationContext(data, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static EvaluationContext evaluationContext() {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static EvaluationContext evaluationContext(AttributeBroker attributeBroker) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                FUNCTION_BROKER, attributeBroker);
    }

    public static EvaluationContext evaluationContext(AuthorizationSubscription subscription) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription, FUNCTION_BROKER,
                ATTRIBUTE_BROKER);
    }

    public static EvaluationContext evaluationContext(AuthorizationSubscription subscription,
            AttributeBroker attributeBroker) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription, FUNCTION_BROKER,
                attributeBroker);
    }

    public static EvaluationContext evaluationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                functionBroker, attributeBroker);
    }

    public static EvaluationContext subscriptionContext(String subscriptionJson) {
        val subscription = parseSubscription(subscriptionJson);
        return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, subscription, FUNCTION_BROKER,
                ATTRIBUTE_BROKER);
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

    // ========================================================================
    // BROKER FACTORIES
    // ========================================================================

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
                val values = attributes.get(invocation.attributeName());
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
                val values = attributeSequences.get(invocation.attributeName());
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
                val value = attributeValues.get(invocation.attributeName());
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

    // ========================================================================
    // EXPRESSION COMPILATION AND EVALUATION
    // ========================================================================

    public static CompiledExpression compileExpression(String expressionSource) {
        return compileExpression(expressionSource, FUNCTION_BROKER, ATTRIBUTE_BROKER);
    }

    public static CompiledExpression compileExpression(String expressionSource, AttributeBroker attributeBroker) {
        return compileExpression(expressionSource, FUNCTION_BROKER, attributeBroker);
    }

    public static CompiledExpression compileExpression(String expressionSource, CompilationContext ctx) {
        val expression = parseExpression(expressionSource);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression compileExpression(String expressionSource, FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        val expression = parseExpression(expressionSource);
        val ctx        = new CompilationContext(functionBroker, attributeBroker);
        return ExpressionCompiler.compile(expression, ctx);
    }

    public static CompiledExpression evaluateExpression(String source) {
        val compiled = compileExpression(source);
        return evaluate(compiled, evaluationContext());
    }

    public static CompiledExpression evaluateExpression(String source, EvaluationContext ctx) {
        val compiled = compileExpression(source, ctx.functionBroker(), ctx.attributeBroker());
        return evaluate(compiled, ctx);
    }

    public static CompiledExpression evaluateExpression(String source, FunctionBroker fnBroker,
            Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars, fnBroker, ATTRIBUTE_BROKER);
        val compiled = compileExpression(source, ctx);
        return evaluate(compiled, evaluationContext(fnBroker, ATTRIBUTE_BROKER));
    }

    public static CompiledExpression evaluateExpression(String source, FunctionBroker fnBroker,
            AttributeBroker attrBroker, Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars, fnBroker, attrBroker);
        val compiled = compileExpression(source, ctx);
        return evaluate(compiled, evaluationContext(fnBroker, attrBroker));
    }

    private static CompiledExpression evaluate(CompiledExpression compiled, EvaluationContext ctx) {
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

    // ========================================================================
    // POLICY COMPILATION AND EVALUATION
    // ========================================================================

    public static Voter compilePolicy(String policySource) {
        return compilePolicy(policySource, compilationContext());
    }

    public static Voter compilePolicy(String policySource, AttributeBroker attrBroker) {
        return compilePolicy(policySource, compilationContext(attrBroker));
    }

    public static Voter compilePolicy(String policySource, CompilationContext ctx) {
        val policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, ctx).applicabilityAndVote();
    }

    public static Flux<Vote> evaluatePolicy(String subscriptionJson, String policySource) {
        return evaluatePolicy(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static Flux<Vote> evaluatePolicy(String subscriptionJson, String policySource, AttributeBroker attrBroker) {
        return evaluatePolicy(subscriptionJson, policySource, compilationContext(attrBroker), attrBroker);
    }

    public static Flux<Vote> evaluatePolicy(String subscriptionJson, String policySource,
            CompilationContext compilationCtx, AttributeBroker attrBroker) {
        val subscription  = parseSubscription(subscriptionJson);
        val compiled      = compilePolicy(policySource, compilationCtx);
        val evaluationCtx = evaluationContext(subscription, attrBroker);
        return evaluatePolicyVoter(compiled, evaluationCtx);
    }

    public static Flux<Vote> evaluatePolicyVoter(Voter compiled, EvaluationContext evalCtx) {
        return switch (compiled) {
        case Vote vote          -> Flux.just(vote);
        case PureVoter pure     -> Flux.just(pure.vote(evalCtx));
        case StreamVoter stream -> stream.vote().contextWrite(c -> c.put(EvaluationContext.class, evalCtx));
        };
    }

    public static CompiledPolicy compilePolicyFull(String policySource) {
        return compilePolicyFull(policySource, compilationContext());
    }

    public static CompiledPolicy compilePolicyFull(String policySource, AttributeBroker attrBroker) {
        return compilePolicyFull(policySource, compilationContext(attrBroker));
    }

    public static CompiledPolicy compilePolicyFull(String policySource, CompilationContext ctx) {
        val policy = parsePolicy(policySource);
        return PolicyCompiler.compilePolicy(policy, ctx);
    }

    public static Flux<VoteWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource) {
        return evaluatePolicyWithCoverage(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static Flux<VoteWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource,
            AttributeBroker attrBroker) {
        return evaluatePolicyWithCoverage(subscriptionJson, policySource, compilationContext(attrBroker), attrBroker);
    }

    public static Flux<VoteWithCoverage> evaluatePolicyWithCoverage(String subscriptionJson, String policySource,
            CompilationContext compilationCtx, AttributeBroker attrBroker) {
        val subscription  = parseSubscription(subscriptionJson);
        val compiled      = compilePolicyFull(policySource, compilationCtx);
        val evaluationCtx = evaluationContext(subscription, attrBroker);
        return compiled.coverage().contextWrite(c -> c.put(EvaluationContext.class, evaluationCtx));
    }

    // ========================================================================
    // POLICY SET COMPILATION AND EVALUATION
    // ========================================================================

    public static CompiledPolicySet compilePolicySet(String source) {
        return compilePolicySet(source, compilationContext());
    }

    public static CompiledPolicySet compilePolicySet(String source, AttributeBroker attrBroker) {
        return compilePolicySet(source, compilationContext(attrBroker));
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

    public static Vote evaluatePolicySet(CompiledPolicySet compiled, EvaluationContext ctx) {
        val voter = compiled.applicabilityAndVote();
        return switch (voter) {
        case Vote vote          -> vote;
        case PureVoter pure     -> pure.vote(ctx);
        case StreamVoter stream ->
            stream.vote().contextWrite(ctxView -> ctxView.put(EvaluationContext.class, ctx)).blockFirst();
        };
    }

    public static VoteWithCoverage evaluatePolicySetWithCoverage(CompiledPolicySet compiled, EvaluationContext ctx) {
        return compiled.coverage().contextWrite(ctxView -> ctxView.put(EvaluationContext.class, ctx)).blockFirst();
    }

    public static Vote evaluatePolicySetWithPathEquivalenceCheck(CompiledPolicySet compiled, EvaluationContext ctx) {
        val productionVote = evaluatePolicySet(compiled, ctx);
        val coverageVote   = evaluatePolicySetWithCoverage(compiled, ctx).vote();
        assertThat(productionVote.authorizationDecision().decision())
                .as("Production and coverage paths must produce same decision")
                .isEqualTo(coverageVote.authorizationDecision().decision());
        return productionVote;
    }

    public static void assertStreamPathEquivalence(CompiledPolicySet compiled, EvaluationContext ctx,
            Decision expectedDecision) {
        val productionVote = ((StreamVoter) compiled.applicabilityAndVote()).vote()
                .contextWrite(c -> c.put(EvaluationContext.class, ctx)).blockFirst();
        val coverageVote   = evaluatePolicySetWithCoverage(compiled, ctx).vote();
        assertThat(productionVote).isNotNull();
        assertThat(productionVote.authorizationDecision().decision()).as("Production path decision")
                .isEqualTo(expectedDecision);
        assertThat(productionVote.authorizationDecision().decision())
                .as("Production and coverage paths must produce same decision")
                .isEqualTo(coverageVote.authorizationDecision().decision());
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource) {
        assertCoverageMatchesProduction(subscriptionJson, policySource, ATTRIBUTE_BROKER);
    }

    public static void assertCoverageMatchesProduction(String subscriptionJson, String policySource,
            AttributeBroker attrBroker) {
        val prodList = evaluatePolicy(subscriptionJson, policySource, attrBroker).collectList()
                .block(Duration.ofSeconds(5));
        val covList  = evaluatePolicyWithCoverage(subscriptionJson, policySource, attrBroker)
                .map(VoteWithCoverage::vote).collectList().block(Duration.ofSeconds(5));

        assertThat(covList).as("Number of emissions").hasSameSizeAs(prodList);
        for (int i = 0; i < Objects.requireNonNull(prodList).size(); i++) {
            val prod = prodList.get(i);
            val cov  = Objects.requireNonNull(covList).get(i);
            assertThat(decisionsEquivalent(prod, cov)).as("Emission[%d]: production=%s, coverage=%s", i, prod, cov)
                    .isTrue();
        }
    }

    private static boolean decisionsEquivalent(Vote a, Vote b) {
        val authzA = a.authorizationDecision();
        val authzB = b.authorizationDecision();
        return authzA.decision() == authzB.decision() && Objects.equals(authzA.obligations(), authzB.obligations())
                && Objects.equals(authzA.advice(), authzB.advice())
                && Objects.equals(authzA.resource(), authzB.resource());
    }

    // ========================================================================
    // VALUE BUILDERS
    // ========================================================================

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

    // ========================================================================
    // EXPRESSION ASSERTIONS
    // ========================================================================

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

    public static void assertPureEvaluatesTo(String source, EvaluationContext ctx, Value expected) {
        val compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(ctx)).isEqualTo(expected);
    }

    public static void assertPureEvaluatesTo(String source, Map<String, Value> variables, Value expected) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evaluationContext())).isEqualTo(expected);
    }

    public static void assertEvaluatesTo(String source, Map<String, Value> variables, Value expected) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        val result   = evaluate(compiled, evaluationContext());
        assertThat(result).isEqualTo(expected);
    }

    public static void assertEvaluatesToError(String source, Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        val result   = evaluate(compiled, evaluationContext());
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    public static void assertPureEvaluatesToError(String source, EvaluationContext ctx) {
        val compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(ctx)).isInstanceOf(ErrorValue.class);
    }

    public static void assertPureEvaluatesToError(String source, Map<String, Value> variables) {
        val vars     = toObjectValue(variables);
        val ctx      = compilationContext(vars);
        val compiled = compileExpression(source, ctx);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).evaluate(evaluationContext())).isInstanceOf(ErrorValue.class);
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

    public static CompiledExpression evaluateWithSubject(String source, Value subject) {
        val subscription = AuthorizationSubscription.of(subject, Value.NULL, Value.NULL, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        return evaluate(compiled, evalCtx);
    }

    public static CompiledExpression evaluateWithResource(String source, Value resource) {
        val subscription = AuthorizationSubscription.of(Value.NULL, Value.NULL, resource, Value.NULL);
        val evalCtx      = evaluationContext(subscription);
        val compiled     = compileExpression(source);
        return evaluate(compiled, evalCtx);
    }

    public static void assertPureDependsOnSubscription(String source, boolean expected) {
        val compiled = compileExpression(source);
        assertThat(compiled).isInstanceOf(PureOperator.class);
        assertThat(((PureOperator) compiled).isDependingOnSubscription()).isEqualTo(expected);
    }

    public static void assertEvaluatesToError(String source) {
        assertThat(evaluateExpression(source)).isInstanceOf(ErrorValue.class);
    }

    public static void assertEvaluatesToError(String source, String messageFragment) {
        val result = evaluateExpression(source);
        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains(messageFragment);
    }

    public static void assertIsError(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    public static void assertIsErrorContaining(CompiledExpression result, String... fragments) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        val message = ((ErrorValue) result).message().toLowerCase();
        for (val fragment : fragments) {
            assertThat(message).contains(fragment.toLowerCase());
        }
    }

    public static String errorMessage(CompiledExpression result) {
        assertThat(result).isInstanceOf(ErrorValue.class);
        return ((ErrorValue) result).message();
    }

    // ========================================================================
    // STRATUM ASSERTIONS
    // ========================================================================

    public static Stratum getStratum(CompiledExpression compiled) {
        return switch (compiled) {
        case ErrorValue ignored    -> null;
        case PureOperator p        -> p.isDependingOnSubscription() ? Stratum.PURE_SUB : Stratum.PURE_NON_SUB;
        case StreamOperator ignore -> Stratum.STREAM;
        case Value ignored         -> Stratum.VALUE;
        default                    -> null;
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
        for (val s : inputs) {
            if (s.level > maxLevel) {
                maxLevel = s.level;
            }
        }
        if (maxLevel <= 2) {
            return Stratum.VALUE;
        }
        return maxLevel == 3 ? Stratum.PURE_SUB : Stratum.STREAM;
    }

    // ========================================================================
    // STREAM VERIFICATION
    // ========================================================================

    @SafeVarargs
    public static void verifyStream(StreamOperator op, EvaluationContext ctx, Consumer<TracedValue>... assertions) {
        val                            stream   = op.stream().contextWrite(c -> c.put(EvaluationContext.class, ctx));
        StepVerifier.Step<TracedValue> verifier = StepVerifier.create(stream);
        for (val assertion : assertions) {
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
            val exp = expected[i];
            assertions[i] = tv -> assertThat(tv.value()).isEqualTo(exp);
        }
        verifyStream(op, ctx, assertions);
    }

    public static void verifyStreamEmitsError(StreamOperator op, EvaluationContext ctx, String messageFragment) {
        verifyStream(op, ctx, tv -> assertThat(tv.value()).isInstanceOf(ErrorValue.class)
                .extracting(v -> ((ErrorValue) v).message()).asString().contains(messageFragment));
    }

    // ========================================================================
    // TEST CONTEXT (bridges old variable-at-eval pattern to new compile-time
    // pattern)
    // ========================================================================

    public record TestContext(FunctionBroker functionBroker, AttributeBroker attributeBroker, ObjectValue variables) {

        public TestContext(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
            this(functionBroker, attributeBroker, Value.EMPTY_OBJECT);
        }

        public CompilationContext compilationContext() {
            val data = new PdpData(variables, Value.EMPTY_OBJECT);
            return new CompilationContext(data, functionBroker, attributeBroker);
        }

        public EvaluationContext evaluationContext() {
            return EvaluationContext.of(DEFAULT_PDP_ID, DEFAULT_CONFIG_ID, DEFAULT_SUB_ID, DEFAULT_SUBSCRIPTION,
                    functionBroker, attributeBroker);
        }
    }

    public static TestContext testContext(Map<String, Value> variables) {
        return new TestContext(FUNCTION_BROKER, ATTRIBUTE_BROKER, toObjectValue(variables));
    }

    public static TestContext testContext(FunctionBroker fnBroker, Map<String, Value> variables) {
        return new TestContext(fnBroker, ATTRIBUTE_BROKER, toObjectValue(variables));
    }

    public static TestContext testContext(FunctionBroker fnBroker, AttributeBroker attrBroker,
            Map<String, Value> variables) {
        return new TestContext(fnBroker, attrBroker, toObjectValue(variables));
    }

    public static TestContext testContext(AttributeBroker attrBroker, Map<String, Value> variables) {
        return new TestContext(FUNCTION_BROKER, attrBroker, toObjectValue(variables));
    }

    public static TestContext testContext(AttributeBroker attrBroker, Value subject) {
        return new TestContext(FUNCTION_BROKER, attrBroker, toObjectValue(Map.of("subject", subject)));
    }

    public static CompiledExpression evaluateExpression(String source, TestContext ctx) {
        val compiled = compileExpression(source, ctx.compilationContext());
        return evaluate(compiled, ctx.evaluationContext());
    }

    // ========================================================================
    // TEST IMPLEMENTATIONS
    // ========================================================================

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
