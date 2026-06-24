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
package io.sapl.compiler.policy;

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.*;
import io.sapl.compiler.document.*;
import io.sapl.compiler.expressions.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.evalChild;
import static io.sapl.api.model.StreamOperator.mergeDependencies;

/**
 * Compiles SAPL policies into executable vote makers.
 * <p>
 * A {@link CompiledPolicy} provides four entry points:
 * <ul>
 * <li>{@code applicabilityCondition} - Evaluates only the policy body for
 * applicability
 * checking.</li>
 * <li>{@code voter} - Evaluates constraints assuming applicability
 * (e.g., the PDP after determining applicability).</li>
 * <li>{@code applicabilityAndVote} - Combined applicability and constraint
 * evaluation (e.g., in policy sets walking the contained polices).</li>
 * <li>{@code coverageVoter} - Snapshot-driven coverage-instrumented evaluator.
 * {@link CoverageVoter.Lazy} or {@link CoverageVoter.Eager} based on
 * {@link CompilationContext#lowLatencyMode()}.</li>
 * </ul>
 */
@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_STATIC_ERROR                         = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR                = "%s contains @ or # outside of proper context.";
    private static final String ERROR_MUST_BE_TRUE_OR_A_STREAM_OPERATOR_BUT_WAS_S = "Streaming part of conditions must be TRUE or a StreamOperator, but was: %s. This indicates an implementation bug.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR                    = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_STREAM_VOTER_IN_PURE_CONTEXT                = "StreamVoter in pure applicability context";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR            = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR                 = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE               = "Unexpected applicabilityCondition type. Indicates implementation bug.";

    /**
     * Compiles a policy AST into an executable compiled policy.
     *
     * @param policy the policy AST node
     * @param ctx the compilation context
     * @return the compiled policy with vote makers and coverage streams
     */
    public CompiledPolicy compilePolicy(Policy policy, CompilationContext ctx) {
        ctx.resetForNextPolicy();
        val           metadata              = policy.metadata();
        val           conditions            = compileConditions(policy.body().statements(), ctx);
        val           isApplicable          = compilePureSectionOfBodyExpression(conditions, policy.body());
        val           streamingSection      = compileStreamingSectionOfBodyExpression(conditions, policy.body());
        val           constraintsVoter      = compileConstraintsVoter(policy, metadata, ctx);
        val           voter                 = wrapWithStreamingBody(constraintsVoter, streamingSection, metadata,
                policy.location());
        val           applicabilityAndVoter = compileApplicabilityAndVoter(isApplicable, streamingSection, voter,
                constraintsVoter, metadata);
        CoverageVoter coverageVoter         = ctx.lowLatencyMode()
                ? new CoverageVoter.Eager(conditions, constraintsVoter, metadata)
                : new CoverageVoter.Lazy(conditions, constraintsVoter, metadata);
        return new CompiledPolicy(isApplicable, voter, applicabilityAndVoter, coverageVoter, metadata);
    }

    /**
     * Builds the voter combining the pure applicability section with the body
     * (the {@code voter}) used by policy sets walking contained policies and by
     * the first-applicable algorithm. Constant cases fold. When applicability is
     * pure and a streaming section remains, a {@link PolicyBodyVoter} applies the
     * cross-stratum Kleene AND so a streaming FALSE can dominate a pure error.
     *
     * @param applicability the pure applicability section (constant or pure)
     * @param streamingSection the streaming section, or {@link Value#TRUE}
     * @param voter the body voter assuming applicability (streaming and
     * constraints)
     * @param constraints the constraints voter
     * @param voterMetadata the policy voterMetadata
     * @return a vote maker that combines applicability and the body
     */
    private Voter compileApplicabilityAndVoter(CompiledExpression applicability, CompiledExpression streamingSection,
            Voter voter, Voter constraints, VoterMetadata voterMetadata) {
        return switch (applicability) {
        case BooleanValue(var applicable) when applicable                                                            ->
            voter;
        case BooleanValue ignored                                                                                    ->
            Vote.abstain(voterMetadata);
        case ErrorValue error when streamingSection instanceof StreamOperator                                        ->
            new PolicyBodyVoter(error, streamingSection, constraints, voterMetadata);
        case ErrorValue error                                                                                        ->
            Vote.error(error, voterMetadata);
        case PureOperator pure when streamingSection instanceof StreamOperator || constraints instanceof StreamVoter ->
            new PolicyBodyVoter(pure, streamingSection, constraints, voterMetadata);
        case PureOperator pure                                                                                       ->
            new ApplicabilityCheckingPureVoter(pure, constraints, voterMetadata);
        default                                                                                                      ->
            Vote.error(Value.error(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), voterMetadata);
        };
    }

    /**
     * Compiles the policy constraints into a vote maker based on their nature.
     *
     * @param policy the policy AST node
     * @param voterMetadata the policy voterMetadata
     * @param ctx the compilation context
     * @return a constant, pure, or stream vote maker for constraints only
     */
    private static Voter compileConstraintsVoter(Policy policy, VoterMetadata voterMetadata, CompilationContext ctx) {
        val location    = policy.location();
        val obligations = compileConstraintArray(policy.obligations(), location, "Obligation", ctx);
        if (obligations instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_OBLIGATIONS_STATIC_ERROR.formatted(error), location);
        }
        val advice = compileConstraintArray(policy.advice(), location, "Advice", ctx);
        if (advice instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_ADVICE_STATIC_ERROR.formatted(error), location);
        }
        val resourceCompiled = policy.transformation() == null ? Value.UNDEFINED
                : ExpressionCompiler.compile(policy.transformation(), ctx);
        if (resourceCompiled instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_STATIC_ERROR.formatted(error), location);
        }
        if (resourceCompiled instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_RELATIVE_ACCESSOR, location);
        }
        val resource = ctx.foldCacheDedupe(resourceCompiled);
        val decision = policy.effect().decision();
        if (obligations instanceof StreamOperator || advice instanceof StreamOperator
                || resource instanceof StreamOperator) {
            return new SimpleStreamVoter(decision, obligations, advice, resource, voterMetadata);
        }
        if (obligations instanceof PureOperator || advice instanceof PureOperator || resource instanceof PureOperator) {
            return new SimplePureVoter(decision, obligations, advice, resource, voterMetadata);
        }
        return Vote.of(decision, (ArrayValue) obligations, (ArrayValue) advice, (Value) resource, voterMetadata);
    }

    /**
     * Wraps the constraints voter with streaming body conditions if needed.
     *
     * @param constraintsVoter the voter for constraints evaluation
     * @param streamingPartOfBody the streaming section of body conditions
     * @param voterMetadata the policy voterMetadata
     * @param location source location for error reporting
     * @return the wrapped voter or the original if no streaming body
     */
    private static Voter wrapWithStreamingBody(Voter constraintsVoter, CompiledExpression streamingPartOfBody,
            VoterMetadata voterMetadata, SourceLocation location) {
        if (streamingPartOfBody == null || (streamingPartOfBody instanceof BooleanValue(var b) && b)) {
            return constraintsVoter;
        }
        if (streamingPartOfBody instanceof StreamOperator so) {
            return new PolicyBodyVoter(Value.TRUE, so, constraintsVoter, voterMetadata);
        }
        throw new SaplCompilerException(ERROR_MUST_BE_TRUE_OR_A_STREAM_OPERATOR_BUT_WAS_S
                .formatted(streamingPartOfBody.getClass().getSimpleName()), location);
    }

    /**
     * Compiles a list of constraint expressions into an array expression.
     *
     * @param expressions the constraint expressions
     * @param location the source location for error reporting
     * @param name the constraint type name for error messages
     * @param ctx the compilation context
     * @return the compiled array expression
     */
    private static CompiledExpression compileConstraintArray(List<Expression> expressions, SourceLocation location,
            String name, CompilationContext ctx) {
        val result = ArrayCompiler.compileExpressionsToArray(expressions, location, ctx);
        if (result instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_CONSTRAINT_RELATIVE_ACCESSOR.formatted(name), location);
        }
        return ctx.foldCacheDedupe(result);
    }

    /**
     * Decision maker for policies with pure constraints requiring runtime
     * evaluation.
     *
     * @param decision the effect-derived decision
     * @param obligations the obligations expression
     * @param advice the advice expression
     * @param resource the transformation expression
     * @param metadata the policy voterMetadata
     */
    record SimplePureVoter(
            Decision decision,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            VoterMetadata metadata) implements PureVoter {

        @Override
        public Vote vote(EvaluationContext ctx) {
            val obligationsArray = evaluate(obligations, ctx);
            if (obligationsArray instanceof ErrorValue error) {
                return Vote.error(error, metadata);
            }
            val adviceArray = evaluate(advice, ctx);
            if (adviceArray instanceof ErrorValue error) {
                return Vote.error(error, metadata);
            }
            val resourceValue = evaluate(resource, ctx);
            if (resourceValue instanceof ErrorValue error) {
                return Vote.error(error, metadata);
            }
            return Vote.of(decision, (ArrayValue) obligationsArray, (ArrayValue) adviceArray, resourceValue, metadata);
        }

        private Value evaluate(CompiledExpression expression, EvaluationContext ctx) {
            if (expression instanceof Value v) {
                return v;
            } else {
                return ((PureOperator) expression).evaluate(ctx);
            }
        }
    }

    /**
     * Decision maker for policies with streaming constraints.
     *
     * @param decision the effect-derived decision
     * @param obligations the obligations expression
     * @param advice the advice expression
     * @param resource the transformation expression
     * @param voterMetadata the policy voterMetadata
     */
    record SimpleStreamVoter(
            Decision decision,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            val deps = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(3);

            val obligationsValue = evalChild(obligations, ctx, deps);
            val adviceValue      = evalChild(advice, ctx, deps);
            val resourceValue    = evalChild(resource, ctx, deps);

            Value firstError = null;
            if (obligationsValue instanceof ErrorValue) {
                firstError = obligationsValue;
            } else if (adviceValue instanceof ErrorValue) {
                firstError = adviceValue;
            } else if (resourceValue instanceof ErrorValue) {
                firstError = resourceValue;
            }
            if (firstError != null) {
                return new VoteResult(Vote.error((ErrorValue) firstError, voterMetadata), deps);
            }

            if (obligationsValue == null || adviceValue == null || resourceValue == null) {
                return new VoteResult(null, deps);
            }

            return new VoteResult(Vote.of(decision, (ArrayValue) obligationsValue, (ArrayValue) adviceValue,
                    resourceValue, voterMetadata), deps);
        }
    }

    /**
     * Decision maker for pure applicability check with non-streaming constraints.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param voter the underlying vote maker
     * @param voterMetadata the policy voterMetadata
     */
    record ApplicabilityCheckingPureVoter(PureOperator isApplicable, Voter voter, VoterMetadata voterMetadata)
            implements PureVoter {

        @Override
        public Vote vote(EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return Vote.error(error, voterMetadata);
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (voter) {
                case Vote pd             -> pd;
                case PureVoter pdm       -> pdm.vote(ctx);
                case StreamVoter ignored -> Vote.error(Value.error(ERROR_STREAM_VOTER_IN_PURE_CONTEXT), voterMetadata);
                };
            }
            return Vote.abstain(voterMetadata);
        }
    }

    /**
     * The voter for a policy body, which is the Kleene strong three-valued AND
     * of the pure applicability section and the streaming section, dispatching
     * to the constraints voter when the body is TRUE. The applicability is
     * always pure (a constant {@link Value} or a {@link PureOperator}, never a
     * stream); the streaming section is a constant {@link Value#TRUE} when the
     * body has no streaming conditions, otherwise a {@link StreamOperator}.
     * <p>
     * The strata combine order-independently: a FALSE in either section
     * dominates and yields NOT_APPLICABLE (so a streaming FALSE dominates a pure
     * error). Otherwise an error in either section yields INDETERMINATE.
     * Otherwise both are TRUE and the constraints voter produces the vote. The
     * constraints voter is evaluated only when the body is TRUE, so an erroring
     * or non-applicable body never triggers obligation, advice, or
     * transformation attribute reads.
     *
     * @param applicability the pure applicability section (constant or pure)
     * @param streamingSection the streaming section, or {@link Value#TRUE}
     * @param constraints the constraints voter, applied when the body is TRUE
     * @param voterMetadata the policy voterMetadata
     */
    record PolicyBodyVoter(
            CompiledExpression applicability,
            CompiledExpression streamingSection,
            Voter constraints,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            // Eval the pure section of the body
            var applicabilityValue = applicability instanceof PureOperator pure ? pure.evaluate(ctx)
                    : (Value) applicability;
            if (applicabilityValue instanceof BooleanValue(var applicable) && !applicable) {
                // If now known to be false -> short circuit to abstain
                return new VoteResult(Vote.abstain(voterMetadata), Map.of());
            }
            // Here applicability is either true, or error. All expressions guaranteed to
            // only return that.
            // Eval streaming section
            val deps        = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(2);
            var streamValue = evalChild(streamingSection, ctx, deps);
            if (streamValue == null) {
                // Needs more attributes
                return new VoteResult(null, deps);
            }
            if (streamValue instanceof BooleanValue(var streamApplicable) && !streamApplicable) {
                // If now known to be false -> short circuit to abstain
                return new VoteResult(Vote.abstain(voterMetadata), deps);
            }
            // Here we either have all true or some error in the history. Errors beat true.
            if (applicabilityValue instanceof ErrorValue applicabilityError) {
                return new VoteResult(Vote.error(applicabilityError, voterMetadata), deps);
            }
            if (streamValue instanceof ErrorValue streamError) {
                return new VoteResult(Vote.error(streamError, voterMetadata), deps);
            }
            // Body was true. Now the policy is allowed to cast a vote!
            val constraintsResult = constraints.evaluate(ctx);
            mergeDependencies(deps, constraintsResult.dependencies());
            return new VoteResult(constraintsResult.vote(), deps);
        }
    }

    public static final String ERROR_ATTEMPT_TO_REDEFINE_VARIABLE_S = "Policy attempted to redefine variable '%s'.";

    public record IndexedCompiledCondition(CompiledExpression expression, SourceLocation location, long statementId) {}

    public static @NonNull List<IndexedCompiledCondition> compileConditions(List<Statement> statements,
            CompilationContext ctx) {
        val conditions  = new ArrayList<IndexedCompiledCondition>(statements.size());
        var statementId = 0;
        if (!statements.isEmpty() && statements.getFirst() instanceof SchemaCondition) {
            statementId = -1;
        }
        for (Statement statement : statements) {
            switch (statement) {
            case VarDef(var name, var value, var ignored, var location) -> {
                // Do not add as guard !
                if (!ctx.addLocalPolicyVariable(name, ExpressionCompiler.compile(value, ctx))) {
                    throw new SaplCompilerException(ERROR_ATTEMPT_TO_REDEFINE_VARIABLE_S.formatted(name), location);
                }
            }
            case Condition(var expression, var ignored)                 -> {
                val conditionExpression = ExpressionCompiler.compile(expression, ctx);
                conditions.add(new IndexedCompiledCondition(conditionExpression, statement.location(), statementId));
                statementId++;
            }
            case SchemaCondition(var schemas, var ignored)              -> {
                val conditionExpression = SchemaValidatorCompiler.compileValidator(schemas, ctx);
                conditions.add(new IndexedCompiledCondition(conditionExpression, statement.location(), statementId));
                statementId++;
            }
            }
        }
        return conditions;
    }

    public static CompiledExpression compilePureSectionOfBodyExpression(List<IndexedCompiledCondition> conditions,
            PolicyBody body) {
        val pureConditions = conditions.stream().map(IndexedCompiledCondition::expression)
                .filter(expression -> expression instanceof Value || expression instanceof PureOperator).toList();
        return NaryBooleanCompiler.compile(pureConditions, body.location(), Value.FALSE, Value.TRUE);
    }

    public static CompiledExpression compileStreamingSectionOfBodyExpression(List<IndexedCompiledCondition> conditions,
            PolicyBody policyBody) {
        val streamConditions = conditions.stream().map(IndexedCompiledCondition::expression)
                .filter(StreamOperator.class::isInstance).toList();
        return NaryBooleanCompiler.compile(streamConditions, policyBody.location(), Value.FALSE, Value.TRUE);
    }

}
