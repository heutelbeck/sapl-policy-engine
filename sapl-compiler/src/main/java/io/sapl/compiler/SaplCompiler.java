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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.grammar.sapl.*;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Main entry point for compiling SAPL documents into executable policies.
 * <p>
 * Transforms parsed SAPL documents (policies and policy sets) into
 * {@link CompiledPolicy} instances that can be
 * evaluated by combining algorithms. The compilation process:
 * <ul>
 * <li>Validates and compiles target expressions into match expressions</li>
 * <li>Compiles policy bodies with conditions and value definitions</li>
 * <li>Compiles obligations, advice, and resource transformations</li>
 * <li>Handles schema validation against subscription elements</li>
 * <li>Resolves combining algorithms for policy sets</li>
 * </ul>
 * <p>
 * The compiler produces three expression types based on the nature of the
 * compiled code:
 * <ul>
 * <li>{@link Value} - Constant expressions that can be evaluated at compile
 * time</li>
 * <li>{@link PureExpression} - Expressions requiring runtime evaluation but no
 * streaming</li>
 * <li>{@link StreamExpression} - Reactive expressions that may emit multiple
 * values over time</li>
 * </ul>
 * <p>
 * Target expression constraints:
 * <ul>
 * <li>Must not contain attribute finders ({@code <>}) - these require
 * streaming</li>
 * <li>Must not contain unresolved relative references ({@code @})</li>
 * <li>Must not always evaluate to false (policy would never apply)</li>
 * <li>Must evaluate to boolean values</li>
 * </ul>
 *
 * @see CompiledPolicy
 * @see ExpressionCompiler
 * @see CombiningAlgorithmCompiler
 */
@UtilityClass
public class SaplCompiler {

    private static final String ENV_SCHEMAS = "SCHEMAS";

    private static final String EXPRESSION_TARGET              = "Target";
    private static final String EXPRESSION_VARIABLE_DEFINITION = "variable definition for %s";

    private static final String ERROR_POLICY_ALWAYS_FALSE                 = "Error compiling policy: %s expression always returns false. These rules will never be applicable.";
    private static final String ERROR_POLICY_ALWAYS_NON_BOOLEAN           = "Error compiling policy: %s expression always returns a non Boolean value: %s.";
    private static final String ERROR_POLICY_COMPILE_TIME_ERROR           = "Error compiling policy: %s expression yielded a compile time error: %s.";
    private static final String ERROR_POLICY_CONTAINS_ATTRIBUTE_FINDERS   = "Error compiling policy: %s expression must not contain access to any <> attribute finders.";
    private static final String ERROR_POLICY_UNRESOLVED_RELATIVE          = "Error compiling policy: %s expression contains an unresolved relative reference (e.g., @) and can never be evaluated correctly.";
    private static final String ERROR_SCHEMA_INVALID_SUBSCRIPTION_ELEMENT = "Schema must reference one of the four subscription identifiers: subject, action, resource, environment, but was: %s.";
    private static final String ERROR_SCHEMA_NOT_OBJECT_VALUE             = "Schema must evaluate to an ObjectValue, but was: %s";
    private static final String ERROR_TARGET_EXPECTED_BOOLEAN             = "Expected a Boolean value to be returned from the target expression, found %s";
    private static final String ERROR_UNEXPECTED_ENTITLEMENT              = "Unexpected entitlement: %s";
    private static final String ERROR_UNEXPECTED_POLICY_ELEMENT           = "Unexpected policy element: %s";
    private static final String ERROR_UNEXPECTED_TARGET_TYPE              = "Unexpected target expression type: %s";
    private static final String ERROR_VARIABLE_ALREADY_DEFINED            = "Variable already defined: %s.";
    private static final String ERROR_VARIABLE_ALREADY_EXISTS             = "Variable already exists: %s";
    private static final String ERROR_VARIABLE_NAME_MISSING               = "Variable name missing.";

    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    public CompiledPolicy compile(String source, CompilationContext context) {
        val document = PARSER.parseDocument(source);
        if (document.isInvalid()) {
            throw new SaplCompilerException("Unable to parse document. %s".formatted(document.errorMessage()));
        }
        context.setDocumentSource(source);
        context.setDocument(document);
        val compiledPolicy = compileDocument(document.sapl(), context);
        context.setDocument(null);
        context.setDocumentSource(null);
        return compiledPolicy;
    }

