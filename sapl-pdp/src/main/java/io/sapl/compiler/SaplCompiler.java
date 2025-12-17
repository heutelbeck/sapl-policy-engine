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
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.ConditionHit;
import io.sapl.compiler.operators.BooleanOperators;
import io.sapl.functions.libraries.SchemaValidationLibrary;
import io.sapl.grammar.antlr.SAPLParser.*;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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

    private static final String EXPRESSION_TARGET              = "target";
    private static final String EXPRESSION_VARIABLE_DEFINITION = "variable definition '%s'";

    private static final String COMPILE_ERROR_BODY_NON_BOOLEAN            = "Compilation failed. Policy body always evaluates to non-Boolean value: %s.";
    private static final String COMPILE_ERROR_CONSTRAINT_ALWAYS_ERROR     = "Compilation failed. Constraint always evaluates to error: %s.";
    private static final String COMPILE_ERROR_EXPRESSION_ALWAYS_ERROR     = "Compilation failed. Expression in %s always evaluates to error: %s.";
    private static final String COMPILE_ERROR_EXPRESSION_ALWAYS_FALSE     = "Compilation failed. Expression in %s always evaluates to false. Policy will never be applicable.";
    private static final String COMPILE_ERROR_EXPRESSION_NON_BOOLEAN      = "Compilation failed. Expression in %s always evaluates to non-Boolean value: %s.";
    private static final String COMPILE_ERROR_EXPRESSION_STREAMING        = "Compilation failed. Expression in %s must not contain attribute finders (<>).";
    private static final String COMPILE_ERROR_EXPRESSION_UNRESOLVED       = "Compilation failed. Expression in %s contains unresolved relative reference (@).";
    private static final String COMPILE_ERROR_PARSE_FAILED                = "Compilation failed. Unable to parse document: %s.";
    private static final String COMPILE_ERROR_SCHEMA_INVALID_ELEMENT      = "Compilation failed. Schema must reference subject, action, resource, or environment, but was: %s.";
    private static final String COMPILE_ERROR_SCHEMA_NOT_OBJECT           = "Compilation failed. Schema must evaluate to ObjectValue, but was: %s.";
    private static final String COMPILE_ERROR_TRANSFORMATION_ALWAYS_ERROR = "Compilation failed. Transformation always evaluates to error: %s.";
    private static final String COMPILE_ERROR_UNEXPECTED_ENTITLEMENT      = "Compilation failed. Unexpected entitlement: %s.";
    private static final String COMPILE_ERROR_UNEXPECTED_POLICY_ELEMENT   = "Compilation failed. Unexpected policy element: %s.";
    private static final String COMPILE_ERROR_UNEXPECTED_TARGET_TYPE      = "Compilation failed. Unexpected target expression type: %s.";
    private static final String COMPILE_ERROR_VARIABLE_ALREADY_DEFINED    = "Compilation failed. Variable '%s' already defined.";
    private static final String COMPILE_ERROR_VARIABLE_NAME_MISSING       = "Compilation failed. Variable name is missing.";

    private static final String RUNTIME_ERROR_TARGET_NON_BOOLEAN = "Target expression must return Boolean, but found: %s.";

    private static final SAPLParser PARSER = new DefaultSAPLParser();

    public CompiledPolicy compile(String source, CompilationContext context) {
        val document = PARSER.parseDocument(source);
        if (document.isInvalid()) {
            throw new SaplCompilerException(COMPILE_ERROR_PARSE_FAILED.formatted(document.errorMessage()));
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
    public CompiledPolicy compileDocument(SaplContext document, CompilationContext context) {
        context.resetForNextDocument();
        addAllImports(document.importStatement(), context);
        val schemaCheckingExpression = compileSchemas(document.schemaStatement(), context);
        val policyElement            = document.policyElement();
        return switch (policyElement) {
        case PolicyOnlyElementContext p -> compilePolicy(p.policy(), schemaCheckingExpression, context);
        case PolicySetElementContext ps -> compilePolicySet(ps.policySet(), schemaCheckingExpression, context);
        case null, default              -> throw new SaplCompilerException(
                COMPILE_ERROR_UNEXPECTED_POLICY_ELEMENT.formatted(policyElement), policyElement);
        };
    }

    private void addAllImports(List<ImportStatementContext> imports, CompilationContext context) {
        if (imports == null) {
            return;
        }
        for (val importStmt : imports) {
            val fullyQualifiedName = buildFullyQualifiedName(importStmt);
            val alias              = importStmt.functionAlias != null
                    ? ExpressionCompiler.getIdentifierName(importStmt.functionAlias)
                    : null;
            context.addImport(fullyQualifiedName, alias);
        }
    }

    private String buildFullyQualifiedName(ImportStatementContext importStmt) {
        val builder = new StringBuilder();
        for (val step : importStmt.libSteps) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(ExpressionCompiler.getIdentifierName(step));
        }
        builder.append('.').append(ExpressionCompiler.getIdentifierName(importStmt.functionName));
        return builder.toString();
    }

    private CompiledPolicy compilePolicy(PolicyContext policy, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        context.resetForNextPolicy();
        val name               = unquoteString(policy.saplName.getText());
        val entitlement        = decisionOf(policy.entitlement()).name();
        val matchExpression    = compileMatchExpression(policy.targetExpression, schemaCheckingExpression, policy,
                context);
        val targetLocation     = context.isCoverageEnabled() && policy.targetExpression != null
                ? SourceLocationUtil.fromContext(policy.targetExpression)
                : null;
        val decisionExpression = compileDecisionExpression(policy, context, targetLocation);
        return new CompiledPolicy(name, entitlement, matchExpression, decisionExpression, targetLocation);
    }

    private CompiledExpression compileMatchExpression(ExpressionContext targetExpression,
            CompiledExpression schemaCheckingExpression, ParserRuleContext astNode, CompilationContext context) {
        val compiledTargetExpression = compileLazyAnd(targetExpression, schemaCheckingExpression, astNode, context);
        assertExpressionSuitableForTarget(astNode, compiledTargetExpression);
        return switch (compiledTargetExpression) {
        case PureExpression pureExpression -> compileTargetExpressionToMatchExpression(astNode, pureExpression);
        case Value value                   -> ensureBooleanValueInTarget(astNode, value);
        default                            -> throw new SaplCompilerException(
                COMPILE_ERROR_UNEXPECTED_TARGET_TYPE.formatted(compiledTargetExpression.getClass()), astNode);
        };
    }

    /**
     * Compiles a lazy AND of schema checking with target expression.
     * Schema checking is evaluated first (left operand) to validate inputs before
     * evaluating the target expression (right operand). This matches the Xtext
     * semantics where schema validation happens before target evaluation.
     */
    static CompiledExpression compileLazyAnd(ExpressionContext targetExpression,
            CompiledExpression schemaCheckingExpression, ParserRuleContext astNode, CompilationContext context) {
        val compiledTarget = ExpressionCompiler.compileExpression(targetExpression, context);
        if (compiledTarget == null) {
            return schemaCheckingExpression;
        }
        if (schemaCheckingExpression instanceof Value schemaValue && Value.TRUE.equals(schemaValue)) {
            return compiledTarget;
        }
        // Combine schema check (left, evaluated first) with target (right, evaluated if
        // schema is true)
        // This ensures schema validation happens before target evaluation, matching
        // Xtext semantics
        return combineLazyAnd(astNode, schemaCheckingExpression, compiledTarget);
    }

    private Value ensureBooleanValueInTarget(ParserRuleContext astNode, Value constantTargetEvaluationResult) {
        if (constantTargetEvaluationResult instanceof BooleanValue) {
            return constantTargetEvaluationResult;
        }
        throw new SaplCompilerException(
                COMPILE_ERROR_EXPRESSION_NON_BOOLEAN.formatted(EXPRESSION_TARGET, constantTargetEvaluationResult),
                astNode);
    }

    private CompiledExpression compileDecisionExpression(PolicyContext policy, CompilationContext context,
            SourceLocation targetLocation) {
        val name             = unquoteString(policy.saplName.getText());
        val entitlement      = decisionOf(policy.entitlement());
        val coverageRecorder = context.isCoverageEnabled() ? new CoverageRecorder() : null;
        val decisionExpr     = compileDecisionExpressionInternal(policy, entitlement, context, coverageRecorder);
        return wrapWithTrace(name, entitlement.name(), decisionExpr, coverageRecorder, targetLocation);
    }

    private CompiledExpression compileDecisionExpressionInternal(PolicyContext policy, Decision entitlement,
            CompilationContext context, CoverageRecorder coverageRecorder) {
        val body           = compileBody(policy.policyBody(), context, coverageRecorder);
        val obligations    = compileListOfConstraints(policy.obligations, context);
        val advice         = compileListOfConstraints(policy.adviceExpressions, context);
        val transformation = compileTransformation(policy.transformation, context);

        if (body instanceof Value bodyValue) {
            return compileConstantBodyDecision(entitlement, bodyValue, obligations, advice, transformation);
        }

        if (body instanceof PureExpression bodyPure) {
            return compilePureBodyDecision(entitlement, bodyPure, obligations, advice, transformation);
        }

        return compileStreamingDecision(entitlement, (StreamExpression) body, obligations, advice, transformation);
    }

    private static CompiledExpression wrapWithTrace(String name, String entitlement, CompiledExpression decisionExpr,
            CoverageRecorder coverageRecorder, SourceLocation targetLocation) {
        if (decisionExpr instanceof Value decisionValue) {
            return buildTracedPolicyDecision(name, entitlement, decisionValue, null, null);
        }

        if (decisionExpr instanceof PureExpression pureExpr) {
            return new PureExpression(ctx -> {
                val result = pureExpr.evaluate(ctx);
                val hits   = coverageRecorder != null ? coverageRecorder.collectAndClear() : null;
                return buildTracedPolicyDecision(name, entitlement, result, hits, targetLocation);
            }, pureExpr.isSubscriptionScoped());
        }

        if (decisionExpr instanceof StreamExpression(Flux<Value> stream)) {
            // Note: For streaming expressions with coverage, hits are collected per
            // emission.
            // The recorder is cleared after each emission to capture fresh hits.
            if (coverageRecorder != null) {
                return new StreamExpression(stream.map(decisionValue -> {
                    val hits = coverageRecorder.collectAndClear();
                    return buildTracedPolicyDecision(name, entitlement, decisionValue, hits, targetLocation);
                }));
            }
            return new StreamExpression(stream
                    .map(decisionValue -> buildTracedPolicyDecision(name, entitlement, decisionValue, null, null)));
        }

        throw new IllegalStateException("Unexpected expression type: " + decisionExpr.getClass());
    }

    private static Value buildTracedPolicyDecision(String name, String entitlement, Value decisionValue,
            List<ConditionHit> conditionHits, SourceLocation targetLocation) {
        val builder = TracedPolicyDecision.builder().name(name).entitlement(entitlement)
                .fromDecisionValue(decisionValue)
                .attributes(TracedPolicyDecision.convertAttributeRecords(decisionValue.metadata().attributeTrace()))
                .errors(TracedPolicyDecision.extractErrors(decisionValue));

        // Add coverage data if present (coverage enabled when conditionHits or
        // targetLocation is non-null)
        if (conditionHits != null || targetLocation != null) {
            if (conditionHits != null) {
                builder.conditions(conditionHits);
            }
            builder.targetResult(true); // Target matched (otherwise we wouldn't be here)
            builder.targetLocation(targetLocation);
        }

        return builder.build();
    }

    private CompiledExpression compileTransformation(ExpressionContext transformationExpression,
            CompilationContext context) {
        val compiledExpression = ExpressionCompiler.compileExpression(transformationExpression, context);
        if (compiledExpression instanceof ErrorValue) {
            throw new SaplCompilerException(COMPILE_ERROR_TRANSFORMATION_ALWAYS_ERROR.formatted(compiledExpression),
                    transformationExpression);
        }
        return compiledExpression;
    }

    private static CompiledExpression compileConstantBodyDecision(Decision entitlement, Value bodyValue,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (isInvalidBooleanBody(bodyValue)) {
            return buildIndeterminateDecision(bodyValue);
        }

        if (isFalseBody(bodyValue)) {
            return buildNotApplicableDecision();
        }

        return switch (determineConstraintsNature(obligations, advice, transformation)) {
        case VALUE  -> compileFullyConstantDecision(entitlement, obligations, advice, transformation);
        case PURE   -> compilePureConstraintsDecision(entitlement, bodyValue, obligations, advice, transformation);
        case STREAM -> compileStreamingDecision(entitlement, new StreamExpression(Flux.just(bodyValue)), obligations,
                advice, transformation);
        };
    }

    private static CompiledExpression compilePureConstraintsDecision(Decision entitlement, Value bodyValue,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        return new PureExpression(ctx -> {
            val obligationValues    = evaluateConstraintList(obligations, ctx);
            val adviceValues        = evaluateConstraintList(advice, ctx);
            val transformationValue = evaluateConstraint(transformation, ctx);
            return buildDecisionWithMetadata(entitlement, bodyValue, obligationValues, adviceValues,
                    transformationValue);
        }, true);
    }

    private static List<Value> evaluateConstraintList(List<CompiledExpression> constraints, EvaluationContext ctx) {
        val result = new ArrayList<Value>(constraints.size());
        for (val constraint : constraints) {
            result.add(evaluateConstraint(constraint, ctx));
        }
        return result;
    }

    private static Value evaluateConstraint(CompiledExpression constraint, EvaluationContext ctx) {
        if (constraint == null) {
            return Value.UNDEFINED;
        }
        if (constraint instanceof Value value) {
            return value;
        }
        if (constraint instanceof PureExpression pureExpr) {
            return pureExpr.evaluate(ctx);
        }
        throw new IllegalStateException("Unexpected constraint type in pure evaluation: " + constraint.getClass());
    }

    private static boolean isInvalidBooleanBody(Value bodyValue) {
        return !(bodyValue instanceof BooleanValue);
    }

    private static boolean isFalseBody(Value bodyValue) {
        return Value.FALSE.equals(bodyValue);
    }

    private static CompiledExpression compileFullyConstantDecision(Decision entitlement,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val obligationValues    = extractValues(obligations);
        val adviceValues        = extractValues(advice);
        val transformationValue = extractTransformationValue(transformation);

        if (containsError(obligationValues) || containsError(adviceValues)) {
            return buildIndeterminateDecision(collectErrors(obligationValues, adviceValues, transformationValue));
        }

        return buildDecisionObject(entitlement, obligationValues, adviceValues, transformationValue);
    }

    private static List<Value> extractValues(List<CompiledExpression> expressions) {
        return expressions.stream().map(Value.class::cast).toList();
    }

    private static Value extractTransformationValue(CompiledExpression transformation) {
        return transformation != null ? (Value) transformation : Value.UNDEFINED;
    }

    private static CompiledExpression compilePureBodyDecision(Decision entitlement, PureExpression bodyPure,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        return switch (determineConstraintsNature(obligations, advice, transformation)) {
        case VALUE, PURE ->
            compilePureBodyWithPureConstraints(entitlement, bodyPure, obligations, advice, transformation);
        case STREAM      -> compileStreamingDecision(entitlement, new StreamExpression(bodyPure.flux()), obligations,
                advice, transformation);
        };
    }

    private static CompiledExpression compilePureBodyWithPureConstraints(Decision entitlement, PureExpression bodyPure,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        return new PureExpression(ctx -> {
            val bodyResult = bodyPure.evaluate(ctx);

            if (isInvalidBooleanBody(bodyResult)) {
                return buildIndeterminateDecisionWithMetadata(bodyResult);
            }

            if (isFalseBody(bodyResult)) {
                return buildNotApplicableDecisionWithMetadata(bodyResult);
            }

            val obligationValues    = evaluateConstraintList(obligations, ctx);
            val adviceValues        = evaluateConstraintList(advice, ctx);
            val transformationValue = evaluateConstraint(transformation, ctx);

            return buildDecisionWithMetadata(entitlement, bodyResult, obligationValues, adviceValues,
                    transformationValue);
        }, true);
    }

    private static List<Value> collectErrors(List<Value> obligations, List<Value> advice, Value transformation) {
        val errors = new ArrayList<Value>();
        obligations.stream().filter(ErrorValue.class::isInstance).forEach(errors::add);
        advice.stream().filter(ErrorValue.class::isInstance).forEach(errors::add);
        if (transformation instanceof ErrorValue) {
            errors.add(transformation);
        }
        return errors;
    }

    private static CompiledExpression compileStreamingDecision(Decision entitlement, StreamExpression body,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val stream = body.stream().switchMap(
                bodyResult -> bodyResultToDecisionFlux(entitlement, bodyResult, obligations, advice, transformation));
        return new StreamExpression(stream);
    }

    private static Value buildIndeterminateDecision(Value error) {
        if (error instanceof ErrorValue) {
            return AuthorizationDecisionUtil.buildIndeterminate(error);
        }
        return AuthorizationDecisionUtil.INDETERMINATE;
    }

    private static Value buildIndeterminateDecision(List<Value> errors) {
        if (errors.isEmpty()) {
            return AuthorizationDecisionUtil.INDETERMINATE;
        }
        return AuthorizationDecisionUtil.buildIndeterminate(errors);
    }

    private static Value buildNotApplicableDecision() {
        return AuthorizationDecisionUtil.NOT_APPLICABLE;
    }

    private static Value buildIndeterminateDecisionWithMetadata(Value bodyResult) {
        val base = bodyResult instanceof ErrorValue ? AuthorizationDecisionUtil.buildIndeterminate(bodyResult)
                : AuthorizationDecisionUtil.INDETERMINATE;
        return base.withMetadata(bodyResult.metadata());
    }

    private static Value buildDecisionWithMetadata(Decision entitlement, Value bodyResult, List<Value> obligations,
            List<Value> advice, Value resource) {
        val mergedMetadata = mergeAllMetadata(bodyResult, obligations, advice, resource);
        val errors         = collectErrors(obligations, advice, resource);
        if (!errors.isEmpty()) {
            return AuthorizationDecisionUtil.buildIndeterminate(errors).withMetadata(mergedMetadata);
        }
        return AuthorizationDecisionUtil.buildDecision(entitlement, obligations, advice, resource, List.of())
                .withMetadata(mergedMetadata);
    }

    private static Value buildNotApplicableDecisionWithMetadata(Value bodyResult) {
        return AuthorizationDecisionUtil.NOT_APPLICABLE.withMetadata(bodyResult.metadata());
    }

    private static Flux<Value> bodyResultToDecisionFlux(Decision entitlement, Value bodyResult,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        if (isInvalidBooleanBody(bodyResult)) {
            return Flux.just(buildIndeterminateDecisionWithMetadata(bodyResult));
        }
        if (isFalseBody(bodyResult)) {
            return Flux.just(buildNotApplicableDecisionWithMetadata(bodyResult));
        }
        return evaluateConstraintsAndBuildDecision(entitlement, bodyResult, obligations, advice, transformation);
    }

    /**
     * Determines the combined nature of a list of expressions. Returns STREAM if
     * any expression is a StreamExpression,
     * PURE if any is a PureExpression (and none are streams), VALUE if all are
     * Values (constants).
     */
    private static Nature determineNature(List<CompiledExpression> expressions) {
        var result = Nature.VALUE;
        for (val expr : expressions) {
            if (expr instanceof StreamExpression) {
                return Nature.STREAM;
            }
            if (expr instanceof PureExpression) {
                result = Nature.PURE;
            }
        }
        return result;
    }

    /**
     * Determines the combined nature of all constraints (obligations, advice,
     * transformation). The result is the
     * "highest" nature: STREAM > PURE > VALUE.
     */
    private static Nature determineConstraintsNature(List<CompiledExpression> obligations,
            List<CompiledExpression> advice, CompiledExpression transformation) {
        val obligationsNature = determineNature(obligations);
        if (obligationsNature == Nature.STREAM) {
            return Nature.STREAM;
        }

        val adviceNature = determineNature(advice);
        if (adviceNature == Nature.STREAM) {
            return Nature.STREAM;
        }

        if (transformation instanceof StreamExpression) {
            return Nature.STREAM;
        }

        if (obligationsNature == Nature.PURE || adviceNature == Nature.PURE
                || transformation instanceof PureExpression) {
            return Nature.PURE;
        }

        return Nature.VALUE;
    }

    private static Flux<Value> evaluateConstraintsAndBuildDecision(Decision entitlement, Value bodyResult,
            List<CompiledExpression> obligations, List<CompiledExpression> advice, CompiledExpression transformation) {
        val obligationsFlux    = evaluateExpressionListToFlux(obligations);
        val adviceFlux         = evaluateExpressionListToFlux(advice);
        val transformationFlux = transformation != null ? ExpressionCompiler.compiledExpressionToFlux(transformation)
                : Flux.just(Value.UNDEFINED);

        return Flux.combineLatest(obligationsFlux, adviceFlux, transformationFlux,
                values -> assembleDecisionObject(entitlement, bodyResult, values));
    }

    private static Value assembleDecisionObject(Decision entitlement, Value bodyResult, Object[] values) {
        @SuppressWarnings("unchecked")
        val obligations = (List<Value>) values[0];
        @SuppressWarnings("unchecked")
        val advice      = (List<Value>) values[1];
        val resource    = (Value) values[2];

        return buildDecisionWithMetadata(entitlement, bodyResult, obligations, advice, resource);
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
     * @return an ObjectValue with fields: decision, obligations, advice, resource,
     * errors
     */
    static Value buildDecisionObject(Decision decision, List<Value> obligations, List<Value> advice, Value resource) {
        return AuthorizationDecisionUtil.buildDecision(decision, obligations, advice, resource, List.of());
    }

    private static ValueMetadata mergeAllMetadata(Value bodyResult, List<Value> obligations, List<Value> advice,
            Value resource) {
        var result = bodyResult != null ? bodyResult.metadata() : ValueMetadata.EMPTY;
        result = result.merge(ValueMetadata.merge(obligations));
        result = result.merge(ValueMetadata.merge(advice));
        if (resource != null && !Value.UNDEFINED.equals(resource)) {
            result = result.merge(resource.metadata());
        }
        return result;
    }

    private static Flux<List<Value>> evaluateExpressionListToFlux(List<CompiledExpression> expressions) {
        if (expressions.isEmpty()) {
            return Flux.just(List.of());
        }
        val sources = expressions.stream().map(ExpressionCompiler::compiledExpressionToFlux).toList();
        return Flux.combineLatest(sources, SaplCompiler::assembleValueList);
    }

    private static List<Value> assembleValueList(Object[] arguments) {
        val result = new ArrayList<Value>(arguments.length);
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
    public Value booleanValueOrErrorInTarget(ParserRuleContext astNode, Value targetExpression) {
        if (targetExpression instanceof ErrorValue || targetExpression instanceof BooleanValue) {
            return targetExpression;
        }
        return Error.at(astNode, targetExpression.metadata(), RUNTIME_ERROR_TARGET_NON_BOOLEAN, targetExpression);
    }

    /**
     * Wraps a pure target expression to validate its result is boolean at runtime.
     *
     * @param targetExpression
     * the pure expression to wrap
     *
     * @return a pure expression that validates the result is boolean
     */
    public PureExpression compileTargetExpressionToMatchExpression(ParserRuleContext astNode,
            PureExpression targetExpression) {
        return new PureExpression(
                evaluationContext -> booleanValueOrErrorInTarget(astNode, targetExpression.evaluate(evaluationContext)),
                targetExpression.isSubscriptionScoped());
    }

    private void assertExpressionSuitableForTarget(ParserRuleContext targetExpression, CompiledExpression expression) {
        if (expression instanceof StreamExpression) {
            throw new SaplCompilerException(COMPILE_ERROR_EXPRESSION_STREAMING.formatted(EXPRESSION_TARGET),
                    targetExpression);
        } else if (expression instanceof ErrorValue error) {
            throw new SaplCompilerException(COMPILE_ERROR_EXPRESSION_ALWAYS_ERROR.formatted(EXPRESSION_TARGET, error),
                    targetExpression);
        } else if (expression instanceof Value && !(expression instanceof BooleanValue)) {
            throw new SaplCompilerException(
                    COMPILE_ERROR_EXPRESSION_NON_BOOLEAN.formatted(EXPRESSION_TARGET, expression), targetExpression);
        } else if (expression instanceof BooleanValue booleanValue && booleanValue.equals(Value.FALSE)) {
            throw new SaplCompilerException(COMPILE_ERROR_EXPRESSION_ALWAYS_FALSE.formatted(EXPRESSION_TARGET),
                    targetExpression);
        } else if (expression instanceof PureExpression pureExpression && !pureExpression.isSubscriptionScoped()) {
            throw new SaplCompilerException(COMPILE_ERROR_EXPRESSION_UNRESOLVED.formatted(EXPRESSION_TARGET),
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
    public static Decision decisionOf(EntitlementContext entitlement) {
        return switch (entitlement) {
        case PermitEntitlementContext ignored -> Decision.PERMIT;
        case DenyEntitlementContext ignored   -> Decision.DENY;
        default                               ->
            throw new IllegalArgumentException(COMPILE_ERROR_UNEXPECTED_ENTITLEMENT.formatted(entitlement));
        };
    }

    private CompiledExpression compileBody(PolicyBodyContext body, CompilationContext context,
            CoverageRecorder coverageRecorder) {
        if (body == null) {
            return Value.TRUE;
        }
        val statements = body.statements;
        if (statements == null || statements.isEmpty()) {
            return Value.TRUE;
        }
        CompiledExpression compiledBody   = null;
        int                conditionIndex = 0;
        for (val statement : statements) {
            if (statement instanceof ConditionStatementContext conditionStmt) {
                var conditionExpr = ExpressionCompiler.compileExpression(conditionStmt.expression(), context);

                // Wrap with coverage recording only when recorder is present
                if (coverageRecorder != null) {
                    val statementId = conditionIndex;
                    val location    = SourceLocationUtil.fromContext(conditionStmt);
                    conditionExpr = wrapWithCoverageRecording(conditionExpr, statementId, location, coverageRecorder);
                }
                conditionIndex++;

                if (compiledBody == null) {
                    compiledBody = conditionExpr;
                } else {
                    compiledBody = combineLazyAnd(statement, compiledBody, conditionExpr);
                }
            } else if (statement instanceof ValueDefinitionStatementContext valueDefStmt) {
                val valueDefinition = valueDefStmt.valueDefinition();
                val variableName    = valueDefinition.name.getText();
                if (context.containsVariable(variableName)) {
                    throw new SaplCompilerException(COMPILE_ERROR_VARIABLE_ALREADY_DEFINED.formatted(variableName),
                            valueDefinition);
                }
                var compiledValueDefinition = ExpressionCompiler.compileExpression(valueDefinition.eval, context);
                if (compiledValueDefinition instanceof StreamExpression streamExpression) {
                    compiledValueDefinition = makeExpressionMulticast(streamExpression);
                }
                context.addLocalPolicyVariable(variableName, compiledValueDefinition);
            }
        }

        if (compiledBody == null) {
            return Value.TRUE;
        }

        if (compiledBody instanceof Value && !(compiledBody instanceof BooleanValue)) {
            throw new SaplCompilerException(COMPILE_ERROR_BODY_NON_BOOLEAN.formatted(compiledBody), body);
        }
        return compiledBody;
    }

    private static int getLineNumber(ParserRuleContext astNode) {
        val location = SourceLocationUtil.fromContext(astNode);
        return location != null ? location.line() : 0;
    }

    private static CompiledExpression wrapWithCoverageRecording(CompiledExpression expression, int statementId,
            SourceLocation location, CoverageRecorder recorder) {
        if (expression instanceof Value value) {
            // For constant boolean values, we still need to record the hit at evaluation
            // time
            return new PureExpression(ctx -> {
                if (value instanceof BooleanValue bv) {
                    recorder.recordHit(statementId, bv.value(), location);
                }
                return value;
            }, true);
        }

        if (expression instanceof PureExpression pureExpr) {
            return new PureExpression(ctx -> {
                val result = pureExpr.evaluate(ctx);
                if (result instanceof BooleanValue bv) {
                    recorder.recordHit(statementId, bv.value(), location);
                }
                return result;
            }, pureExpr.isSubscriptionScoped());
        }

        if (expression instanceof StreamExpression streamExpr) {
            // For streaming expressions, record hits when each value is emitted
            return new StreamExpression(streamExpr.stream().doOnNext(result -> {
                if (result instanceof BooleanValue bv) {
                    recorder.recordHit(statementId, bv.value(), location);
                }
            }));
        }

        return expression;
    }

    private static CompiledExpression combineLazyAnd(ParserRuleContext astNode, CompiledExpression left,
            CompiledExpression right) {
        // Simplified version of lazy AND for pre-compiled expressions
        if (left instanceof BooleanValue bv) {
            return bv.value() ? right : Value.FALSE;
        }

        if (left instanceof PureExpression leftPure) {
            if (right instanceof Value rightValue) {
                return new PureExpression(ctx -> {
                    val leftResult = leftPure.evaluate(ctx);
                    if (leftResult instanceof BooleanValue boolVal && boolVal.value()) {
                        return rightValue;
                    }
                    return leftResult instanceof BooleanValue boolVal && !boolVal.value() ? Value.FALSE : leftResult;
                }, leftPure.isSubscriptionScoped());
            }
            if (right instanceof PureExpression rightPure) {
                return new PureExpression(ctx -> {
                    val leftResult = leftPure.evaluate(ctx);
                    if (leftResult instanceof BooleanValue boolVal && boolVal.value()) {
                        return rightPure.evaluate(ctx);
                    }
                    return leftResult instanceof BooleanValue boolVal && !boolVal.value() ? Value.FALSE : leftResult;
                }, leftPure.isSubscriptionScoped() && rightPure.isSubscriptionScoped());
            }
        }

        // Fall back to streaming for complex cases
        val leftFlux  = ExpressionCompiler.compiledExpressionToFlux(left);
        val rightFlux = ExpressionCompiler.compiledExpressionToFlux(right);
        return new StreamExpression(leftFlux.switchMap(leftVal -> {
            if (leftVal instanceof BooleanValue bv && bv.value()) {
                return rightFlux;
            }
            return Flux.just(leftVal instanceof BooleanValue bv && !bv.value() ? Value.FALSE : leftVal);
        }));
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

    private List<CompiledExpression> compileListOfConstraints(List<ExpressionContext> constraintExpressions,
            CompilationContext context) {
        if (constraintExpressions == null) {
            return List.of();
        }
        val compiledConstraints = new ArrayList<CompiledExpression>(constraintExpressions.size());
        for (val constraint : constraintExpressions) {
            val compiledConstraint = ExpressionCompiler.compileExpression(constraint, context);
            if (compiledConstraint instanceof ErrorValue) {
                throw new SaplCompilerException(COMPILE_ERROR_CONSTRAINT_ALWAYS_ERROR.formatted(compiledConstraint),
                        constraint);
            }
            compiledConstraints.add(compiledConstraint);
        }
        return compiledConstraints;
    }

    private CompiledPolicy compilePolicySet(PolicySetContext policySet, CompiledExpression schemaCheckingExpression,
            CompilationContext context) {
        val name             = unquoteString(policySet.saplName.getText());
        val matchExpression  = compileMatchExpression(policySet.targetExpression, schemaCheckingExpression, policySet,
                context);
        val valueDefinitions = policySet.valueDefinition();
        for (val valueDefinition : valueDefinitions) {
            val variableName            = valueDefinition.name.getText();
            val compiledValueDefinition = ExpressionCompiler.compileExpression(valueDefinition.eval, context);
            if (variableName == null || variableName.isBlank()) {
                throw new SaplCompilerException(COMPILE_ERROR_VARIABLE_NAME_MISSING, valueDefinition);
            }
            if (compiledValueDefinition instanceof ErrorValue error) {
                throw new SaplCompilerException(COMPILE_ERROR_EXPRESSION_ALWAYS_ERROR
                        .formatted(EXPRESSION_VARIABLE_DEFINITION.formatted(variableName), error), valueDefinition);
            }
            if (!context.addGlobalPolicySetVariable(variableName, compiledValueDefinition)) {
                throw new SaplCompilerException(COMPILE_ERROR_VARIABLE_ALREADY_DEFINED.formatted(variableName),
                        valueDefinition);
            }
        }
        val combiningAlgorithm = policySet.combiningAlgorithm();
        val policies           = policySet.policy();
        val targetLocation     = context.isCoverageEnabled() && policySet.targetExpression != null
                ? SourceLocationUtil.fromContext(policySet.targetExpression)
                : null;
        val decisionExpression = compilePolicySetPolicies(name, combiningAlgorithm, policies, context);
        return new CompiledPolicy(name, null, matchExpression, decisionExpression, targetLocation);
    }

    private static CompiledExpression compilePolicySetPolicies(String setName,
            CombiningAlgorithmContext combiningAlgorithm, List<PolicyContext> policies, CompilationContext context) {
        val compiledPolicies = policies.stream().map(p -> compilePolicy(p, Value.TRUE, context)).toList();
        val algorithmName    = toSaplSyntax(combiningAlgorithm);
        return switch (combiningAlgorithm) {
        case DenyOverridesAlgorithmContext ignored     ->
            CombiningAlgorithmCompiler.denyOverrides(setName, algorithmName, compiledPolicies);
        case DenyUnlessPermitAlgorithmContext ignored  ->
            CombiningAlgorithmCompiler.denyUnlessPermit(setName, algorithmName, compiledPolicies);
        case OnlyOneApplicableAlgorithmContext ignored ->
            CombiningAlgorithmCompiler.onlyOneApplicable(setName, algorithmName, compiledPolicies);
        case FirstApplicableAlgorithmContext ignored   ->
            CombiningAlgorithmCompiler.firstApplicable(setName, algorithmName, compiledPolicies);
        case PermitUnlessDenyAlgorithmContext ignored  ->
            CombiningAlgorithmCompiler.permitUnlessDeny(setName, algorithmName, compiledPolicies);
        case PermitOverridesAlgorithmContext ignored   ->
            CombiningAlgorithmCompiler.permitOverrides(setName, algorithmName, compiledPolicies);
        default                                        -> throw new SaplCompilerException(
                "Unexpected combining algorithm: " + combiningAlgorithm.getClass().getSimpleName(), combiningAlgorithm);
        };
    }

    private static String toSaplSyntax(CombiningAlgorithmContext algorithm) {
        return switch (algorithm) {
        case DenyOverridesAlgorithmContext ignored     -> "deny-overrides";
        case PermitOverridesAlgorithmContext ignored   -> "permit-overrides";
        case FirstApplicableAlgorithmContext ignored   -> "first-applicable";
        case OnlyOneApplicableAlgorithmContext ignored -> "only-one-applicable";
        case DenyUnlessPermitAlgorithmContext ignored  -> "deny-unless-permit";
        case PermitUnlessDenyAlgorithmContext ignored  -> "permit-unless-deny";
        default                                        -> algorithm.getText().toLowerCase().replace('_', '-');
        };
    }

    private CompiledExpression compileSchemas(List<SchemaStatementContext> schemas, CompilationContext context) {
        if (schemas == null || schemas.isEmpty()) {
            return Value.TRUE;
        }
        PureExpression schemaValidationExpression = null;
        for (val schema : schemas) {
            if (schema.enforced == null) {
                // Only actually verify enforced schemata. The others are just sugar for the
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

    private PureExpression compileSchema(SchemaStatementContext schema, CompilationContext context) {
        val schemaValue = ExpressionCompiler.compileExpression(schema.schemaExpression, context);
        if (!(schemaValue instanceof ObjectValue schemaObjectValue)) {
            throw new SaplCompilerException(COMPILE_ERROR_SCHEMA_NOT_OBJECT.formatted(schemaValue), schema);
        }
        val subscriptionElement = getSubscriptionElementName(schema.subscriptionElement);
        if (!ReservedIdentifiers.SUBSCRIPTION_IDENTIFIERS.contains(subscriptionElement)) {
            throw new SaplCompilerException(COMPILE_ERROR_SCHEMA_INVALID_ELEMENT.formatted(subscriptionElement),
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

    private String getSubscriptionElementName(ReservedIdContext reservedId) {
        return switch (reservedId) {
        case SubjectIdContext ignored     -> ReservedIdentifiers.SUBJECT;
        case ActionIdContext ignored      -> ReservedIdentifiers.ACTION;
        case ResourceIdContext ignored    -> ReservedIdentifiers.RESOURCE;
        case EnvironmentIdContext ignored -> ReservedIdentifiers.ENVIRONMENT;
        default                           -> reservedId.getText().toLowerCase();
        };
    }

    private String unquoteString(String quoted) {
        if (quoted == null || quoted.length() < 2) {
            return quoted;
        }
        if ((quoted.startsWith("\"") && quoted.endsWith("\"")) || (quoted.startsWith("'") && quoted.endsWith("'"))) {
            return quoted.substring(1, quoted.length() - 1);
        }
        return quoted;
    }

}
