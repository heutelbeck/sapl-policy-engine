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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.*;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.val;
import reactor.core.publisher.Flux;

import java.time.Clock;

/**
 * Test utility for single-document policy evaluation.
 * <p>
 * Loads standard function libraries and Policy Information Points (PIPs),
 * compiles a single SAPL policy document, and
 * evaluates it against authorization subscriptions.
 * <p>
 * Loaded function libraries:
 * <ul>
 * <li>TemporalFunctionLibrary - temporal functions</li>
 * <li>ArrayFunctionLibrary - array manipulation</li>
 * <li>FilterFunctionLibrary - filtering operations</li>
 * <li>BitwiseFunctionLibrary - bitwise operations</li>
 * <li>SaplFunctionLibrary - core SAPL functions</li>
 * <li>SchemaValidationLibrary - JSON schema validation</li>
 * </ul>
 * <p>
 * Loaded Policy Information Points:
 * <ul>
 * <li>TimePolicyInformationPoint - time-based attributes (time.now, etc.)</li>
 * </ul>
 * <p>
 * Evaluation logic:
 * <ul>
 * <li>Check match expression - if error -> INDETERMINATE</li>
 * <li>If match returns false -> NOT_APPLICABLE</li>
 * <li>If match returns true -> evaluate decision expression and convert to
 * AuthorizationDecision</li>
 * </ul>
 */
public final class SingleDocumentPolicyDecisionPoint implements PolicyDecisionPoint {

    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    private final CompilationContext compilationContext;
    private CompiledPolicy           compiledPolicy;

    /**
     * Creates a new SingleDocumentPolicyDecisionPoint with default function
     * libraries and PIPs.
     *
     * @throws InitializationException
     * if function or attribute library loading fails
     */
    public SingleDocumentPolicyDecisionPoint() throws InitializationException {
        val clock               = Clock.systemUTC();
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(clock);
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        // Load standard function libraries
        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(BitwiseFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SaplFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SchemaValidationLibrary.class);

        // Load standard policy information points
        attributeBroker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(clock));

        this.compilationContext = new CompilationContext(functionBroker, attributeBroker);
    }

    /**
     * Creates a SingleDocumentPolicyDecisionPoint with custom compilation context.
     *
     * @param compilationContext
     * the compilation context with configured function and attribute brokers
     */
    public SingleDocumentPolicyDecisionPoint(CompilationContext compilationContext) {
        this.compilationContext = compilationContext;
    }

    /**
     * Loads and compiles a SAPL policy document.
     *
     * @param document
     * the SAPL policy document as a string
     *
     * @throws SaplCompilerException
     * if parsing or compilation fails
     */
    public void loadDocument(String document) {
        val parsed = PARSER.parse(document);
        this.compiledPolicy = SaplCompiler.compileDocument(parsed, compilationContext);
    }

    /**
     * Evaluates the loaded policy against an authorization subscription.
     * <p>
     * Evaluation flow:
     * <ol>
     * <li>Check if policy matches (evaluate match expression)</li>
     * <li>If match expression produces error -> return INDETERMINATE</li>
     * <li>If match returns false -> return NOT_APPLICABLE</li>
     * <li>If match returns true -> evaluate decision expression and convert to
     * AuthorizationDecision</li>
     * </ol>
     *
     * @param authorizationSubscription
     * the authorization subscription
     *
     * @return Flux of authorization decisions
     *
     * @throws IllegalStateException
     * if no document has been loaded
     */
    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        if (compiledPolicy == null) {
            throw new IllegalStateException("No policy document loaded. Call loadDocument() first.");
        }

        val evaluationContext = new EvaluationContext("", "", "", authorizationSubscription,
                compilationContext.getFunctionBroker(), compilationContext.getAttributeBroker());

        // Evaluate match expression
        val matchResult = evalValueOrPure(compiledPolicy.matchExpression(), evaluationContext);

        // Handle match errors
        if (matchResult instanceof ErrorValue) {
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }

        // Handle non-boolean match results
        if (!(matchResult instanceof BooleanValue matchBool)) {
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }

        // Policy does not match
        if (BooleanValue.FALSE.equals(matchBool)) {
            return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
        }

        // Policy matches - evaluate decision expression
        return evaluateDecisionExpression(evaluationContext);
    }

    private Value evalValueOrPure(CompiledExpression e, EvaluationContext ctx) {
        if (e instanceof Value v) {
            return v;
        }
        return ((PureExpression) e).evaluate(ctx);
    }

    /**
     * Synchronous pure evaluation - bypasses Reactor entirely.
     * Only works for pure policies (no attribute streams).
     * For policies with obligations/advice/transform, falls back to blocking.
     *
     * @param authorizationSubscription
     * the authorization subscription
     *
     * @return the authorization decision directly (no Flux)
     *
     * @throws IllegalStateException
     * if no document has been loaded or policy is not pure
     */
    @Override
    public AuthorizationDecision decidePure(AuthorizationSubscription authorizationSubscription) {
        if (compiledPolicy == null) {
            throw new IllegalStateException("No policy document loaded. Call loadDocument() first.");
        }

        val evaluationContext = new EvaluationContext("", "", "", authorizationSubscription,
                compilationContext.getFunctionBroker(), compilationContext.getAttributeBroker());

        // Evaluate match expression
        val matchResult = evalValueOrPure(compiledPolicy.matchExpression(), evaluationContext);

        // Handle match errors
        if (matchResult instanceof ErrorValue) {
            return AuthorizationDecision.INDETERMINATE;
        }

        // Handle non-boolean match results
        if (!(matchResult instanceof BooleanValue matchBool)) {
            return AuthorizationDecision.INDETERMINATE;
        }

        // Policy does not match
        if (BooleanValue.FALSE.equals(matchBool)) {
            return AuthorizationDecision.NOT_APPLICABLE;
        }

        // Policy matches - evaluate decision expression
        val decisionExpression = compiledPolicy.decisionExpression();

        return switch (decisionExpression) {
        case Value decisionValue         -> AuthorizationDecision.of(decisionValue);
        case PureExpression pureExpr     -> AuthorizationDecision.of(pureExpr.evaluate(evaluationContext));
        case StreamExpression streamExpr -> {
            // For stream expressions (policies with obligations/advice/transform),
            // we need to use blocking - but avoid Flux.just() overhead
            val stream = streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
            yield AuthorizationDecision.of(stream.blockFirst());
        }
        default                          -> AuthorizationDecision.INDETERMINATE;
        };
    }

    private Flux<AuthorizationDecision> evaluateDecisionExpression(EvaluationContext evaluationContext) {
        val decisionExpression = compiledPolicy.decisionExpression();

        return switch (decisionExpression) {
        case Value decisionValue         -> Flux.just(AuthorizationDecision.of(decisionValue));
        case PureExpression pureExpr     -> {
            val decisionValue = pureExpr.evaluate(evaluationContext);
            yield Flux.just(AuthorizationDecision.of(decisionValue));
        }
        case StreamExpression streamExpr -> {
            val stream = streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
            yield stream.map(AuthorizationDecision::of);
        }
        default                          -> Flux.just(AuthorizationDecision.INDETERMINATE);
        };
    }
}