    /**
     * Compiles a SAPL document into an executable policy.
     *
     * @param document
     * the parsed SAPL document (policy or policy set)
     * @param context
     * the compilation context (reused across documents)
     *
     * @return the compiled policy ready for evaluation
     *
     * @throws SaplCompilerException
     * if the document contains invalid constructs
     */
    public CompiledPolicy compileDocument(SAPL document, CompilationContext context) {
        context.resetForNextDocument();
        context.addAllImports(document.getImports());
        val schemaCheckingExpression = compileSchemas(document.getSchemas(), context);
        val policyElement            = document.getPolicyElement();
        return switch (policyElement) {
        case Policy policy       -> compilePolicy(policy, schemaCheckingExpression, context);
        case PolicySet policySet -> compilePolicySet(policySet, schemaCheckingExpression, context);
        default                  ->
            throw new SaplCompilerException(ERROR_UNEXPECTED_POLICY_ELEMENT.formatted(policyElement), policyElement);
        };
    }

    private CompiledPolicy compilePolicy(Policy policy, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        context.resetForNextPolicy();
        val name               = policy.getSaplName();
        val matchExpression    = compileMatchExpression(policy.getTargetExpression(), schemaCheckingExpression,
                context);
        val decisionExpression = compileDecisionExpression(policy, context);
        return new CompiledPolicy(name, matchExpression, decisionExpression);
    }

    private CompiledExpression compileMatchExpression(Expression targetExpression,
            CompiledExpression schemaCheckingExpression, CompilationContext context) {
        val compiledTargetExpression = ExpressionCompiler.compileLazyAnd(targetExpression, schemaCheckingExpression,
                targetExpression, context);
        assertExpressionSuitableForTarget(targetExpression, compiledTargetExpression);
        return switch (compiledTargetExpression) {
        case PureExpression pureExpression ->
            compileTargetExpressionToMatchExpression(targetExpression, pureExpression);
        case Value value                   -> booleanValueOrErrorInTarget(targetExpression, value);
        default                            -> throw new SaplCompilerException(
                ERROR_UNEXPECTED_TARGET_TYPE.formatted(targetExpression.getClass()), targetExpression);
        };
    }

    private CompiledExpression compileDecisionExpression(Policy policy, CompilationContext context) {
        val entitlement    = decisionOf(policy.getEntitlement());
        val body           = compileBody(policy.getBody(), context);
        val obligations    = compileListOfExpressions(policy.getObligations(), context);
        val advice         = compileListOfExpressions(policy.getAdvice(), context);
        val transformation = ExpressionCompiler.compileExpression(policy.getTransformation(), context);

        if (hasCompileTimeErrors(body, obligations, advice, transformation)) {
            return buildIndeterminateDecision();
        }

        if (body instanceof Value bodyValue) {
            return compileConstantBodyDecision(entitlement, bodyValue, obligations, advice, transformation);
        }

        if (body instanceof PureExpression bodyPure) {
            return compilePureBodyDecision(entitlement, bodyPure, obligations, advice, transformation);
        }

        return compileStreamingBodyDecision(entitlement, body, obligations, advice, transformation);
    }

    private static boolean hasCompileTimeErrors(CompiledExpression body, List<CompiledExpression> obligations,
            List<CompiledExpression> advice, CompiledExpression transformation) {
        if (body instanceof ErrorValue) {
            return true;
        }
        if (containsAnyError(obligations)) {
            return true;
        }
        if (containsAnyError(advice)) {
            return true;
        }
        return transformation instanceof ErrorValue;
    }

    private static boolean containsAnyError(List<CompiledExpression> expressions) {
        return expressions.stream().anyMatch(ErrorValue.class::isInstance);
    }

