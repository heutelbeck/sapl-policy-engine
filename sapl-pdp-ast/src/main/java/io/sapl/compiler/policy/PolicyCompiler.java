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

import java.util.ArrayList;
import java.util.List;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.TracedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Expression;
import io.sapl.ast.Policy;
import io.sapl.compiler.expressions.ArrayCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.*;
import io.sapl.compiler.pdp.PureVoter;
import io.sapl.compiler.pdp.Voter;
import io.sapl.compiler.policy.policybody.PolicyBodyCompiler;
import io.sapl.compiler.policy.policybody.TracedValueAndBodyCoverage;
import io.sapl.compiler.util.Nature;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Compiles SAPL policies into executable vote makers.
 * <p>
 * A {@link CompiledPolicy} provides four entry points:
 * <ul>
 * <li>{@code isApplicable} - Evaluates only the policy body for applicability
 * checking.</li>
 * <li>{@code voter} - Evaluates constraints assuming applicability
 * (e.g., the PDP after determining applicability).</li>
 * <li>{@code applicabilityAndVote} - Combined applicability and constraint
 * evaluation (e.g., in policy sets walking the contained polices).</li>
 * <li>{@code coverage} - Stream emitting decisions with coverage data for
 * testing.</li>
 * </ul>
 */
@UtilityClass
public class PolicyCompiler {

    private static final String ERROR_ADVICE_STATIC_ERROR              = "Advice expression statically evaluates to an error: %s.";
    private static final String ERROR_CONSTRAINT_RELATIVE_ACCESSOR     = "%s contains @ or # outside of proper context.";
    private static final String ERROR_OBLIGATIONS_STATIC_ERROR         = "Obligation expression statically evaluates to an error: %s.";
    private static final String ERROR_TRANSFORMATION_RELATIVE_ACCESSOR = "Transformation contains @ or # outside of proper context.";
    private static final String ERROR_TRANSFORMATION_STATIC_ERROR      = "Transformation expression statically evaluates to an error: %s.";
    private static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE    = "Unexpected isApplicable type. Indicates implementation bug.";

    /**
     * Compiles a policy AST into an executable compiled policy.
     *
     * @param policy the policy AST node
     * @param ctx the compilation context
     * @return the compiled policy with vote makers and coverage streams
     */
    public CompiledPolicy compilePolicy(Policy policy, CompilationContext ctx) {
        ctx.resetForNextPolicy();
        val voterMetadata            = policy.metadata();
        val compiledBody             = PolicyBodyCompiler.compilePolicyBody(policy.body(), ctx);
        val isApplicable             = compiledBody.bodyExpression();
        val voter                    = compileVoter(policy, voterMetadata, ctx);
        val coverage                 = assembleVoteWithCoverage(compiledBody.coverageStream(), voter, voterMetadata);
        val hasConstraints           = hasConstraints(policy);
        val applicabilityAndDecision = compileApplicabilityAndDecision(isApplicable, voter, voterMetadata);
        return new CompiledPolicy(isApplicable, voter, applicabilityAndDecision, coverage, voterMetadata,
                hasConstraints);
    }

    /**
     * @return true if the policy has obligations, advice, or a transformation
     */
    private static boolean hasConstraints(Policy policy) {
        return !policy.obligations().isEmpty() || !policy.advice().isEmpty() || policy.transformation() != null;
    }

    /**
     * Wraps the vote maker with applicability checking based on the body
     * expression type.
     * Used by policy sets walking contained policies.
     *
     * @param isApplicable the compiled body expression determining applicability
     * @param voter the vote maker for constraints evaluation
     * @param voterMetadata the policy voterMetadata
     * @return a vote maker that combines applicability and constraint
     * evaluation
     */
    private Voter compileApplicabilityAndDecision(CompiledExpression isApplicable, Voter voter,
            VoterMetadata voterMetadata) {
        return switch (isApplicable) {
        case ErrorValue error                                      -> Vote.error(error, voterMetadata);
        case BooleanValue(var b) when b                            -> voter;
        case BooleanValue ignored                                  -> Vote.abstain(voterMetadata);
        case PureOperator po when voter instanceof StreamVoter sdm ->
            new PureBodyStreamConstraintsVoter(po, sdm, voterMetadata);
        case PureOperator po                                       ->
            new ApplicabilityCheckingPureVoter(po, voter, voterMetadata);
        case StreamOperator so                                     ->
            new ApplicabilityCheckingStreamVoter(so, voter, voterMetadata);
        default                                                    ->
            Vote.error(Value.error(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), voterMetadata);
        };
    }