    private static CompiledExpression compileConstantBodyDecision(Decision entitlement, Value bodyValue,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (isInvalidBooleanBody(bodyValue)) {
            return buildIndeterminateDecision();
        }

        if (isFalseBody(bodyValue)) {
            return buildNotApplicableDecision();
        }

        val fullyConstantDecision = tryCompileFullyConstantDecision(entitlement, obligations, advice, transformation);
        return fullyConstantDecision != null ? fullyConstantDecision
                : compileConstantBodyWithMixedConstraints(entitlement, bodyValue, obligations, advice, transformation);
    }

    private static CompiledExpression compileConstantBodyWithMixedConstraints(Decision entitlement, Value bodyValue,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        return compileStreamingBodyDecision(entitlement, bodyValue, obligations, advice, transformation);
    }

    private static boolean isInvalidBooleanBody(Value bodyValue) {
        return !(bodyValue instanceof BooleanValue);
    }

    private static boolean isFalseBody(Value bodyValue) {
        return Value.FALSE.equals(bodyValue);
    }

    private static CompiledExpression tryCompileFullyConstantDecision(Decision entitlement,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (!areAllConstantValues(obligations, advice, transformation)) {
            return null;
        }

        val obligationValues    = extractValues(obligations);
        val adviceValues        = extractValues(advice);
        val transformationValue = extractTransformationValue(transformation);

        if (containsError(obligationValues) || containsError(adviceValues)) {
            return buildIndeterminateDecision();
        }

        return buildDecisionObject(entitlement, obligationValues, adviceValues, transformationValue);
    }

    private static boolean areAllConstantValues(List<CompiledExpression> obligations, List<CompiledExpression> advice,
            CompiledExpression transformation) {
        return allAreValues(obligations) && allAreValues(advice)
                && (transformation == null || transformation instanceof Value);
    }

    private static List<Value> extractValues(List<CompiledExpression> expressions) {
        return expressions.stream().map(Value.class::cast).toList();
    }

    private static Value extractTransformationValue(CompiledExpression transformation) {
        return transformation != null ? (Value) transformation : Value.UNDEFINED;
    }

    private static CompiledExpression compilePureBodyDecision(Decision entitlement, PureExpression bodyPure,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (areAllPureExpressions(obligations, advice, transformation)) {
            return compileFullyPureDecision(entitlement, bodyPure, obligations, advice, transformation);
        }

        return compilePureBodyWithMixedConstraints(entitlement, bodyPure, obligations, advice, transformation);
    }

    private static boolean areAllPureExpressions(List<CompiledExpression> obligations, List<CompiledExpression> advice,
            CompiledExpression transformation) {
        return allArePureExpressions(obligations) && allArePureExpressions(advice)
                && (transformation == null || transformation instanceof PureExpression);
    }

    private static CompiledExpression compileFullyPureDecision(Decision entitlement, PureExpression bodyPure,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val transformationPure = (PureExpression) transformation;
        return new PureExpression(evaluationContext -> evaluatePureDecision(entitlement, bodyPure, transformationPure,
                obligations, advice, evaluationContext), true);
    }

    private static Value evaluatePureDecision(Decision entitlement, PureExpression bodyPure,
            PureExpression transformationPure, List<CompiledExpression> obligations, List<CompiledExpression> advice,
            EvaluationContext evaluationContext) {
        val bodyResult = bodyPure.evaluate(evaluationContext);

        if (isInvalidBooleanBody(bodyResult)) {
            return buildIndeterminateDecision();
        }

        if (isFalseBody(bodyResult)) {
            return buildNotApplicableDecision();
        }

        val obligationValues    = evaluatePureExpressionList(obligations, evaluationContext);
        val adviceValues        = evaluatePureExpressionList(advice, evaluationContext);
        val transformationValue = transformationPure != null ? transformationPure.evaluate(evaluationContext)
                : Value.UNDEFINED;

        if (hasAnyError(obligationValues, adviceValues, transformationValue)) {
            return buildIndeterminateDecision();
        }

        return buildDecisionObject(entitlement, obligationValues, adviceValues, transformationValue);
    }

    private static boolean hasAnyError(List<Value> obligations, List<Value> advice, Value transformation) {
        return containsError(obligations) || containsError(advice) || transformation instanceof ErrorValue;
    }

    private static CompiledExpression compileStreamingBodyDecision(Decision entitlement, CompiledExpression body,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val bodyFlux = ExpressionCompiler.compiledExpressionToFlux(body);
        val stream   = bodyFlux.switchMap(
                bodyResult -> bodyResultToDecisionFlux(entitlement, bodyResult, obligations, advice, transformation));
        return new StreamExpression(stream);
    }

    private static Value buildIndeterminateDecision() {
        return AuthorizationDecisionUtil.INDETERMINATE;
    }

    private static Value buildNotApplicableDecision() {
        return AuthorizationDecisionUtil.NOT_APPLICABLE;
    }

    private static CompiledExpression compilePureBodyWithMixedConstraints(Decision entitlement, PureExpression bodyPure,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (areAllConstantValues(obligations, advice, transformation)) {
            return compilePureBodyWithConstantConstraints(entitlement, bodyPure, obligations, advice, transformation);
        }
        return compilePureBodyWithStreamingConstraints(entitlement, bodyPure, obligations, advice, transformation);
    }

    private static CompiledExpression compilePureBodyWithConstantConstraints(Decision entitlement,
            PureExpression bodyPure, List<CompiledExpression> obligations, List<CompiledExpression> advice,
            CompiledExpression transformation) {
        val obligationValues    = extractValues(obligations);
        val adviceValues        = extractValues(advice);
        val transformationValue = extractTransformationValue(transformation);

        if (containsError(obligationValues) || containsError(adviceValues)
                || transformationValue instanceof ErrorValue) {
            return buildIndeterminateDecision();
        }

        return new PureExpression(ctx -> evaluateBodyAndBuildDecision(entitlement, bodyPure.evaluate(ctx),
                obligationValues, adviceValues, transformationValue), true);
    }

    private static CompiledExpression compilePureBodyWithStreamingConstraints(Decision entitlement,
            PureExpression bodyPure, List<CompiledExpression> obligations, List<CompiledExpression> advice,
            CompiledExpression transformation) {
        val bodyFlux = ExpressionCompiler.compiledExpressionToFlux(bodyPure);
        val stream   = bodyFlux.switchMap(
                bodyResult -> bodyResultToDecisionFlux(entitlement, bodyResult, obligations, advice, transformation));
        return new StreamExpression(stream);
    }

    private static Value evaluateBodyAndBuildDecision(Decision entitlement, Value bodyResult,
            List<Value> obligationValues, List<Value> adviceValues, Value transformationValue) {
        if (isInvalidBooleanBody(bodyResult)) {
            return buildIndeterminateDecision();
        }
        if (isFalseBody(bodyResult)) {
            return buildNotApplicableDecision();
        }
        return buildDecisionObject(entitlement, obligationValues, adviceValues, transformationValue);
    }

    private static Flux<Value> bodyResultToDecisionFlux(Decision entitlement, Value bodyResult,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (isInvalidBooleanBody(bodyResult)) {
            return Flux.just(buildIndeterminateDecision());
        }
        if (isFalseBody(bodyResult)) {
            return Flux.just(buildNotApplicableDecision());
        }
        return evaluateConstraintsAndBuildDecision(entitlement, obligations, advice, transformation);
    }

    private static boolean allAreValues(List<CompiledExpression> expressions) {
        return expressions.stream().allMatch(Value.class::isInstance);
    }

    private static boolean allArePureExpressions(List<CompiledExpression> expressions) {
        return expressions.stream().allMatch(PureExpression.class::isInstance);
    }

    private static List<Value> evaluatePureExpressionList(List<CompiledExpression> expressions, EvaluationContext ctx) {
        val result = new java.util.ArrayList<Value>(expressions.size());
        for (val expr : expressions) {
            if (expr instanceof PureExpression pureExpr) {
                result.add(pureExpr.evaluate(ctx));
            }
        }
        return result;
    }

    private static Flux<Value> evaluateConstraintsAndBuildDecision(Decision entitlement,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val obligationsFlux    = evaluateExpressionListToFlux(obligations);
        val adviceFlux         = evaluateExpressionListToFlux(advice);
        val transformationFlux = transformation != null ? ExpressionCompiler.compiledExpressionToFlux(transformation)
                : Flux.just(Value.UNDEFINED);

        return Flux.combineLatest(obligationsFlux, adviceFlux, transformationFlux,
                values -> assembleDecisionObject(entitlement, values));
    }