    /**
     * Compiles the policy constraints into a vote maker based on their nature.
     * Used by the PDP after determining applicability.
     *
     * @param policy the policy AST node
     * @param voterMetadata the policy voterMetadata
     * @param ctx the compilation context
     * @return a constant, pure, or stream vote maker
     */
    private static Voter compileVoter(Policy policy, VoterMetadata voterMetadata, CompilationContext ctx) {
        val constraints = compileConstraints(policy, policy.location(), ctx);
        val decision    = policy.entitlement().decision();
        return switch (constraints.nature()) {
        case CONSTANT -> Vote.tracedVote(decision, (ArrayValue) constraints.obligations(),
                (ArrayValue) constraints.advice(), (Value) constraints.resource(), voterMetadata, List.of());
        case PURE     -> new SimplePureVoter(decision, constraints.obligations(), constraints.advice(),
                constraints.resource(), voterMetadata);
        case STREAM   -> new SimpleStreamVoter(decision, OperatorLiftUtil.liftToStream(constraints.obligations()),
                OperatorLiftUtil.liftToStream(constraints.advice()),
                OperatorLiftUtil.liftToStream(constraints.resource()), voterMetadata);
        };
    }

    /**
     * Compiles policy constraints (obligations, advice, transformation).
     *
     * @param policy the policy AST node
     * @param location the source location for error reporting
     * @param ctx the compilation context
     * @return compiled constraints with their determined nature
     */
    private static CompiledConstraints compileConstraints(Policy policy, SourceLocation location,
            CompilationContext ctx) {
        val obligations = compileConstraintArray(policy.obligations(), location, "Obligation", ctx);
        if (obligations instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_OBLIGATIONS_STATIC_ERROR.formatted(error), location);
        }
        val advice = compileConstraintArray(policy.advice(), location, "Advice", ctx);
        if (advice instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_ADVICE_STATIC_ERROR.formatted(error), location);
        }
        var resource = policy.transformation() == null ? Value.UNDEFINED
                : ExpressionCompiler.compile(policy.transformation(), ctx);
        if (resource instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_STATIC_ERROR.formatted(error), location);
        }
        if (resource instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_TRANSFORMATION_RELATIVE_ACCESSOR, location);
        }
        var nature = Nature.CONSTANT;
        if (obligations instanceof StreamOperator || advice instanceof StreamOperator
                || resource instanceof StreamOperator) {
            nature = Nature.STREAM;
        } else if (obligations instanceof PureOperator || advice instanceof PureOperator
                || resource instanceof PureOperator) {
            nature = Nature.PURE;
        }
        return new CompiledConstraints(nature, obligations, advice, ExpressionCompiler.fold(resource, ctx));
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
        var result = ArrayCompiler.buildFromCompiled(
                expressions.stream().map(e -> ExpressionCompiler.compile(e, ctx)).toList(), location);
        if (result instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_CONSTRAINT_RELATIVE_ACCESSOR.formatted(name), location);
        }
        return ExpressionCompiler.fold(result, ctx);
    }

    /**
     * Creates a coverage-tracking vote stream from body coverage and vote
     * maker.
     *
     * @param bodyCoverage the body coverage stream
     * @param voter the vote maker
     * @param voterMetadata the policy voterMetadata
     * @return a flux emitting policy decisions with coverage information
     */
    private static Flux<VoteWithCoverage> assembleVoteWithCoverage(Flux<TracedValueAndBodyCoverage> bodyCoverage,
            Voter voter, VoterMetadata voterMetadata) {
        return bodyCoverage.switchMap(bodyResult -> {
            val bodyValue         = bodyResult.value().value();
            val bodyAttrs         = bodyResult.value().contributingAttributes();
            val bodyConditionHits = bodyResult.bodyCoverage();
            val policyCoverage    = new Coverage.PolicyCoverage(voterMetadata, bodyConditionHits);
            if (bodyValue instanceof ErrorValue error) {
                return Flux.just(withCoverage(Vote.tracedError(error, voterMetadata, bodyAttrs), voterMetadata,
                        bodyConditionHits));
            }
            if (Value.FALSE.equals(bodyValue)) {
                return Flux.just(
                        withCoverage(Vote.tracedAbstain(voterMetadata, bodyAttrs), voterMetadata, bodyConditionHits));
            }
            return switch (voter) {
            case Vote pd         -> Flux.just(new VoteWithCoverage(pd, policyCoverage));
            case PureVoter pdm   -> Flux.deferContextual(ctxView -> Flux.just(
                    new VoteWithCoverage(pdm.vote(bodyAttrs, ctxView.get(EvaluationContext.class)), policyCoverage)));
            case StreamVoter sdm ->
                sdm.vote(bodyAttrs).map(policyDecision -> new VoteWithCoverage(policyDecision, policyCoverage));
            };
        });
    }

    /**
     * Wraps a policy vote with coverage information.
     *
     * @param vote the policy vote
     * @param voterMetadata the policy voterMetadata
     * @param bodyCoverage the body coverage data
     * @return the vote with coverage
     */
    private static VoteWithCoverage withCoverage(Vote vote, VoterMetadata voterMetadata,
            Coverage.BodyCoverage bodyCoverage) {
        val policyCoverage = new Coverage.PolicyCoverage(voterMetadata, bodyCoverage);
        return new VoteWithCoverage(vote, policyCoverage);
    }

    /**
     * Builds a policy vote from merged constraint stream values.
     *
     * @param merged the merged stream values [obligations, advice, resource]
     * @param decision the entitlement vote
     * @param baseAttributes the contributing attributes from body evaluation
     * @param voterMetadata the policy voterMetadata
     * @return the assembled policy vote
     */
    private static Vote buildFromConstraintStreams(Object[] merged, Decision decision,
            List<AttributeRecord> baseAttributes, VoterMetadata voterMetadata) {
        val tracedObligationsValue = (TracedValue) merged[0];
        val obligationsValue       = tracedObligationsValue.value();
        val contributingAttributes = new ArrayList<>(baseAttributes);
        contributingAttributes.addAll(tracedObligationsValue.contributingAttributes());
        if (obligationsValue instanceof ErrorValue error) {
            return Vote.tracedError(error, voterMetadata, contributingAttributes);
        }
        val tracedAdviceValue = (TracedValue) merged[1];
        val adviceValue       = tracedAdviceValue.value();
        contributingAttributes.addAll(tracedAdviceValue.contributingAttributes());
        if (adviceValue instanceof ErrorValue error) {
            return Vote.tracedError(error, voterMetadata, contributingAttributes);
        }
        val tracedResourceValue = (TracedValue) merged[2];
        val resourceValue       = tracedResourceValue.value();
        contributingAttributes.addAll(tracedResourceValue.contributingAttributes());
        if (resourceValue instanceof ErrorValue error) {
            return Vote.tracedError(error, voterMetadata, contributingAttributes);
        }
        return Vote.tracedVote(decision, (ArrayValue) obligationsValue, (ArrayValue) adviceValue, resourceValue,
                voterMetadata, contributingAttributes);
    }

    /**
     * Compiled policy constraints with their nature classification.
     *
     * @param nature the complexity classification
     * @param obligations the compiled obligations array
     * @param advice the compiled advice array
     * @param resource the compiled transformation expression
     */
    public record CompiledConstraints(
            Nature nature,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource) {}

    /**
     * Decision maker for policies with pure constraints requiring runtime
     * evaluation.
     *
     * @param decision the entitlement vote
     * @param obligations the obligations expression
     * @param advice the advice expression
     * @param resource the transformation expression
     * @param metadata the policy voterMetadata
     */
    public record SimplePureVoter(
            Decision decision,
            CompiledExpression obligations,
            CompiledExpression advice,
            CompiledExpression resource,
            VoterMetadata metadata) implements PureVoter {

        @Override
        public Vote vote(List<AttributeRecord> bodyContributions, EvaluationContext ctx) {
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
            return Vote.tracedVote(decision, (ArrayValue) obligationsArray, (ArrayValue) adviceArray, resourceValue,
                    metadata, bodyContributions);
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
     * @param decision the entitlement vote
     * @param obligations the obligations stream operator
     * @param advice the advice stream operator
     * @param resource the transformation stream operator
     * @param voterMetadata the policy voterMetadata
     */
    public record SimpleStreamVoter(
            Decision decision,
            StreamOperator obligations,
            StreamOperator advice,
            StreamOperator resource,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public Flux<Vote> vote(List<AttributeRecord> knownContributions) {
            return Flux.combineLatest(obligations.stream(), advice.stream(), resource.stream(),
                    merged -> buildFromConstraintStreams(merged, decision, knownContributions, voterMetadata));
        }
    }

    /**
     * Decision maker for pure applicability check with non-streaming constraints.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param voter the underlying vote maker
     * @param voterMetadata the policy voterMetadata
     */
    public record ApplicabilityCheckingPureVoter(PureOperator isApplicable, Voter voter, VoterMetadata voterMetadata)
            implements PureVoter {

        @Override
        public Vote vote(List<AttributeRecord> bodyContributions, EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return Vote.error(error, voterMetadata);
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (voter) {
                case Vote pd             -> pd;
                case PureVoter pdm       -> pdm.vote(bodyContributions, ctx);
                case StreamVoter ignored -> Vote.tracedError(Value.error("StreamVoter in pure applicability context"),
                        voterMetadata, bodyContributions);
                };
            }
            return Vote.abstain(voterMetadata);
        }
    }

    /**
     * Decision maker for streaming applicability check.
     *
     * @param isApplicable the stream operator for applicability evaluation
     * @param voter the underlying vote maker
     * @param voterMetadata the policy voterMetadata
     */
    public record ApplicabilityCheckingStreamVoter(
            StreamOperator isApplicable,
            Voter voter,
            VoterMetadata voterMetadata) implements StreamVoter {

        @Override
        public Flux<Vote> vote(List<AttributeRecord> knownContributions) {
            return isApplicable.stream().switchMap(tracedApplicability -> {
                val applicabilityValue = tracedApplicability.value();
                if (applicabilityValue instanceof ErrorValue error) {
                    return Flux.just(Vote.error(error, voterMetadata));
                }
                if (applicabilityValue instanceof BooleanValue(var b) && b) {
                    return switch (voter) {
                    case Vote pd         -> Flux.just(pd);
                    case PureVoter pdm   -> Flux.deferContextual(
                            ctxView -> Flux.just(pdm.vote(knownContributions, ctxView.get(EvaluationContext.class))));
                    case StreamVoter sdm -> sdm.vote(knownContributions);
                    };
                }
                return Flux.just(Vote.abstain(voterMetadata));
            });
        }
    }

    /**
     * Decision maker for pure applicability check with streaming constraints.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param streamDecisionMaker the streaming vote maker for constraints
     * @param metadata the policy voterMetadata
     */
    public record PureBodyStreamConstraintsVoter(
            PureOperator isApplicable,
            StreamVoter streamDecisionMaker,
            VoterMetadata metadata) implements StreamVoter {

        @Override
        public Flux<Vote> vote(List<AttributeRecord> knownContributions) {
            return Flux.deferContextual(ctxView -> {
                val ctx                 = ctxView.get(EvaluationContext.class);
                val applicabilityResult = isApplicable.evaluate(ctx);
                if (applicabilityResult instanceof ErrorValue error) {
                    return Flux.just(Vote.error(error, metadata));
                }
                if (applicabilityResult instanceof BooleanValue(var b) && b) {
                    return streamDecisionMaker.vote(knownContributions);
                }
                return Flux.just(Vote.abstain(metadata));
            });
        }
    }
}