    private static Value assembleDecisionObject(Decision entitlement, java.lang.Object[] values) {
        @SuppressWarnings("unchecked")
        val obligations = (List<Value>) values[0];
        @SuppressWarnings("unchecked")
        val advice      = (List<Value>) values[1];
        val resource    = (Value) values[2];

        if (containsError(obligations) || containsError(advice) || resource instanceof ErrorValue) {
            return buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(), Value.UNDEFINED);
        }
        return buildDecisionObject(entitlement, obligations, advice, resource);
    }

    /**
     * Builds a decision object containing the authorization decision and associated
     * constraints.
     *
     * @param decision
     * the authorization decision (PERMIT, DENY, INDETERMINATE, NOT_APPLICABLE)
     * @param obligations
     * obligations that must be fulfilled for the decision to be valid
     * @param advice
     * advice that should be considered but is not mandatory
     * @param resource
     * optional resource transformation result, or UNDEFINED if no transformation
     *
     * @return an ObjectValue with fields: decision, obligations, advice, resource
     */
    static Value buildDecisionObject(Decision decision, List<Value> obligations, List<Value> advice, Value resource) {
        return AuthorizationDecisionUtil.buildDecision(decision, obligations, advice, resource);
    }

    private static Flux<List<Value>> evaluateExpressionListToFlux(List<CompiledExpression> expressions) {
        if (expressions.isEmpty()) {
            return Flux.just(List.of());
        }
        val sources = expressions.stream().map(ExpressionCompiler::compiledExpressionToFlux).toList();
        return Flux.combineLatest(sources, SaplCompiler::assembleValueList);
    }

    private static List<Value> assembleValueList(java.lang.Object[] arguments) {
        val result = new java.util.ArrayList<Value>(arguments.length);
        for (val argument : arguments) {
            if (argument instanceof Value value) {
                result.add(value);
            }
        }
        return result;
    }

    private static boolean containsError(List<Value> values) {
        return values.stream().anyMatch(ErrorValue.class::isInstance);
    }

    /**
     * Validates that a target expression result is a boolean value.
     *
     * @param targetExpression
     * the evaluated target expression result
     *
     * @return the value if boolean, or an error value if not
     */
    public Value booleanValueOrErrorInTarget(EObject astNode, Value targetExpression) {
        if (targetExpression instanceof BooleanValue) {
            return targetExpression;
        }
        return Error.at(astNode, targetExpression.metadata(), ERROR_TARGET_EXPECTED_BOOLEAN, targetExpression);
    }

    /**
     * Wraps a pure target expression to validate its result is boolean at runtime.
     *
     * @param targetExpression
     * the pure expression to wrap
     *
     * @return a pure expression that validates the result is boolean
     */
    public PureExpression compileTargetExpressionToMatchExpression(EObject astNode, PureExpression targetExpression) {
        return new PureExpression(
                evaluationContext -> booleanValueOrErrorInTarget(astNode, targetExpression.evaluate(evaluationContext)),
                targetExpression.isSubscriptionScoped());
    }

    private void assertExpressionSuitableForTarget(EObject targetExpression, CompiledExpression expression) {
        if (expression instanceof StreamExpression) {
            throw new SaplCompilerException(ERROR_POLICY_CONTAINS_ATTRIBUTE_FINDERS.formatted(EXPRESSION_TARGET),
                    targetExpression);
        } else if (expression instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_POLICY_COMPILE_TIME_ERROR.formatted(EXPRESSION_TARGET, error),
                    targetExpression);
        } else if (expression instanceof Value && !(expression instanceof BooleanValue)) {
            throw new SaplCompilerException(ERROR_POLICY_ALWAYS_NON_BOOLEAN.formatted(EXPRESSION_TARGET, expression),
                    targetExpression);
        } else if (expression instanceof BooleanValue booleanValue && booleanValue.equals(Value.FALSE)) {
            throw new SaplCompilerException(ERROR_POLICY_ALWAYS_FALSE.formatted(EXPRESSION_TARGET), targetExpression);
        } else if (expression instanceof PureExpression pureExpression && !pureExpression.isSubscriptionScoped()) {
            throw new SaplCompilerException(ERROR_POLICY_UNRESOLVED_RELATIVE.formatted(EXPRESSION_TARGET),
                    targetExpression);
        }
    }

    /**
     * Converts a SAPL entitlement to a PDP decision.
     *
     * @param entitlement
     * the SAPL entitlement (permit or deny)
     *
     * @return the corresponding Decision enum value
     *
     * @throws IllegalArgumentException
     * if entitlement is neither Permit nor Deny
     */
    public static Decision decisionOf(Entitlement entitlement) {
        return switch (entitlement) {
        case Permit p -> Decision.PERMIT;
        case Deny d   -> Decision.DENY;
        default       -> throw new IllegalArgumentException(ERROR_UNEXPECTED_ENTITLEMENT.formatted(entitlement));
        };
    }

    private CompiledExpression compileBody(PolicyBody body, CompilationContext context) {
        if (body == null) {
            return Value.TRUE;
        }
        val statements = body.getStatements();
        if (statements == null || statements.isEmpty()) {
            return Value.TRUE;
        }
        CompiledExpression compiledBody = null;
        for (val statement : statements) {
            if (statement instanceof Condition condition) {
                if (compiledBody == null) {
                    compiledBody = ExpressionCompiler.compileExpression(condition.getExpression(), context);
                } else {
                    compiledBody = ExpressionCompiler.compileLazyAnd(statement, compiledBody, condition.getExpression(),
                            context);
                }
            } else if (statement instanceof ValueDefinition valueDefinition) {
                val variableName = valueDefinition.getName();
                if (context.containsVariable(variableName)) {
                    throw new SaplCompilerException(ERROR_VARIABLE_ALREADY_EXISTS.formatted(variableName),
                            valueDefinition);
                }
                var compiledValueDefinition = ExpressionCompiler.compileExpression(valueDefinition.getEval(), context);
                if (compiledValueDefinition instanceof StreamExpression streamExpression) {
                    compiledValueDefinition = makeExpressionMulticast(streamExpression);
                }
                context.addLocalPolicyVariable(variableName, compiledValueDefinition);
            }
        }
        if (compiledBody == null) {
            compiledBody = Value.TRUE;
        }

        return compiledBody;
    }

    /**
     * Converts a StreamExpression into a multicast stream that caches the last
     * value and allows multiple subscribers.
     * <p>
     * Uses replay(1).refCount() to create a hot source that:
     * <ul>
     * <li>Caches the last emitted value</li>
     * <li>Shares the cached value with new subscribers immediately</li>
     * <li>Automatically connects when the first subscriber subscribes</li>
     * <li>Automatically disconnects when all subscribers unsubscribe</li>
     * </ul>
     *
     * @param streamExpression
     * the stream expression to multicast
     *
     * @return a new StreamExpression with multicast behavior
     */
    private static StreamExpression makeExpressionMulticast(StreamExpression streamExpression) {
        return new StreamExpression(streamExpression.stream().replay(1).refCount());
    }

    private List<CompiledExpression> compileListOfExpressions(List<Expression> expressions,
            CompilationContext context) {
        return expressions == null ? List.of()
                : expressions.stream().map(expression -> ExpressionCompiler.compileExpression(expression, context))
                        .toList();
    }

    private CompiledPolicy compilePolicySet(PolicySet policySet, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        val name             = policySet.getSaplName();
        val matchExpression  = compileMatchExpression(policySet.getTargetExpression(), schemaCheckingExpression,
                context);
        val valueDefinitions = policySet.getValueDefinitions();
        for (val valueDefinition : valueDefinitions) {
            val variableName            = valueDefinition.getName();
            val compiledValueDefinition = ExpressionCompiler.compileExpression(valueDefinition.getEval(), context);
            if (variableName == null || variableName.isBlank()) {
                throw new SaplCompilerException(ERROR_VARIABLE_NAME_MISSING, valueDefinition);
            }
            if (compiledValueDefinition instanceof ErrorValue error) {
                throw new SaplCompilerException(ERROR_POLICY_COMPILE_TIME_ERROR
                        .formatted(EXPRESSION_VARIABLE_DEFINITION.formatted(variableName), error), valueDefinition);
            }
            if (!context.addGlobalPolicySetVariable(variableName, compiledValueDefinition)) {
                throw new SaplCompilerException(ERROR_VARIABLE_ALREADY_DEFINED.formatted(variableName),
                        valueDefinition);
            }
        }
        val combiningAlgorithm = policySet.getAlgorithm();
        val policies           = policySet.getPolicies();
        val decisionExpression = compilePolicySetPolicies(combiningAlgorithm, policies, context);
        return new CompiledPolicy(name, matchExpression, decisionExpression);
    }

    private static CompiledExpression compilePolicySetPolicies(CombiningAlgorithm combiningAlgorithm,
            List<Policy> policies, CompilationContext context) {
        val compiledPolicies = policies.stream().map(p -> compilePolicy(p, Value.TRUE, context)).toList();
        return switch (combiningAlgorithm) {
        case CombiningAlgorithm.DENY_OVERRIDES      -> CombiningAlgorithmCompiler.denyOverrides(compiledPolicies);
        case CombiningAlgorithm.DENY_UNLESS_PERMIT  -> CombiningAlgorithmCompiler.denyUnlessPermit(compiledPolicies);
        case CombiningAlgorithm.ONLY_ONE_APPLICABLE -> CombiningAlgorithmCompiler.onlyOneApplicable(compiledPolicies);
        case CombiningAlgorithm.FIRST_APPLICABLE    -> CombiningAlgorithmCompiler.firstApplicable(compiledPolicies);
        case CombiningAlgorithm.PERMIT_UNLESS_DENY  -> CombiningAlgorithmCompiler.permitUnlessDeny(compiledPolicies);
        case CombiningAlgorithm.PERMIT_OVERRIDES    -> CombiningAlgorithmCompiler.permitOverrides(compiledPolicies);
        };
    }

    private CompiledExpression compileSchemas(EList<Schema> schemas, CompilationContext context) {
        if (schemas == null || schemas.isEmpty()) {
            return Value.TRUE;
        }
        PureExpression schemaValidationExpression = null;
        for (val schema : schemas) {
            if (!schema.isEnforced()) {
                // Only actually verify enforced schemata. THe others are just sugar for the
                // editor helping with code completion hints.
                continue;
            }
            val schemaValidation = compileSchema(schema, context);
            if (schemaValidationExpression == null) {
                schemaValidationExpression = schemaValidation;
            } else {
                val finalSchemaValidationExpression = schemaValidationExpression;
                schemaValidationExpression = new PureExpression(ctx -> BooleanOperators.and(schema,
                        finalSchemaValidationExpression.evaluate(ctx), schemaValidation.evaluate(ctx)), true);
            }
        }
        return schemaValidationExpression == null ? Value.TRUE : schemaValidationExpression;
    }

    private PureExpression compileSchema(Schema schema, CompilationContext context) {
        val schemaValue = ExpressionCompiler.compileExpression(schema.getSchemaExpression(), context);
        if (!(schemaValue instanceof ObjectValue schemaObjectValue)) {
            throw new SaplCompilerException(ERROR_SCHEMA_NOT_OBJECT_VALUE.formatted(schemaValue), schema);
        }
        val subscriptionElement = schema.getSubscriptionElement();
        if (!ReservedIdentifiers.SUBSCRIPTION_IDENTIFIERS.contains(subscriptionElement)) {
            throw new SaplCompilerException(ERROR_SCHEMA_INVALID_SUBSCRIPTION_ELEMENT.formatted(subscriptionElement),
                    schema);
        }

        return new PureExpression(ctx -> {
            val externalSchemas = ctx.get(ENV_SCHEMAS);
            if (!(externalSchemas instanceof ArrayValue)) {
                return Value.TRUE;
            }
            val subscriptionElementValue = ctx.get(subscriptionElement);
            return SchemaValidationLibrary.isCompliantWithExternalSchemas(subscriptionElementValue, schemaObjectValue,
                    externalSchemas);
        }, true);
    }

}
